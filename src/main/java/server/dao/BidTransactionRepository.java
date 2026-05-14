package server.dao;

import model.Auction.BidTransaction;
import server.config.DatabaseConfig;
import server.dto.BidHistoryDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQLBidTransactionRepository
 *
 * Repository quản lý bảng:bid_transactions
 *
 * Schema:id, auction_id, bidder_id, amount, bid_time
 *
 * Trách nhiệm:
 *  - lưu lịch sử bid
 *  - query bid history
 *  - query bidder participation
 *  - query dữ liệu cho chart visualization
 *  - query realtime bid
 *
 */
public class BidTransactionRepository {

    // Danh sách cột cụ thể — tránh phụ thuộc thứ tự cột, dễ debug hơn SELECT *
    private static final String SELECT_COLS =
            "id, auction_id, bidder_id, amount, bid_time";

    // =====================================================================
    // SAVE
    // =====================================================================
    public boolean save(BidTransaction bid) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return save(conn, bid);
        } catch (SQLException e) {
            // Ném RuntimeException thay vì nuốt lỗi âm thầm
            throw new RuntimeException(
                    "[BidTransactionRepo] save thất bại, id=" + bid.getId(), e);
        }
    }

    /**
     * Overload dùng chung Connection — LUÔN gọi method này trong transaction đặt giá.
     * * Dùng bởi: BidService.placeBid()
     * Không tự commit — Service sẽ commit sau khi cả 2 thao tác thành công:
     *   1. bidTransactionRepository.save(conn, bid)
     *   2. auctionRepository.updateCurrentBid(conn, ...)
     */
    public boolean save(Connection conn, BidTransaction bid) throws SQLException {
        String sql = """
                INSERT INTO bid_transactions (id, auction_id, bidder_id, amount, bid_time)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,    bid.getId());
            ps.setString(2,    bid.getAuctionId());
            ps.setString(3,    bid.getBidderId());
            ps.setDouble(4,    bid.getAmount());
            ps.setTimestamp(5, Timestamp.valueOf(bid.getTimestamp()));
            return ps.executeUpdate() > 0;
        }
    }

    // =====================================================================
    // FIND BY AUCTION — lịch sử bid của 1 phiên
    // =====================================================================
    /**
     * Lấy toàn bộ lịch sử bid của 1 auction.
     *
     * Frontend dùng cho:
     *  [Auction Detail Screen]
     *      - lịch sử bid
     *      - realtime update
     *      - admin audit
     *
     * Hiển thị:bidderId, amount, bidTime
     */
    public List<BidTransaction> findByAuctionId(String auctionId) {
        // DESC: bid mới nhất lên trên — phù hợp hiển thị lịch sử
        String sql = "SELECT " + SELECT_COLS + " FROM bid_transactions " +
                "WHERE auction_id = ? ORDER BY bid_time DESC";
        return queryList(sql, auctionId);
    }

    // =====================================================================
    // FIND BY BIDDER — tất cả bid của 1 người dùng
    // =====================================================================
    /**
     * Lấy tất cả bid của một bidder.
     * Frontend dùng cho:
     *  [Bidder Dashboard]tab --> "Các phiên đã tham gia"
     *
     * Dùng để:
     *  - thống kê user từng bid gì
     *  - analytics
     *  - fraud detection
     */
    public List<BidTransaction> findByBidderId(String bidderId) {
        String sql = "SELECT " + SELECT_COLS + " FROM bid_transactions " +
                "WHERE bidder_id = ? ORDER BY bid_time DESC";
        return queryList(sql, bidderId);
    }

    // =====================================================================
    // FIND HIGHEST BID — bid cao nhất của 1 phiên đấu giá
    // =====================================================================
    public Optional<BidTransaction> findHighestBid(String auctionId) {
        String sql = "SELECT " + SELECT_COLS + " FROM bid_transactions " +
                "WHERE auction_id = ? ORDER BY amount DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {   // đóng ResultSet đúng cách
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "[BidTransactionRepo] findHighestBid thất bại, auctionId=" + auctionId, e);
        }
    }

    /**
     * Lấy bid mới nhất của auction.
     *
     * Frontend dùng cho:
     *  - realtime websocket
     *  - live activity feed
     *  - recent bid popup
     *
     * Khác với highest bid:
     *      latest bid = bid gần nhất theo thời gian
     */
    public Optional<BidTransaction> findLatestBid(String auctionId) {
        String sql = """
                SELECT
                    """ + SELECT_COLS + """
                FROM bid_transactions
                WHERE auction_id = ?
                ORDER BY bid_time DESC
                LIMIT 1
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));}}
        } catch (SQLException e) {
            throw new RuntimeException("[BidRepo] findLatestBid thất bại: " + auctionId, e);}
        return Optional.empty();
    }

    // ──────────────────────────────────────────────
    // READ — DTO
    // ──────────────────────────────────────────────
    /**
     * JOIN users
     * → BidHistoryDTO
     *
     * Frontend dùng cho:
     *
     *  [Auction Detail Screen]
     *      bảng lịch sử đấu giá:Username, Amount, Bid Time
     *
     *  [BidHistoryVisualization] biểu đồ đường:time → amount
     *
     * Tại sao ORDER ASC:chart cần dữ liệu theo timeline tăng dần.
     */
    public List<BidHistoryDTO> findBidHistoryDTOByAuctionId(
            String auctionId) {
        String sql = """
                SELECT
                    u.username,
                    b.amount,
                    b.bid_time
                FROM bid_transactions b
                JOIN users u
                    ON b.bidder_id = u.id
                WHERE b.auction_id = ?
                ORDER BY b.bid_time ASC
                """;
        List<BidHistoryDTO> result = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BidHistoryDTO(
                            rs.getString("username"),
                            rs.getDouble("amount"),
                            rs.getTimestamp("bid_time").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load bid history", e);
        }return result;
    }
    /**
     * Tìm tất cả auction mà bidder từng tham gia.
     *
     * Frontend dùng cho:
     *  [Bidder Dashboard]
     *      tab:"Các phiên đang tham gia"
     *      tab:"Các phiên đã thắng"
     * Trả về:auctionId list
     * Sau đó:AuctionRepository -> load AuctionCardDTO
     */
    public List<String> findAuctionIdsByBidder(String bidderId) {
        String sql = """
                SELECT DISTINCT auction_id
                FROM bid_transactions
                WHERE bidder_id = ?
                ORDER BY bid_time DESC
                """;
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("auction_id"));}
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidRepo] findAuctionIdsByBidder thất bại: " + bidderId, e
            );
        }return result;
    }

    // ──────────────────────────────────────────────
    // ANALYTICS
    // ──────────────────────────────────────────────
    /**
     * Đếm tổng số bid của một auction.
     *
     * Frontend dùng cho:
     *  [Auction Detail]"Tổng số lượt bid"
     *  [Admin Analytics]
     *  [Seller Dashboard]
     */
    public int countBidsByAuction(String auctionId) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM bid_transactions
                WHERE auction_id = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {return rs.getInt("total");}
            }
        } catch (SQLException e) {
            throw new RuntimeException("[BidRepo] countBidsByAuction thất bại: " + auctionId, e);
        }return 0;
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private List<BidTransaction> queryList(String sql, String param) {
        List<BidTransaction> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {   // đóng ResultSet đúng cách
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            // Log kèm param để biết query nào lỗi
            throw new RuntimeException(
                    "[BidTransactionRepo] queryList thất bại, param=" + param, e);
        }
        return result;
    }

    private BidTransaction mapRow(ResultSet rs) throws SQLException {
        return new BidTransaction(
                rs.getString("id"),
                rs.getString("bidder_id"),
                rs.getString("auction_id"),
                rs.getDouble("amount"),
                rs.getTimestamp("bid_time").toLocalDateTime()
        );
    }
}