package exception.auth;

/**
 * Ném khi token đã tồn tại nhưng đã vượt quá SESSION_TIMEOUT_MINUTES.
 *
 * Dùng tại: SessionManager.getUserByToken() khi entry.isExpired() == true
 *
 * Ví dụ sử dụng:
 *   user đăng nhập -> sau 30 phút session hết hạn -> throw new SessionExpiredException(token); báo user đăng nhập lại
 */
public class SessionExpiredException extends AuthException {

    private static final String CODE = "AUTH_SESSION_EXPIRED";

    public SessionExpiredException(String token) {
        super(CODE, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại. (token: " + safePrefix(token) + ")"); // gọi lớp cha AuthException(String code, String message)
    }

    public SessionExpiredException() {
        super(CODE, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }

    // Ví dụ token thât: abcd1234xyz98765 -> hiển thị: abcd1234... để tránh lộ token đầy đủ trong log
    private static String safePrefix(String token) {
        if (token == null || token.length() < 8) return "???";
        return token.substring(0, 8) + "...";
    }
}
