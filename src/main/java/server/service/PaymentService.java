package server.service;
import exception.InsufficientFundsException;
import model.Auction.Auction;
import model.Auction.AuctionStatus;
import server.config.DatabaseConfig;
import server.dao.AuctionRepository;
import server.dao.WalletRepository;
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
 * Đã xoá: PaymentObligation, PaymentScheduler, payment deadline, PaymentStatus.
 *
 * Giả định: WalletRepository có:
 *   withdraw(Connection, userId, amount) throws SQLException, InsufficientFundsException
 *   deposit(Connection, userId, amount)  throws SQLException
 * Thay bằng AccountRepository/WalletRepository thực tế của project.
 */
public class PaymentService {

    private final AuctionRepository auctionRepository;
    private final WalletRepository       walletRepository;
    private final AuctionLifeCycleService lifecycleService;
    private final FrontendNotifier frontendNotifier;

    public PaymentService(AuctionRepository auctionRepository,
                          WalletRepository walletRepository,
                          AuctionLifeCycleService lifecycleService,
                          FrontendNotifier frontendNotifier) {
        this.auctionRepository = auctionRepository;
        this.walletRepository  = walletRepository;
        this.lifecycleService  = lifecycleService;
        this.frontendNotifier  = frontendNotifier;
    }

    // ──────────────────────────────────────────────
    // PROCESS PAYMENT (Buyer xác nhận trả)
    // ──────────────────────────────────────────────

    /**
     * Buyer xác nhận thanh toán cho phiên FINISHED.
     *
     * Transaction atomic:
     *   1. withdraw(buyerId, amount)   — trừ tiền Buyer
     *   2. deposit(sellerId, amount)   — cộng tiền Seller
     *   3. updateStatus(PAID)          — đánh dấu phiên hoàn tất
     *
     * Tất cả cùng commit hoặc rollback.
     *
     * @param auctionId ID phiên đấu giá
     * @param buyerId   ID người mua (phải là leadingBidderId)
     * @return PaymentResult
     */
    public PaymentResult processPayment(String auctionId, String buyerId) {
        // Load auction
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null)
            return PaymentResult.fail("Phiên đấu giá không tồn tại.");

        // Validate trạng thái
        if (!auction.isFinished())
            return PaymentResult.fail("Phiên không ở trạng thái FINISHED. Hiện tại: " + auction.getStatus());

        // Validate người thanh toán
        if (!buyerId.equals(auction.getLeadingBidderId()))
            return PaymentResult.fail("Chỉ người thắng đấu giá mới có thể thanh toán.");

        String sellerId = auction.getSellerId();
        double amount   = auction.getCurrentPrice();

        // Atomic transaction
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Trừ tiền Buyer
                walletRepository.withdraw(conn, buyerId, amount);

                // Bước 2: Cộng tiền Seller
                walletRepository.deposit(conn, sellerId, amount);

                // Bước 3: Đánh dấu phiên PAID
                auctionRepository.updateStatus(conn, auctionId, AuctionStatus.PAID);

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

        System.out.printf("[PaymentService] Thanh toán thành công: phiên=%s | buyer=%s | "
                + "seller=%s | %.0f%n", auctionId, buyerId, sellerId, amount);

        frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.PAID);
        return PaymentResult.success("Thanh toán thành công!");
    }

    // ──────────────────────────────────────────────
    // REJECT PAYMENT (Buyer từ chối)
    // ──────────────────────────────────────────────

    /**
     * Buyer từ chối thanh toán.
     * Phiên chuyển sang CANCELED (BUYER_REJECTED).
     * Không cần transaction — chỉ update status.
     *
     * @param auctionId ID phiên đấu giá
     * @param buyerId   ID người mua (phải là leadingBidderId)
     */
    public PaymentResult rejectPayment(String auctionId, String buyerId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null)
            return PaymentResult.fail("Phiên đấu giá không tồn tại.");

        if (!auction.isFinished())
            return PaymentResult.fail("Phiên không ở trạng thái FINISHED.");

        if (!buyerId.equals(auction.getLeadingBidderId()))
            return PaymentResult.fail("Chỉ người thắng mới có thể từ chối thanh toán.");

        // Delegate sang LifecycleService để giữ logic cancel tập trung
        boolean canceled = lifecycleService.cancelAfterRejection(auctionId, buyerId);
        if (!canceled)
            return PaymentResult.fail("Không thể huỷ phiên. Vui lòng liên hệ Admin.");

        System.out.printf("[PaymentService] Buyer %s từ chối thanh toán phiên %s%n", buyerId, auctionId);
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

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
    }
}