package exception.user;

/**
 * Ném khi dữ liệu người dùng nhập vào không vượt qua validation.
 *
 * Bao gồm: username quá ngắn/dài, email sai định dạng, password quá ngắn.
 *
 * Dùng tại: UserValidator.validateUsername/Email/Password()
 *
 * Ví dụ sử dụng:
 *   throw new InvalidUserDataException("username", "Tên đăng nhập phải từ 3 đến 20 ký tự.");
 *   throw new InvalidUserDataException("email", "Định dạng email không hợp lệ: " + email);
 *   throw new InvalidUserDataException("password", "Mật khẩu phải có ít nhất 6 ký tự.");
 */
public class InvalidUserDataException extends UserException {

    private static final String CODE = "USER_INVALID_DATA";

    /**
     * @param field   Tên trường bị lỗi: "username", "email", "password"
     * @param message Mô tả lỗi cụ thể
     */
    public InvalidUserDataException(String field, String message) {
        super(CODE, "[" + field.toUpperCase() + "] " + message);
    }

    public InvalidUserDataException(String message) {
        super(CODE, message);
    }
}

