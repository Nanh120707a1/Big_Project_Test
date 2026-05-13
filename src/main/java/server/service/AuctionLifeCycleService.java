package server.service;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import server.dao.AuctionRepository;
import server.websocket.FrontendNotifier;

/**
 * AuctionLifecycleService — quản lý vòng đời phiên đấu giá.
 *
 * Trách nhiệm:
 *  - createAuction()   : Seller tạo phiên → lưu DB với status PENDING
 *  - approveAuction()  : Admin duyệt    → PENDING → OPEN, schedule start/end
 *  - rejectAuction()   : Admin từ chối  → PENDING → CANCELED (ADMIN_REJECTED)
 *  - startAuction()    : Scheduler gọi  → OPEN → RUNNING
 *  - finishAuction()   : Scheduler gọi  → RUNNING → FINISHED hoặc CANCELED (NO_BIDDER)
 *  - cancelAuction()   : Buyer reject   → FINISHED → CANCELED (BUYER_REJECTED)
 *    (Không có seller cancel, không có admin cancel khi đang RUNNING)
 *
 * Không trực tiếp xử lý: bid, payment, scheduler schedule.
 */
public class AuctionLifeCycleService {

    private final AuctionRepository auctionRepository;
    private final FrontendNotifier frontendNotifier;
    private final AuctionSchedulerService schedulerService; // inject để schedule khi approve

    public AuctionLifeCycleService(AuctionRepository auctionRepository,
                                   FrontendNotifier frontendNotifier,
                                   AuctionSchedulerService schedulerService) {
        this.auctionRepository = auctionRepository;
        this.frontendNotifier  = frontendNotifier;
        this.schedulerService  = schedulerService;
    }

    // ──────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────

    /**
     * Seller tạo phiên đấu giá.
     * Auction được tạo với status PENDING — không hiển thị công khai cho đến khi Admin duyệt.
     *
     * @return Auction vừa tạo, hoặc null nếu lỗi DB
     */
    public Auction createAuction(String itemId, String sellerId,
                                 double startingPrice, double priceStep,
                                 java.time.LocalDateTime startTime,
                                 java.time.LocalDateTime endTime) {

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (startingPrice <= 0 || priceStep <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm và bước giá phải > 0.");
        }

        Auction auction = new Auction(itemId, sellerId, startingPrice, priceStep, startTime, endTime);
        boolean saved = auctionRepository.save(auction);
        if (!saved) {
            System.err.println("[LifecycleService] Không thể lưu phiên mới: " + auction.getId());
            return null;
        }

        System.out.printf("[LifecycleService] Tạo phiên thành công: %s (PENDING)%n", auction.getId());
        return auction;
    }

    // ──────────────────────────────────────────────
    // APPROVE / REJECT (Admin)
    // ──────────────────────────────────────────────

    /**
     * Admin duyệt phiên: PENDING → OPEN.
     * Sau khi approve mới schedule start/end.
     *
     * @param auctionId ID phiên
     * @param adminId   ID Admin thực hiện
     */
    public boolean approveAuction(String auctionId, String adminId) {
        Auction auction = requireAuction(auctionId);

        if (!auction.isPending()) {
            System.err.printf("[LifecycleService] Approve thất bại: phiên=%s không ở PENDING (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return false;
        }

        auction.markAsOpen();
        boolean updated = auctionRepository.updateStatus(auctionId, AuctionStatus.OPEN);
        if (!updated) return false;

        System.out.printf("[LifecycleService] Phiên %s được duyệt bởi %s → OPEN%n", auctionId, adminId);

        // Schedule start/end CHỈ sau khi approve
        schedulerService.scheduleAuction(auctionId, auction.getStartTime(), auction.getEndTime());

        frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.OPEN);
        return true;
    }

    /**
     * Admin từ chối phiên: PENDING → CANCELED (ADMIN_REJECTED).
     *
     * @param auctionId ID phiên
     * @param adminId   ID Admin thực hiện
     */
    public boolean rejectAuction(String auctionId, String adminId) {
        Auction auction = requireAuction(auctionId);

        if (!auction.isPending()) {
            System.err.printf("[LifecycleService] Reject thất bại: phiên=%s không ở PENDING%n", auctionId);
            return false;
        }

        auction.markAsCanceled(CancelReason.ADMIN_REJECTED, adminId);
        boolean updated = auctionRepository.updateCancelInfo(
                auctionId, AuctionStatus.CANCELED, CancelReason.ADMIN_REJECTED, adminId);

        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bị từ chối bởi Admin %s%n", auctionId, adminId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);
        }
        return updated;
    }

    // ──────────────────────────────────────────────
    // START / FINISH (Scheduler gọi)
    // ──────────────────────────────────────────────

    /**
     * Bắt đầu phiên: OPEN → RUNNING.
     * Gọi bởi AuctionSchedulerService khi đến startTime.
     */
    public void startAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            System.err.println("[LifecycleService] startAuction: phiên không tồn tại: " + auctionId);
            return;
        }
        if (!auction.isOpen()) {
            System.err.printf("[LifecycleService] startAuction: phiên=%s không ở OPEN (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return;
        }

        auction.markAsRunning();
        boolean updated = auctionRepository.updateStatus(auctionId, AuctionStatus.RUNNING);
        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bắt đầu → RUNNING%n", auctionId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.RUNNING);
        }
    }

    /**
     * Kết thúc phiên: RUNNING → FINISHED hoặc CANCELED.
     * Gọi bởi AuctionSchedulerService khi đến endTime.
     *
     * Nếu không có bidder → CANCELED (NO_BIDDER).
     * Nếu có bidder        → FINISHED, broadcast winner.
     */
    public void finishAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            System.err.println("[LifecycleService] finishAuction: phiên không tồn tại: " + auctionId);
            return;
        }
        if (!auction.isRunning()) {
            System.err.printf("[LifecycleService] finishAuction: phiên=%s không ở RUNNING (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return;
        }

        if (!auction.hasBids()) {
            // Không có ai đặt giá → huỷ
            auction.markAsCanceled(CancelReason.NO_BIDDER, "SYSTEM");
            auctionRepository.updateCancelInfo(
                    auctionId, AuctionStatus.CANCELED, CancelReason.NO_BIDDER, "SYSTEM");

            System.out.printf("[LifecycleService] Phiên %s kết thúc không có bidder → CANCELED%n", auctionId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);

        } else {
            // Có người thắng → FINISHED
            auction.markAsFinished();
            auctionRepository.updateStatus(auctionId, AuctionStatus.FINISHED);

            System.out.printf("[LifecycleService] Phiên %s kết thúc → FINISHED | Winner: %s | Giá: %.0f%n",
                    auctionId, auction.getLeadingBidderId(), auction.getCurrentPrice());

            frontendNotifier.broadcastAuctionFinished(
                    auctionId, auction.getLeadingBidderId(), auction.getCurrentPrice());
        }
    }

    // ──────────────────────────────────────────────
    // CANCEL (Buyer reject payment)
    // ──────────────────────────────────────────────

    /**
     * Buyer từ chối thanh toán: FINISHED → CANCELED (BUYER_REJECTED).
     * Gọi bởi PaymentService.rejectPayment() — không gọi trực tiếp từ Controller.
     *
     * @param auctionId ID phiên
     * @param buyerId   ID người thắng (buyer)
     */
    public boolean cancelAfterRejection(String auctionId, String buyerId) {
        Auction auction = requireAuction(auctionId);

        if (!auction.isFinished()) {
            System.err.printf("[LifecycleService] cancelAfterRejection: phiên=%s không ở FINISHED%n", auctionId);
            return false;
        }

        auction.markAsCanceled(CancelReason.BUYER_REJECTED, buyerId);
        boolean updated = auctionRepository.updateCancelInfo(
                auctionId, AuctionStatus.CANCELED, CancelReason.BUYER_REJECTED, buyerId);

        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bị huỷ do Buyer %s từ chối%n", auctionId, buyerId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);
        }
        return updated;
    }

    // ──────────────────────────────────────────────
    // HELPER
    // ──────────────────────────────────────────────

    private Auction requireAuction(String auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Phiên đấu giá không tồn tại: " + auctionId));
    }
}