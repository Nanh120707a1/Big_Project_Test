package exception.user;

import exception.root.AppException;

/**
 * Nhóm exception liên quan đến quản lý User (Account, Admin).
 *
 * Phân cấp:
 *   UserException (base)
 *     ├── UserNotFoundException         → không tìm thấy user theo id/email
 *     ├── InvalidUserDataException      → dữ liệu đầu vào không hợp lệ (username, email, password)
 *     └── InsufficientFundsException    → số dư ví không đủ
 */
public class UserException extends AppException {
    public UserException(String errorCode, String message) {
        super(errorCode, message);
    }
    public UserException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
