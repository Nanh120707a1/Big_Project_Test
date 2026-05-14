package exception.item;

import exception.root.AppException;

/**
 * Nhóm exception liên quan đến Item (sản phẩm đấu giá).
 *
 * Phân cấp:
 *   ItemException (base)
 *     ├── ItemNotFoundException         → không tìm thấy item theo id
 *     └── InvalidItemDataException      → dữ liệu item không hợp lệ (tên trống, giá âm,...)
 */
public class ItemException extends AppException {
    public ItemException(String errorCode, String message) {
        super(errorCode, message);
    }
    public ItemException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
