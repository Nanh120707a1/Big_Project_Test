package server.dao;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * AuctionRepository — tầng duy nhất được phép đọc/ghi bảng `auctions`.
 *
 * Schema bảng auctions:
 *   id, item_id, seller_id, starting_price, current_price, price_step,
 *   leading_bidder_id, start_time, end_time, status,
 *   cancel_reason, canceled_by, created_at, updated_at
 *
 * Quy ước:
 *  A. Method không có Connection tham số → tự mở connection, dùng cho query đơn.
 *  B. Method có Connection tham số     → caller (Service) quản lý transaction,
 *     repository chỉ execute SQL, KHÔNG commit/rollback/close connection đó.
 */
public interface AuctionRepository {

    // ─── WRITE ───────────────────────────────────────────────────────────

    /**
     * INSERT thuần — tạo phiên mới với status PENDING.
     * Gọi khi Seller submit phiên đấu giá.
     */
    boolean save(Auction auction);

    /**
     * Cập nhật trạng thái phiên.
     * Dùng cho các transition đơn: PENDING→OPEN, OPEN→RUNNING, RUNNING→FINISHED, v.v.
     */
    boolean updateStatus(String auctionId, AuctionStatus status);

    /**
     * Cập nhật status + cancel info trong một lần gọi.
     * Dùng khi huỷ phiên để tránh 2 query riêng lẻ.
     */
    boolean updateCancelInfo(String auctionId, AuctionStatus status,
                             CancelReason reason, String canceledBy);

    /**
     * Cập nhật giá hiện tại và người dẫn đầu.
     * Dùng khi không cần atomic với bid_transactions (ít dùng trực tiếp).
     */
    boolean updateCurrentBid(String auctionId, double newPrice, String leadingBidderId);

    /**
     * Overload nhận Connection — dùng trong JDBC atomic transaction của BidService.placeBid().
     * Đảm bảo INSERT bid_transactions và UPDATE auctions cùng commit/rollback.
     * Caller KHÔNG được close Connection này.
     */
    boolean updateCurrentBid(Connection conn, String auctionId,
                             double newPrice, String leadingBidderId) throws SQLException;

    /**
     * Cập nhật status trong transaction — dùng bởi PaymentService.
     * Đảm bảo withdraw + deposit + update status cùng một transaction.
     */
    boolean updateStatus(Connection conn, String auctionId,
                         AuctionStatus status) throws SQLException;

    // ─── READ ────────────────────────────────────────────────────────────

    Optional<Auction> findById(String auctionId);

    /** Lấy tất cả phiên chưa PAID/CANCELED — dùng cho danh sách active. */
    List<Auction> findAllActive();

    // Nhung thêm
    /** Lấy tất cả phiên theo status
     * Dùng trong màn hình của Admin PENDING*/
    List<Auction> findByStatus(AuctionStatus status);

    /** Lấy nhiều trạng thái cùng lúc
     * Dùng trong MainView --> Cần (OPEN, RUNNING, FINISHED)
     */
    List<Auction> findByStatus(List<AuctionStatus> statuses);

    /** Lấy tất cả phiên của một Seller (mọi status). */
    List<Auction> findBySeller(String sellerId);

    /** Lấy tất cả phiên mà một Bidder đã tham gia (join bid_transactions). */
    List<Auction> findByBidder(String bidderId);

}