package exception.auth;

/**
 * Ném khi email hoặc mật khẩu không khớp với dữ liệu trong hệ thống.
 *
 * Dùng tại: UserService.login()
 *
 * Lưu ý bảo mật: KHÔNG nói rõ "sai email" hay "sai mật khẩu" để tránh
 * lộ thông tin tài khoản nào tồn tại. Thông báo nên chung chung.
 *
 * Ví dụ sử dụng:
 *   throw new InvalidCredentialsException();
 *   throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác.");
 */
public class InvalidCredentialException extends AuthException {

    private static final String CODE = "AUTH_INVALID_CREDENTIALS";
    private static final String DEFAULT_MSG = "Email hoặc mật khẩu không chính xác.";

    public InvalidCredentialException() {
        super(CODE, DEFAULT_MSG);
    }

    public InvalidCredentialException(String message) {
        super(CODE, message);
    }
}
