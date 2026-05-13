package model.Auction;
import model.root.BaseEntity;

import java.time.LocalDateTime;

/**
 * Một lần đặt giá thành công trong phiên đấu giá.
 * Immutable sau khi tạo — không có setter.
 * Được persist xuống bảng bid_transactions ngay sau khi AuctionService.applyBid().
 */
public class BidTransaction extends BaseEntity {

    private final String        bidderId;
    private final String        auctionId;
    private final double        amount;
    private final LocalDateTime timestamp; // thời điểm đặt giá

    /** Constructor tạo mới — gọi bởi AuctionService khi bid được chấp nhận. */
    public BidTransaction(String bidderId, String auctionId, double amount) {
        super();
        this.bidderId  = bidderId;
        this.auctionId = auctionId;
        this.amount    = amount;
        this.timestamp = LocalDateTime.now();
    }

    /** Constructor load từ DB — gọi bởi BidTransactionRepository. */
    public BidTransaction(String id, String bidderId, String auctionId,
                          double amount, LocalDateTime timestamp) {
        super(id, timestamp, timestamp); // createdAt = updatedAt = bid_time
        this.bidderId  = bidderId;
        this.auctionId = auctionId;
        this.amount    = amount;
        this.timestamp = timestamp;
    }

    public String        getBidderId()  { return bidderId; }
    public String        getAuctionId() { return auctionId; }
    public double        getAmount()    { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}