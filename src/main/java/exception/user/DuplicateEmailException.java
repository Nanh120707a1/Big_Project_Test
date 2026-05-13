package exception.user;

/**
 * Ném khi email đã được đăng ký trong hệ thống.
 *
 * Dùng tại: UserService.register() — kiểm tra trùng email trước khi tạo Account.
 *
 * Ví dụ sử dụng:
 *   throw new DuplicateEmailException("alice@example.com");
 */
public class DuplicateEmailException extends UserException {

    private static final String CODE = "USER_DUPLICATE_EMAIL";

    public DuplicateEmailException(String email) {
        super(CODE, "Email đã được sử dụng: " + email
                + ". Vui lòng dùng email khác hoặc đăng nhập.");
    }
}
