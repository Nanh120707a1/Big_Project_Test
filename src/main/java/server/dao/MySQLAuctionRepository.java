package server.dao;
import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import server.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQLAuctionRepository — implementation duy nhất của AuctionRepository.
 */
public class MySQLAuctionRepository implements AuctionRepository {

    // ─── WRITE ───────────────────────────────────────────────────────────

    @Override
    public boolean save(Auction auction) {
        String sql = """
                INSERT INTO auctions
                    (id, item_id, seller_id, starting_price, current_price, price_step,
                     leading_bidder_id, start_time, end_time, status,
                     cancel_reason, canceled_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1,  auction.getId());
            ps.setString(2,  auction.getItemId());
            ps.setString(3,  auction.getSellerId());
            ps.setDouble(4,  auction.getStartingPrice());
            ps.setDouble(5,  auction.getCurrentPrice());
            ps.setDouble(6,  auction.getPriceStep());
            ps.setString(7,  auction.getLeadingBidderId()); // null-safe
            ps.setTimestamp(8,  Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(9,  Timestamp.valueOf(auction.getEndTime()));
            ps.setString(10, auction.getStatus().name());   // "PENDING"
            ps.setNull(11, Types.VARCHAR);                  // cancel_reason = NULL khi tạo mới
            ps.setNull(12, Types.VARCHAR);                  // canceled_by   = NULL khi tạo mới
            ps.setTimestamp(13, Timestamp.valueOf(auction.getCreatedAt()));
            ps.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] save thất bại: " + auction.getId());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateStatus(String auctionId, AuctionStatus status) {
        String sql = "UPDATE auctions SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1,    status.name());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] updateStatus thất bại: " + auctionId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Dùng trong PaymentService transaction.
     * Caller quản lý Connection — KHÔNG close ở đây.
     */
    @Override
    public boolean updateStatus(Connection conn, String auctionId,
                                AuctionStatus status) throws SQLException {
        String sql = "UPDATE auctions SET status = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,    status.name());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cập nhật status + lý do huỷ trong một query — tránh 2 round-trip.
     * Dùng bởi AuctionLifecycleService.cancelAuction().
     */
    @Override
    public boolean updateCancelInfo(String auctionId, AuctionStatus status,
                                    CancelReason reason, String canceledBy) {
        String sql = """
                UPDATE auctions
                SET status = ?, cancel_reason = ?, canceled_by = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1,    status.name());
            ps.setString(2,    reason != null ? reason.name() : null);
            ps.setString(3,    canceledBy);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(5,    auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] updateCancelInfo thất bại: " + auctionId);
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateCurrentBid(String auctionId, double newPrice, String leadingBidderId) {
        String sql = """
                UPDATE auctions
                SET current_price = ?, leading_bidder_id = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1,    newPrice);
            ps.setString(2,    leadingBidderId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4,    auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] updateCurrentBid thất bại: " + auctionId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Overload nhận Connection — dùng trong BidService.placeBid() transaction.
     * Caller (BidService) chịu trách nhiệm commit/rollback và đóng connection.
     */
    @Override
    public boolean updateCurrentBid(Connection conn, String auctionId,
                                    double newPrice, String leadingBidderId) throws SQLException {
        String sql = """
                UPDATE auctions
                SET current_price = ?, leading_bidder_id = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1,    newPrice);
            ps.setString(2,    leadingBidderId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4,    auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────

    @Override
    public Optional<Auction> findById(String auctionId) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));

        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findById thất bại: " + auctionId);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public List<Auction> findAllActive() {
        // Active = chưa kết thúc vĩnh viễn
        String sql = """
                SELECT * FROM auctions
                WHERE status NOT IN ('PAID', 'CANCELED')
                ORDER BY start_time ASC
                """;
        return queryList(sql);
    }

    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        String sql = "SELECT * FROM auctions WHERE status = ? ORDER BY start_time ASC";
        return queryList(sql, status.name());
    }

    @Override
    public List<Auction> findBySeller(String sellerId) {
        String sql = "SELECT * FROM auctions WHERE seller_id = ? ORDER BY created_at DESC";
        return queryList(sql, sellerId);
    }

    @Override
    public List<Auction> findByBidder(String bidderId) {
        // JOIN để lấy các phiên mà bidder đã đặt giá
        String sql = """
                SELECT DISTINCT a.*
                FROM auctions a
                JOIN bid_transactions b ON b.auction_id = a.id
                WHERE b.bidder_id = ?
                ORDER BY a.start_time DESC
                """;
        return queryList(sql, bidderId);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private List<Auction> queryList(String sql, String... params) {
        List<Auction> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));

        } catch (SQLException e) {
            System.err.println("[AuctionRepo] queryList thất bại.");
            e.printStackTrace();
        }
        return result;
    }

    private Auction mapRow(ResultSet rs) throws SQLException {
        String cancelReasonStr = rs.getString("cancel_reason");
        CancelReason cancelReason = (cancelReasonStr != null)
                ? CancelReason.valueOf(cancelReasonStr)
                : null;

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");

        return new Auction(
                rs.getString("id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAtTs != null ? updatedAtTs.toLocalDateTime() : null,
                rs.getString("item_id"),
                rs.getString("seller_id"),
                rs.getDouble("starting_price"),
                rs.getDouble("current_price"),
                rs.getDouble("price_step"),
                rs.getString("leading_bidder_id"),  // null-safe
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                AuctionStatus.valueOf(rs.getString("status")),
                cancelReason,
                rs.getString("canceled_by")         // null-safe
        );
    }
}