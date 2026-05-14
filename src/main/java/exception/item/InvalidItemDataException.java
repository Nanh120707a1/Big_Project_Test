package exception.item;

/**
 * Ném khi dữ liệu đầu vào của Item không vượt qua validation trong ItemFactory.
 *
 * Bao gồm các trường hợp:
 *   - Tên sản phẩm null/blank
 *   - Mô tả null/blank
 *   - originalPrice < 0
 *   - ownerId null/blank
 *   - extraFields thiếu hoặc blank (brand, model, artist, ...)
 *   - auctionStart >= auctionEnd
 *   - itemType không hợp lệ
 *
 * Dùng tại:
 *   - ItemFactory.validateBaseFields()
 *   - ItemFactory.assertExtraFields()
 *   - ItemService.submitItem() — kiểm tra auctionStart/auctionEnd
 *
 * Ví dụ sử dụng:
 *   throw new InvalidItemDataException("name", "Tên sản phẩm không được để trống.");
 *   throw new InvalidItemDataException("originalPrice", "Giá gốc phải >= 0.");
 *   throw new InvalidItemDataException("auctionTime", "Thời gian đóng phiên phải sau thời gian mở.");
 *   throw new InvalidItemDataException("ELECTRONICS cần 3 trường bổ sung, chỉ nhận được 2.");
 */
public class InvalidItemDataException extends ItemException {

    private static final String CODE = "ITEM_INVALID_DATA";

    /**
     * Constructor với tên trường cụ thể.
     *
     * @param field   Tên trường bị lỗi: "name", "description", "originalPrice", ...
     * @param message Mô tả lỗi
     */
    public InvalidItemDataException(String field, String message) {
        super(CODE, "[" + field.toUpperCase() + "] " + message);
    }

    /**
     * Constructor không có tên trường — dùng khi lỗi liên quan nhiều trường.
     */
    public InvalidItemDataException(String message) {
        super(CODE, message);
    }
}