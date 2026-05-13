package server.service;

import model.Auction.Auction;
import model.Auction.BidTransaction;
import server.config.DatabaseConfig;
import server.dao.AuctionRepository;
import server.dao.BidTransactionRepository;
import server.websocket.FrontendNotifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BidService — service core của hệ thống realtime đấu giá.
 *
 * Trách nhiệm:
 *  - Validate bid (auction đang RUNNING, giá hợp lệ, không tự bid chính mình)
 *  - Per-auction lock (tránh race condition khi nhiều user bid cùng lúc)
 *  - JDBC transaction atomic (bid_transactions INSERT + auctions UPDATE cùng commit)
 *  - Broadcast realtime WebSocket SAU KHI transaction commit thành công
 *
 * Flow chuẩn:
 *   lock(auctionId)
 *   → validate bid
 *   → begin transaction
 *   → INSERT bid_transactions
 *   → UPDATE auctions (current_price, leading_bidder_id)
 *   → commit
 *   → unlock
 *   → broadcastBidUpdate()   ← SAU commit, NGOÀI lock
 *
 * Tại sao broadcast SAU unlock:
 *  - Lock chỉ bảo vệ DB write, không cần giữ khi gửi socket.
 *  - Tránh deadlock nếu socket callback cố lấy lại lock.
 *  - Latency WebSocket không ảnh hưởng throughput của luồng bid.
 */
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidTransactionRepository bidRepository;
    private final FrontendNotifier frontendNotifier;

    /**
     * Per-auction lock map.
     * Mỗi auctionId có 1 ReentrantLock riêng.
     * ConcurrentHashMap.computeIfAbsent đảm bảo thread-safe khi tạo lock mới.
     *
     * Lưu ý: Lock được giữ trong RAM — nếu scale multi-instance thì cần
     * distributed lock (Redis SETNX). Với single-instance, đây là đủ.
     */
    private final Map<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    public BidService(AuctionRepository auctionRepository,
                      BidTransactionRepository bidRepository,
                      FrontendNotifier frontendNotifier) {
        this.auctionRepository = auctionRepository;
        this.bidRepository     = bidRepository;
        this.frontendNotifier  = frontendNotifier;
    }

    // ──────────────────────────────────────────────
    // PLACE BID
    // ──────────────────────────────────────────────

    /**
     * Đặt giá cho một phiên đấu giá.
     *
     * @param auctionId ID phiên
     * @param bidderId  ID người đặt giá
     * @param amount    Số tiền muốn đặt
     * @return BidResult chứa kết quả và message
     */
    public BidResult placeBid(String auctionId, String bidderId, double amount) {

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock());
        lock.lock();

        BidResult result;
        BidTransaction savedBid = null;

        try {
            // ── 1. Load auction từ DB (fresh data, không dùng cache) ──
            Auction auction = auctionRepository.findById(auctionId).orElse(null);
            if (auction == null) {
                return BidResult.fail("Phiên đấu giá không tồn tại.");
            }

            // ── 2. Validate ──
            BidResult validation = validateBid(auction, bidderId, amount);
            if (!validation.isSuccess()) {
                return validation;
            }

            // ── 3. Tạo BidTransaction object ──
            BidTransaction bid = new BidTransaction(bidderId, auctionId, amount);

            // ── 4. JDBC transaction: INSERT bid + UPDATE auction ──
            try (Connection conn = DatabaseConfig.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    boolean bidSaved = bidRepository.save(conn, bid);
                    boolean auctionUpdated = auctionRepository.updateCurrentBid(
                            conn, auctionId, amount, bidderId);

                    if (!bidSaved || !auctionUpdated) {
                        conn.rollback();
                        return BidResult.fail("Lỗi lưu bid xuống DB. Vui lòng thử lại.");
                    }

                    conn.commit();
                    savedBid = bid; // chỉ gán sau commit thành công

                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("[BidService] Rollback transaction: " + e.getMessage());
                    return BidResult.fail("Lỗi hệ thống khi đặt giá. Vui lòng thử lại.");
                }
            }

            // ── 5. Cập nhật RAM sau commit (optional nhưng giúp validate nhất quán) ──
            auction.applyBid(bidderId, amount);

            result = BidResult.success(bid, "Đặt giá thành công: " + amount);

        } catch (SQLException e) {
            System.err.println("[BidService] Lỗi kết nối DB: " + e.getMessage());
            return BidResult.fail("Lỗi kết nối hệ thống. Vui lòng thử lại.");
        } finally {
            lock.unlock(); // LUÔN unlock dù thành công hay thất bại
        }

        // ── 6. Broadcast WebSocket SAU KHI unlock ──
        // Chỉ broadcast khi bid thực sự thành công (savedBid != null)
        if (savedBid != null) {
            frontendNotifier.broadcastBidUpdate(auctionId, bidderId, amount);
        }

        return result;
    }

    // ──────────────────────────────────────────────
    // VALIDATE
    // ──────────────────────────────────────────────

    private BidResult validateBid(Auction auction, String bidderId, double amount) {
        if (!auction.isRunning()) {
            return BidResult.fail("Phiên đấu giá không đang hoạt động. Trạng thái: "
                    + auction.getStatus());
        }
        if (auction.getSellerId().equals(bidderId)) {
            return BidResult.fail("Seller không thể tự đặt giá cho phiên của mình.");
        }
        if (amount < auction.getMinNextBid()) {
            return BidResult.fail(String.format(
                    "Giá phải cao hơn giá hiện tại ít nhất %.0f. Giá tối thiểu: %.0f",
                    auction.getPriceStep(), auction.getMinNextBid()));
        }
        return BidResult.success(null, "OK");
    }

    // ──────────────────────────────────────────────
    // RESULT CLASS
    // ──────────────────────────────────────────────

    /**
     * Kết quả của placeBid() — trả về cho Controller.
     * Không throw exception cho business failure, chỉ throw cho system error.
     */
    public static class BidResult {
        private final boolean        success;
        private final String         message;
        private final BidTransaction bid; // null nếu thất bại

        private BidResult(boolean success, String message, BidTransaction bid) {
            this.success = success;
            this.message = message;
            this.bid     = bid;
        }

        public static BidResult success(BidTransaction bid, String message) {
            return new BidResult(true, message, bid);
        }

        public static BidResult fail(String message) {
            return new BidResult(false, message, null);
        }

        public boolean        isSuccess() { return success; }
        public String         getMessage() { return message; }
        public BidTransaction getBid()    { return bid; }
    }
}