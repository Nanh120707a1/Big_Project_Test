package server.dao;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import server.dto.AuctionCardDTO;
import model.item.ItemType;
import server.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQLAuctionRepository — implementation duy nhất của AuctionRepository.
 *
 * Schema bảng auctions:
 *   id, item_id, seller_id, current_price, price_step,
 *   leading_bidder_id, start_time, end_time, status,
 *   cancel_reason, canceled_by, created_at, updated_at
 *
 * Lưu ý quan trọng:
 *  - KHÔNG còn cột starting_price trong schema — đã xoá hoàn toàn.
 *  - price_step được lưu xuống DB (tính sẵn trong Auction constructor).
 *  - save(Connection, Auction) dùng cho transaction chung với ItemRepository.
 */
public class MySQLAuctionRepository implements AuctionRepository {

    // ─── INSERT SQL dùng chung cho cả 2 overload save() ─────────────────
    private static final String INSERT_SQL = """
            INSERT INTO auctions
                (id, item_id, seller_id, current_price, price_step,
                 leading_bidder_id, start_time, end_time, status,
                 cancel_reason, canceled_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // ─── WRITE ───────────────────────────────────────────────────────────

    /**
     * INSERT thuần — tự mở connection.
     * Dùng khi không cần transaction chung với ItemRepository.
     */
    @Override
    public boolean save(Auction auction) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return save(conn, auction);
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] save thất bại: " + auction.getId());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * INSERT trong transaction do caller quản lý.
     * Caller KHÔNG được close Connection này.
     *
     * Thứ tự parameter phải khớp INSERT_SQL:
     *   1  id
     *   2  item_id
     *   3  seller_id
     *   4  current_price
     *   5  price_step
     *   6  leading_bidder_id  (null khi tạo mới)
     *   7  start_time
     *   8  end_time
     *   9  status             ("PENDING")
     *   10 cancel_reason      (null khi tạo mới)
     *   11 canceled_by        (null khi tạo mới)
     *   12 created_at
     *   13 updated_at
     */
    @Override
    public boolean save(Connection conn, Auction auction) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1,    auction.getId());
            ps.setString(2,    auction.getItemId());
            ps.setString(3,    auction.getSellerId());
            ps.setDouble(4,    auction.getCurrentPrice());
            ps.setDouble(5,    auction.getPriceStep());

            // leading_bidder_id — null khi tạo mới
            if (auction.getLeadingBidderId() != null) {
                ps.setString(6, auction.getLeadingBidderId());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }

            ps.setTimestamp(7,  Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(8,  Timestamp.valueOf(auction.getEndTime()));
            ps.setString(9,     auction.getStatus().name());

            // cancel_reason — null khi tạo mới
            if (auction.getCancelReason() != null) {
                ps.setString(10, auction.getCancelReason().name());
            } else {
                ps.setNull(10, Types.VARCHAR);
            }

            // canceled_by — null khi tạo mới
            if (auction.getCanceledBy() != null) {
                ps.setString(11, auction.getCanceledBy());
            } else {
                ps.setNull(11, Types.VARCHAR);
            }

            ps.setTimestamp(12, Timestamp.valueOf(auction.getCreatedAt()));
            ps.setTimestamp(13, Timestamp.valueOf(
                    auction.getUpdatedAt() != null ? auction.getUpdatedAt() : LocalDateTime.now()));

            return ps.executeUpdate() > 0;
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
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findById thất bại: " + auctionId);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public List<Auction> findAllActive() {
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
        String sql = """
                SELECT DISTINCT a.*
                FROM auctions a
                JOIN bid_transactions b ON b.auction_id = a.id
                WHERE b.bidder_id = ?
                ORDER BY a.start_time DESC
                """;
        return queryList(sql, bidderId);
    }

    /**
     * JOIN auctions + items → AuctionCardDTO dùng cho MainView.
     * Chỉ lấy phiên chưa PAID/CANCELED.
     */
    @Override
    public List<AuctionCardDTO> findMainViewAuctions() {
        String sql = """
                SELECT
                    a.id            AS auction_id,
                    a.current_price,
                    a.price_step,
                    a.leading_bidder_id,
                    a.start_time,
                    a.end_time,
                    a.status,
                    i.id            AS item_id,
                    i.name          AS item_name,
                    i.description,
                    i.image_url,
                    i.item_type
                FROM auctions a
                JOIN items i ON a.item_id = i.id
                WHERE a.status NOT IN ('PAID', 'CANCELED')
                ORDER BY a.start_time ASC
                """;

        List<AuctionCardDTO> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapCardRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findMainViewAuctions thất bại.");
            e.printStackTrace();
        }
        return result;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private List<Auction> queryList(String sql, String... params) {
        List<Auction> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] queryList thất bại.");
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Map một ResultSet row từ bảng auctions → Auction entity.
     *
     * Cột được đọc (phải khớp schema):
     *   id, created_at, updated_at, item_id, seller_id,
     *   current_price, price_step, leading_bidder_id,
     *   start_time, end_time, status, cancel_reason, canceled_by
     *
     * Không còn cột starting_price.
     */
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
                rs.getDouble("current_price"),
                rs.getDouble("price_step"),
                rs.getString("leading_bidder_id"),   // null-safe
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                AuctionStatus.valueOf(rs.getString("status")),
                cancelReason,
                rs.getString("canceled_by")          // null-safe
        );
    }

    /**
     * Map một row từ JOIN query → AuctionCardDTO.
     * Column aliases phải khớp SELECT trong findMainViewAuctions().
     */
    private AuctionCardDTO mapCardRow(ResultSet rs) throws SQLException {
        String cancelReasonStr = rs.getString("status");  // dùng status không phải cancel_reason ở đây
        AuctionStatus status = AuctionStatus.valueOf(cancelReasonStr);

        Timestamp startTs = rs.getTimestamp("start_time");
        Timestamp endTs   = rs.getTimestamp("end_time");

        return new AuctionCardDTO(
                rs.getString("auction_id"),
                rs.getString("item_id"),
                rs.getString("item_name"),
                rs.getString("description"),
                rs.getString("image_url"),
                ItemType.valueOf(rs.getString("item_type")),
                rs.getDouble("current_price"),
                rs.getDouble("price_step"),
                rs.getString("leading_bidder_id"),   // null-safe
                startTs.toLocalDateTime(),
                endTs.toLocalDateTime(),
                status
        );
    }
}