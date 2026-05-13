package exception.user;

/**
 * Ném khi không tìm thấy User theo id hoặc email trong Repository.
 *
 * Dùng tại:
 *   - UserService.login()        → findByEmail() trả về empty
 *   - UserService.findById()     → findById() trả về empty
 *
 * Ví dụ sử dụng:
 *   throw new UserNotFoundException("email", "alice@example.com");
 *   throw new UserNotFoundException("id", userId);
 */
public class UserNotFoundException extends UserException {

    private static final String CODE = "USER_NOT_FOUND";

    public UserNotFoundException(String field, String value) {
        super(CODE, "Không tìm thấy tài khoản với " + field + ": " + value);
    }

    public UserNotFoundException(String message) {
        super(CODE, message);
    }
}
