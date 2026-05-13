package model.Auction;
/**
 * Trạng thái vòng đời của một phiên đấu giá.
 *
 * Flow hợp lệ:
 *   PENDING → OPEN → RUNNING → FINISHED → PAID
 *   PENDING → CANCELED  (Admin reject)
 *   FINISHED → CANCELED (Buyer reject payment)
 *   RUNNING → CANCELED  (không có bidder khi finish → NO_BIDDER)
 */
public enum AuctionStatus {
    PENDING,
    OPEN,
    RUNNING,
    FINISHED,
    PAID,
    CANCELED
}