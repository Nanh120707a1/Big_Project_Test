package server.service;

import exception.user.InsufficientFundsException;
import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.user.Account;
import server.config.DatabaseConfig;
import server.dao.AuctionRepository;
import server.dao.UserRepository;
import server.websocket.FrontendNotifier;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * PaymentService — xử lý thanh toán sau khi phiên đấu giá kết thúc.
 *
 * Trách nhiệm:
 *  - processPayment() : Buyer xác nhận trả tiền
 *      → atomic transaction: trừ tiền Buyer + cộng tiền Seller + PAID
 *  - rejectPayment()  : Buyer từ chối trả tiền
 *      → FINISHED → CANCELED (BUYER_REJECTED)
 *
 * Bảo mật:
 *  - buyerId KHÔNG nhận từ client trong bất kỳ method nào.
 *  - Luôn resolve Account từ token qua SessionManager.requireAccount(token).
 *  - Client chỉ gửi: token + auctionId.
 */
public class PaymentService {

    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final AuctionLifeCycleService lifecycleService;
    private final FrontendNotifier        frontendNotifier;
    private final SessionManager          sessionManager;
    private final UserService             userService;

    public PaymentService(AuctionRepository auctionRepository,
                          UserRepository userRepository,
                          AuctionLifeCycleService lifecycleService,
                          FrontendNotifier frontendNotifier,
                          SessionManager sessionManager,
                          UserService userService) {
        this.auctionRepository = auctionRepository;
        this.userRepository    = userRepository;
        this.lifecycleService  = lifecycleService;
        this.frontendNotifier  = frontendNotifier;
        this.sessionManager    = sessionManager;
        this.userService       = userService;
    }

    // ──────────────────────────────────────────────
    // PROCESS PAYMENT (Buyer xác nhận trả)
    // ──────────────────────────────────────────────

    /**
     * Buyer xác nhận thanh toán cho phiên FINISHED.
     *
     * Client gửi: token + auctionId (không gửi buyerId).
     * buyerId được resolve từ token phía server.
     *
     * Transaction atomic:
     *   1. withdraw(buyerId, amount)   — trừ tiền Buyer
     *   2. deposit(sellerId, amount)   — cộng tiền Seller
     *   3. updateStatus(PAID)          — đánh dấu phiên hoàn tất
     *
     * Tất cả cùng commit hoặc rollback.
     *
     * @param token     Session token của Buyer
     * @param auctionId ID phiên đấu giá
     * @return PaymentResult
     */
    public PaymentResult processPayment(String token, String auctionId) {
        // ── 1. Resolve buyer từ token — KHÔNG trust client ──
        Account buyer = sessionManager.requireAccount(token);

        // ── 2. Load auction ──
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null)
            return PaymentResult.fail("Phiên đấu giá không tồn tại.");

        // ── 3. Validate trạng thái ──
        if (!auction.isFinished())
            return PaymentResult.fail("Phiên không ở trạng thái FINISHED. Hiện tại: "
                    + auction.getStatus());

        // ── 4. Validate người thanh toán ──
        if (!buyer.getId().equals(auction.getLeadingBidderId()))
            return PaymentResult.fail("Chỉ người thắng đấu giá mới có thể thanh toán.");

        String buyerId  = buyer.getId();
        String sellerId = auction.getSellerId();
        double amount   = auction.getCurrentPrice();

        double buyerNewBalance;
        double sellerNewBalance;

        // ── 5. Atomic transaction ──
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Trừ tiền Buyer
                userRepository.withdraw(conn, buyerId, amount);

                // Bước 2: Cộng tiền Seller
                userRepository.deposit(conn, sellerId, amount);

                // Bước 3: Đánh dấu phiên PAID
                auctionRepository.updateStatus(conn, auctionId, AuctionStatus.PAID);

                // Bước 4: Lấy balance mới trong cùng transaction
                buyerNewBalance  = userRepository.getBalance(conn, buyerId);
                sellerNewBalance = userRepository.getBalance(conn, sellerId);

                conn.commit();

            } catch (InsufficientFundsException e) {
                conn.rollback();
                return PaymentResult.fail("Số dư không đủ để thanh toán. Cần: " + amount);

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[PaymentService] Rollback processPayment: " + e.getMessage());
                return PaymentResult.fail("Lỗi hệ thống khi xử lý thanh toán. Vui lòng thử lại.");
            }

        } catch (SQLException e) {
            System.err.println("[PaymentService] Lỗi kết nối DB: " + e.getMessage());
            return PaymentResult.fail("Lỗi kết nối hệ thống.");
        }

        // ── 6. Sync RAM — chỉ chạy sau khi DB đã commit thành công ──
        // Nếu user không online thì bỏ qua (findAccountById trả empty) —
        // lần login sau load số dư mới từ DB.
        userService.withdraw(buyerId, amount);
        userService.deposit(sellerId, amount);

        // ── 7. Push realtime balance update ──
        frontendNotifier.notifyBalanceUpdated(buyerId, buyerNewBalance);
        frontendNotifier.notifyBalanceUpdated(sellerId, sellerNewBalance);

        System.out.printf("[PaymentService] Thanh toán thành công: phiên=%s | "
                        + "buyer=%s | seller=%s | %.0f%n",
                auctionId, buyerId, sellerId, amount);

        frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.PAID);
        return PaymentResult.success("Thanh toán thành công!");
    }

    // ──────────────────────────────────────────────
    // REJECT PAYMENT (Buyer từ chối)
    // ──────────────────────────────────────────────

    /**
     * Buyer từ chối thanh toán.
     * Phiên chuyển sang CANCELED (BUYER_REJECTED).
     *
     * Client gửi: token + auctionId (không gửi buyerId).
     * buyerId được resolve từ token phía server.
     *
     * @param token     Session token của Buyer
     * @param auctionId ID phiên đấu giá
     * @return PaymentResult
     */
    public PaymentResult rejectPayment(String token, String auctionId) {
        // ── 1. Resolve buyer từ token — KHÔNG trust client ──
        Account buyer = sessionManager.requireAccount(token);
        String buyerId = buyer.getId();

        // ── 2. Load auction ──
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null)
            return PaymentResult.fail("Phiên đấu giá không tồn tại.");

        // ── 3. Validate trạng thái ──
        if (!auction.isFinished())
            return PaymentResult.fail("Phiên không ở trạng thái FINISHED.");

        // ── 4. Validate buyer là người thắng đấu giá ──
        if (!buyerId.equals(auction.getLeadingBidderId()))
            return PaymentResult.fail("Chỉ người thắng mới có thể từ chối thanh toán.");

        // ── 5. Delegate sang LifecycleService để giữ cancel logic tập trung ──
        boolean canceled = lifecycleService.cancelAfterRejection(auctionId, buyerId);
        if (!canceled)
            return PaymentResult.fail("Không thể huỷ phiên. Vui lòng liên hệ Admin.");

        System.out.printf("[PaymentService] Buyer %s từ chối thanh toán phiên %s%n",
                buyerId, auctionId);
        return PaymentResult.success("Đã từ chối thanh toán. Phiên đấu giá bị huỷ.");
    }

    // ──────────────────────────────────────────────
    // RESULT CLASS
    // ──────────────────────────────────────────────

    public static class PaymentResult {
        private final boolean success;
        private final String  message;

        private PaymentResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static PaymentResult success(String message) { return new PaymentResult(true,  message); }
        public static PaymentResult fail(String message)    { return new PaymentResult(false, message); }

        public boolean isSuccess()  { return success; }
        public String  getMessage() { return message; }
    }
}