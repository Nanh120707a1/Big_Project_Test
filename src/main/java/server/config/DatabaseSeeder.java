package server.config;


import model.factory.UserFactory;
import model.user.Admin;
import server.dao.UserRepository;

/**
 * DatabaseSeeder
 * Tạo dữ liệu Admin mặc định vào DB khi hệ thống khởi động lần đầu.
 *
 * Cách dùng: gọi DatabaseSeeder.run() trong ServerApp.main()
 * An toàn khi gọi nhiều lần — tự kiểm tra, không tạo trùng.
 */
public class DatabaseSeeder {
    private final UserRepository userRepository;
    public DatabaseSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Chạy toàn bộ quá trình seed.
     * Thêm Admin mới vào đây nếu hệ thống cần nhiều Admin.
     */
    public void run() {
        System.out.println("[DatabaseSeeder] Bắt đầu seed dữ liệu...");

        createAdminIfNotExists("admin1", "admin1@auction.com", "Admin@123");
        createAdminIfNotExists("admin2", "admin2@auction.com", "Admin@456");

        System.out.println("[DatabaseSeeder] Seed hoàn tất.");
    }

    // =========================================================
    // PRIVATE HELPER
    // =========================================================

    /**
     * Tạo Admin nếu email chưa tồn tại trong DB.
     * Nếu đã tồn tại thì bỏ qua — không throw lỗi, không tạo trùng.
     */
    private void createAdminIfNotExists(String username, String email, String rawPassword) {
        if (userRepository.findByEmail(email).isPresent()) {
            System.out.println("[DatabaseSeeder] Đã tồn tại, bỏ qua: " + email);
            return;
        }

        // UserFactory tự hash password bên trong — không truyền password thô xuống DB
        Admin admin = UserFactory.createAdmin(username.trim(), email.trim(), rawPassword);
        userRepository.save(admin);
        System.out.println("[DatabaseSeeder] Tạo Admin thành công: " + username);
    }
}