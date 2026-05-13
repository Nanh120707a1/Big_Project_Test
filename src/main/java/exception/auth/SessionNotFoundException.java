package exception.auth;

/**
 * Ném khi token không tồn tại trong SessionManager (chưa đăng nhập, hoặc đã logout).
 *
 * Phân biệt với SessionExpiredException:
 *   - SessionNotFoundException  → token không có trong map (chưa login / đã logout)
 *   - SessionExpiredException   → token có trong map nhưng đã hết hạn
 *
 * Dùng tại: mọi Service method gọi requireLoggedInUser() / requireAccount()
 *
 * Ví dụ sử dụng:
 *   throw new SessionNotFoundException();
 */
public class SessionNotFoundException extends AuthException {

    private static final String CODE = "AUTH_SESSION_NOT_FOUND";

    public SessionNotFoundException() {
        super(CODE, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
    }

    public SessionNotFoundException(String message) {
        super(CODE, message);
    }
}
