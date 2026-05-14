package model.factory;

import model.item.Item;
import model.item.ItemType;

/**
 * ItemFactory — Utility class, không tạo instance.
 *
 * Validate input TRƯỚC khi tạo Item.
 * Không lưu DB — đó là nhiệm vụ của ItemService + ItemRepository.
 *
 * Lưu ý:
 *   - Không còn subclass (Electronics/Art/Vehicle đã bị xoá).
 *   - Không còn extraFields đặc thù.
 *   - Không nhận auctionStart/auctionEnd — thời gian phiên được
 *     xử lý riêng ở Auction, không lưu trong Item.
 */
public class ItemFactory {

    private ItemFactory() {}
    // ─────────────────────────────────────────────
    // GENERIC — dùng khi ItemType đến từ UI (dropdown/form)
    // ─────────────────────────────────────────────

    /**
     * Tạo Item dựa vào ItemType từ UI.
     *
     * @param itemType    ELECTRONICS | ART | VEHICLE
     * @param name        Tên sản phẩm (không blank)
     * @param description Mô tả (không blank)
     * @param originalPrice Giá khởi điểm (>= 0)
     * @param ownerId     ID Account đăng bán (không blank)
     * @param imageUrl    Đường dẫn ảnh — null nếu Seller chưa upload
     * @return Item mới với status PENDING
     */
    public static Item createByItemType(ItemType itemType,
                                        String name, String description,
                                        double originalPrice, String ownerId,
                                        String imageUrl) {
        validateFields(name, description, originalPrice, ownerId, imageUrl);
        if (itemType == null)
            throw new IllegalArgumentException("ItemType không được null.");
        return new Item(name, description, originalPrice, ownerId, itemType, imageUrl);
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPER
    // ─────────────────────────────────────────────

    private static void validateFields(String name, String description,
                                       double originalPrice, String ownerId,
                                       String imageUrl) {
        assertNotBlank("Tên sản phẩm", name);
        assertNotBlank("Mô tả", description);
        assertNotBlank("ID chủ sở hữu", ownerId);
        assertNotBlank("Ảnh sản phẩm", imageUrl);   // ← thêm dòng này
        if (originalPrice < 0)
            throw new IllegalArgumentException("Giá gốc phải >= 0, nhận: " + originalPrice);
    }

    private static void assertNotBlank(String fieldName, String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " không được để trống.");
    }
}