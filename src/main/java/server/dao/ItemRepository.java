package server.dao;


import model.item.Item;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {

    // ── Thao tác đơn lẻ (tự mở connection) ──────────────────────────────

    /**
     * Lưu Item mới — gọi bởi AuctionService.createAuction().
     */
    boolean save(Item item);

    /**
     * Tìm Item theo ID — AuctionService dùng để lấy chi tiết sản phẩm khi cần.
     */
    Optional<Item> findById(String itemId);

    /**
     * Load toàn bộ Item của một Seller — dùng cho Seller view hiển thị chi tiết.
     */
    List<Item> findBySeller(String ownerId);

    // ── Overload nhận Connection — dùng trong transaction ────────────────

    /**
     * Cần lưu Item + Auction trong cùng 1 transaction.: Transaction overload (nhận Connection) dùng khi AuctionService.createAuction()
     * {@code itemRepository.save(conn, item)} + {@code auctionRepository.save(conn, auction)}
     */
    boolean save(Connection conn, Item item) throws SQLException;
}