package exception.auth;

import exception.root.AppException;

/**
 * Nhóm exception liên quan đến xác thực & phiên đăng nhập.
 *
 * Phân cấp:
 *   AuthException (base)
 *     ├── InvalidCredentialsException   → sai email hoặc mật khẩu
 *     ├── SessionExpiredException       → token hết hạn
 *     ├── SessionNotFoundException      → token không tồn tại
 *     └── UnauthorizedActionException   → đã login nhưng không đủ quyền
 */
public class AuthException extends AppException {
    // Constructor 1
    public AuthException(String errorCode, String message) {
        super(errorCode, message);
    }

    // Constructor 2
    public AuthException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}