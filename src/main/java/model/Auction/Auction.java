package model.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import model.root.BaseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quy tắc:
 *  - Chỉ Service được gọi các method thay đổi trạng thái.
 *  - Service phải persist xuống DB SAU khi gọi method trên entity.
 */

public class Auction extends BaseEntity {

    private final String        itemId;
    private final String        sellerId;
    private final double        startingPrice;
    private double              currentPrice;
    private final double        priceStep;
    private String              leadingBidderId;   // null nếu chưa ai bid
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private AuctionStatus status;
    private CancelReason cancelReason;      // null nếu chưa huỷ
    private String              canceledBy;        // userId hoặc "SYSTEM", null nếu chưa huỷ

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 1 — tạo mới (AuctionLifecycleService gọi)
    // ──────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới.
     * Status mặc định là PENDING — chờ Admin duyệt.
     */
    public Auction(String itemId, String sellerId,
                   double startingPrice, double priceStep,
                   LocalDateTime startTime, LocalDateTime endTime) {
        super();
        this.itemId          = itemId;
        this.sellerId        = sellerId;
        this.startingPrice   = startingPrice;
        this.currentPrice    = startingPrice;
        this.priceStep       = priceStep;
        this.startTime       = startTime;
        this.endTime         = endTime;
        this.status          = AuctionStatus.PENDING;
        this.leadingBidderId = null;
        this.cancelReason    = null;
        this.canceledBy      = null;
    }

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 2 — load từ DB (AuctionRepository gọi)
    // ──────────────────────────────────────────────

    public Auction(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   String itemId, String sellerId,
                   double startingPrice, double currentPrice, double priceStep,
                   String leadingBidderId,
                   LocalDateTime startTime, LocalDateTime endTime,
                   AuctionStatus status,
                   CancelReason cancelReason, String canceledBy) {
        super(id, createdAt, updatedAt);
        this.itemId          = itemId;
        this.sellerId        = sellerId;
        this.startingPrice   = startingPrice;
        this.currentPrice    = currentPrice;
        this.priceStep       = priceStep;
        this.leadingBidderId = leadingBidderId;
        this.startTime       = startTime;
        this.endTime         = endTime;
        this.status          = status;
        this.cancelReason    = cancelReason;
        this.canceledBy      = canceledBy;
    }

    // ──────────────────────────────────────────────
    // GETTERS
    // ──────────────────────────────────────────────

    public String        getItemId()          { return itemId; }
    public String        getSellerId()        { return sellerId; }
    public double        getStartingPrice()   { return startingPrice; }
    public double        getCurrentPrice()    { return currentPrice; }
    public double        getPriceStep()       { return priceStep; }
    public String        getLeadingBidderId() { return leadingBidderId; }
    public LocalDateTime getStartTime()       { return startTime; }
    public LocalDateTime getEndTime()         { return endTime; }
    public AuctionStatus getStatus()          { return status; }
    public CancelReason  getCancelReason()    { return cancelReason; }
    public String        getCanceledBy()      { return canceledBy; }

    // ──────────────────────────────────────────────
    // BUSINESS METHODS — chỉ Service được gọi
    // ──────────────────────────────────────────────

    /**
     * Admin approve: PENDING → OPEN.
     * Sau khi gọi, AuctionLifecycleService phải:
     *   1. updateStatus(id, OPEN) xuống DB
     *   2. schedule start/end qua AuctionSchedulerService
     */
    public void markAsOpen() {
        if (this.status != AuctionStatus.PENDING)
            throw new IllegalStateException(
                    "Chỉ chuyển OPEN từ PENDING. Hiện tại: " + this.status);
        this.status = AuctionStatus.OPEN;
        markUpdated();
    }

    /**
     * Scheduler kích hoạt: OPEN → RUNNING.
     * Gọi bởi AuctionLifecycleService.startAuction().
     */
    public void markAsRunning() {
        if (this.status != AuctionStatus.OPEN)
            throw new IllegalStateException(
                    "Chỉ chuyển RUNNING từ OPEN. Hiện tại: " + this.status);
        this.status = AuctionStatus.RUNNING;
        markUpdated();
    }

    /**
     * Scheduler kết thúc: RUNNING → FINISHED.
     * Gọi bởi AuctionLifecycleService.finishAuction().
     * Nếu không có bidder → CANCELED (NO_BIDDER) thay vì FINISHED — xử lý ở Service.
     */
    public void markAsFinished() {
        if (this.status != AuctionStatus.RUNNING)
            throw new IllegalStateException(
                    "Chỉ chuyển FINISHED từ RUNNING. Hiện tại: " + this.status);
        this.status = AuctionStatus.FINISHED;
        markUpdated();
    }

    /**
     * Thanh toán thành công: FINISHED → PAID.
     * Gọi bởi PaymentService.processPayment().
     */
    public void markAsPaid() {
        if (this.status != AuctionStatus.FINISHED)
            throw new IllegalStateException(
                    "Chỉ chuyển PAID từ FINISHED. Hiện tại: " + this.status);
        this.status = AuctionStatus.PAID;
        markUpdated();
    }

    /**
     * Huỷ phiên.
     * Cho phép từ: PENDING (Admin reject), RUNNING (NO_BIDDER), FINISHED (Buyer reject).
     * @param reason     Lý do huỷ — xem enum CancelReason
     * @param canceledBy userId của Buyer, hoặc "SYSTEM"
     */
    public void markAsCanceled(CancelReason reason, String canceledBy) {
        if (this.status == AuctionStatus.PAID)
            throw new IllegalStateException("Không thể huỷ phiên đã PAID.");
        if (this.status == AuctionStatus.CANCELED)
            throw new IllegalStateException("Phiên đã bị huỷ rồi.");
        if (this.status == AuctionStatus.OPEN )
            throw new IllegalStateException("Không thể huỷ phiên trong trạng thái " + this.status);
        this.status       = AuctionStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledBy   = canceledBy;
        markUpdated();
    }

    /**
     * Áp dụng bid vào RAM.
     * Chỉ cập nhật currentPrice và leadingBidderId.
     * BidService chịu trách nhiệm persist xuống DB trong transaction.
     */
    public void applyBid(String bidderId, double amount) {
        this.currentPrice    = amount;
        this.leadingBidderId = bidderId;
        markUpdated();
    }

    // ──────────────────────────────────────────────
    // UTILITY
    // ──────────────────────────────────────────────
    public boolean isPending()  { return status == AuctionStatus.PENDING; }
    public boolean isOpen()     { return status == AuctionStatus.OPEN; }
    public boolean isRunning()  { return status == AuctionStatus.RUNNING; }
    public boolean isFinished() { return status == AuctionStatus.FINISHED; }
    public boolean isPaid()     { return status == AuctionStatus.PAID; }
    public boolean isCanceled() { return status == AuctionStatus.CANCELED; }
    public boolean hasBids()    { return leadingBidderId != null; }

    /** Giá bid tối thiểu tiếp theo hợp lệ.*/
    public double getMinNextBid() {
        return currentPrice + priceStep;
    }
}