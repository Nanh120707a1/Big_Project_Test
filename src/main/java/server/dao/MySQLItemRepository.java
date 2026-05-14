package server.dao;


import model.item.Item;
import model.item.ItemType;
import server.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQLItemRepository — implement ItemRepository.
 *
 * Schema bảng items:
 *   id, item_type, name, description, original_price,
 *   owner_id, image_url, created_at, updated_at
 *
 * Lưu ý: không còn cột {@code status} — trạng thái phiên nằm ở bảng auctions.
 */
public class MySQLItemRepository implements ItemRepository {

    // =====================================================================
    // SAVE — đơn lẻ
    // =====================================================================

    @Override
    public boolean save(Item item) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return save(conn, item);
        } catch (SQLException e) {
            System.err.println("[ItemRepo] save thất bại: " + item.getId());
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================================
    // SAVE — trong transaction (AuctionService.createAuction() gọi)
    // =====================================================================

    @Override
    public boolean save(Connection conn, Item item) throws SQLException {
        String sql = """
                INSERT INTO items
                    (id, item_type, name, description, original_price,
                     owner_id, image_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, item.getItemType().name());
            ps.setString(3, item.getItemName());
            ps.setString(4, item.getItemDescription());
            ps.setDouble(5, item.getOriginalPrice());
            ps.setString(6, item.getOwnerId());
            ps.setString(7, item.getImageUrl());           // null-safe — JDBC chấp nhận null
            ps.setTimestamp(8, Timestamp.valueOf(item.getCreatedAt()));
            ps.setTimestamp(9, Timestamp.valueOf(item.getUpdatedAt()));
            return ps.executeUpdate() > 0;
        }
    }

    // =====================================================================
    // FIND BY ID
    // =====================================================================

    @Override
    public Optional<Item> findById(String itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[ItemRepo] findById thất bại: " + itemId);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // =====================================================================
    // FIND BY SELLER
    // =====================================================================

    @Override
    public List<Item> findBySeller(String ownerId) {
        String sql = "SELECT * FROM items WHERE owner_id = ? ORDER BY created_at DESC";
        return queryList(sql, ownerId);
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private List<Item> queryList(String sql, String param) {
        List<Item> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[ItemRepo] queryList thất bại.");
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Map một ResultSet row → Item.
     * Không còn cột {@code status} — bỏ so với phiên bản cũ.
     */
    private Item mapRow(ResultSet rs) throws SQLException {
        String        id          = rs.getString("id");
        ItemType      itemType    = ItemType.valueOf(rs.getString("item_type"));
        String        name        = rs.getString("name");
        String        description = rs.getString("description");
        double        price       = rs.getDouble("original_price");
        String        ownerId     = rs.getString("owner_id");
        String        imageUrl    = rs.getString("image_url");    // có thể null
        LocalDateTime createdAt   = rs.getTimestamp("created_at").toLocalDateTime();
        Timestamp     updatedAtTs = rs.getTimestamp("updated_at");
        LocalDateTime updatedAt   = updatedAtTs != null ? updatedAtTs.toLocalDateTime() : createdAt;

        return new Item(id, createdAt, updatedAt,
                name, description, price,
                ownerId, itemType, imageUrl);
    }
}