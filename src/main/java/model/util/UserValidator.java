package model.util;


/**
 * UTILITY CLASS -> ko cần khai báo, gọi trực tiếp
 * Validate toàn bộ dữ liệu đầu vào liên quan đến User.
 *
 * Mỗi method throw {@link InvalidUserDataException} (subclass của AppException)
 * Các method:
 *   validateUsername(username)                        → 3–20 ký tự, không blank
 *   validateEmail(email)                              → đúng định dạng email
 *   validatePassword(password)                        → tối thiểu 6 ký tự
 *   validateConfirmPassword(password, confirmPassword)→ confirmPassword khớp password
 */
import java.util.regex.Pattern;

import exception.user.InvalidUserDataException;

public class UserValidator {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 6;

    // Utility class - ko cho tạo instance
    private UserValidator () {}

    /**
     * Kiểm tra username hợp lệ: không blank, độ dài từ 3 đến 20 ký tự.
     *
     * @param username Tên đăng nhập người dùng nhập vào
     * @throws InvalidUserDataException nếu không hợp lệ
     */
    public static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidUserDataException("username",
                    "Tên đăng nhập không được để trống.");
        }
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new InvalidUserDataException("username",
                    "Tên đăng nhập phải từ " + USERNAME_MIN
                            + " đến " + USERNAME_MAX + " ký tự. "
                            + "(Hiện tại: " + username.length() + " ký tự)");
        }
    }

    /**
     * Kiểm tra email đúng định dạng (ví dụ: alice@example.com).
     * Không kiểm tra trùng lặp ở đây — đó là nhiệm vụ của UserService.
     *
     * @param email Email người dùng nhập vào
     * @throws InvalidUserDataException nếu không hợp lệ
     */
    public static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidUserDataException("email",
                    "Email không được để trống.");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserDataException("email",
                    "Định dạng email không hợp lệ: " + email
                            + ". Ví dụ hợp lệ: alice@example.com");
        }
    }

    /**
     * Kiểm tra password đủ độ dài tối thiểu.
     *
     * @param password Mật khẩu thô người dùng nhập vào
     * @throws InvalidUserDataException nếu không hợp lệ
     */
    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidUserDataException("password",
                    "Mật khẩu không được để trống.");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new InvalidUserDataException("password",
                    "Mật khẩu phải có ít nhất " + PASSWORD_MIN + " ký tự. "
                            + "(Hiện tại: " + password.length() + " ký tự)");
        }
    }

    /**
     * Kiểm tra confirmPassword có khớp với password hay không.
     * Gọi SAU validatePassword() — đảm bảo password đã hợp lệ trước khi so sánh.
     *
     * Thứ tự gọi chuẩn trong register():
     *   1. validatePassword(rawPassword)
     *   2. validateConfirmPassword(rawPassword, confirmPassword)
     *
     * @param rawPassword     Mật khẩu gốc đã qua validatePassword()
     * @param confirmPassword Mật khẩu xác nhận người dùng nhập lần 2
     * @throws InvalidUserDataException nếu 2 mật khẩu không khớp
     */
    public static void validateConfirmPassword(String rawPassword, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new InvalidUserDataException("confirmPassword",
                    "Xác nhận mật khẩu không được để trống.");
        }
        if (!rawPassword.equals(confirmPassword)) {
            throw new InvalidUserDataException("confirmPassword",
                    "Xác nhận mật khẩu không khớp. Vui lòng nhập lại.");
        }
    }
}

