package model.factory;


import model.user.Account;
import model.user.Admin;
import model.util.PasswordUtil;

/**
 * Factory tạo các loại User trong hệ thống.
 *
 * Trách nhiệm:
 *   - Validate toàn bộ input TRƯỚC khi tạo object.
 *   - Hash password trước khi lưu vào User.
 *   - Đảm bảo không có User nào tồn tại với dữ liệu không hợp lệ.
 *
 * Lưu ý: UserFactory KHÔNG lưu vào DB — đó là nhiệm vụ của UserService + UserRepository.
 */

public class UserFactory {
    //-------------CONSTRUCTOR-------------------
    private UserFactory () {
        // Private constructor để ngăn tạo instance của UserFactory - đây là utility class, tất cả method đều static
    }

    //--------------ACCOUNT FACTORY----------------
    /**
     * Tạo tài khoản người dùng thường sau khi validate và hash password.
     * Chỉ gọi bởi UserService.register().
     *
     * @param username    Tên đăng nhập (3–20 ký tự) — đã validate bởi UserService
     * @param email       Email hợp lệ — unique check do UserService đảm nhận
     * @param rawPassword Mật khẩu thô (chưa hash, tối thiểu 6 ký tự)
     */
    public static Account createAccount(String username, String email, String rawPassword) {
        return new Account(username, email, PasswordUtil.hashPassword(rawPassword));
    }

    //--------------ADMIN FACTORY----------------
    /**
     * Tạo tài khoản Admin — do hệ thống cấp phát, không qua đăng ký thông thường.
     * Chỉ gọi bởi UserService.provisionAdmin().
     */
    public static Admin createAdmin(String username, String email, String rawPassword) {
        return new Admin(username, email, PasswordUtil.hashPassword(rawPassword));
    }
}
