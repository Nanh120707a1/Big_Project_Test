package server.dao;

import model.Auction.BidTransaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * BidTransactionRepository — persist lịch sử đặt giá.
 *
 * Schema bảng bid_transactions:
 *   id, auction_id, bidder_id, amount, bid_time, created_at
 *
 * Không thay đổi so với phiên bản cũ — interface đã đúng.
 * BidService dùng save(Connection, bid) trong atomic transaction.
 */
public interface BidTransactionRepository {

    /**
     * INSERT một BidTransaction, tự mở connection.
     * Ít dùng — BidService thường dùng overload có Connection.
     */
    boolean save(BidTransaction bid);

    /**
     * INSERT trong JDBC transaction do caller (BidService) quản lý.
     * Caller KHÔNG được close Connection này.
     * Đảm bảo bid_transactions INSERT và auctions UPDATE là atomic.
     */
    boolean save(Connection conn, BidTransaction bid) throws SQLException;

    /** Lịch sử bid của một phiên, sắp xếp tăng dần theo thời gian. */
    List<BidTransaction> findByAuction(String auctionId);

    /** Tất cả bid của một Bidder (dùng cho tab "Phiên đang tham gia"). */
    List<BidTransaction> findByBidder(String bidderId);
}
