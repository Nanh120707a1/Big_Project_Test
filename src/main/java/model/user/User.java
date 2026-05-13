package model.user;
import java.time.LocalDateTime;

import model.root.BaseEntity;

/**
 * Lớp trừu tượng đại diện cho người dùng trong hệ thống.
 * Các subclass: Account (người dùng thường), Admin (quản trị viên hệ thống).
 * Các subclass bắt buộc phải implement: updateProfile() và getDisplayInfo()
 */

public abstract class User extends BaseEntity{
    private String username;
    private String email;
    private String hashedPassword; // đã được hash bởi UserFactory trước khi lưu

    /**
     * Constructor dùng khi tạo mới (đăng ký).
     * Password phải đã được hash trước khi truyền vào đây.
     *
     * @param username       Đã validate bởi UserFactory
     * @param email          Đã validate bởi UserFactory
     * @param hashedPassword Đã hash bởi UserFactory
     */
    public User(String username, String email, String hashedPassword) {
        super();
        this.username = username;
        this.email = email;
        this.hashedPassword = hashedPassword; //nhận hash, không tự hash
    }

    /**
     * Constructor dùng khi load lại từ MySQL (id, timestamps đã có sẵn).
     */
    public User(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String username, String email, String hashedPassword) {
        super(id, createdAt, updatedAt);
        this.username = username;
        this.email    = email;
        this.hashedPassword = hashedPassword;
    }

    // ──────────────────────────────────────────────
    // GETTERS
    // ──────────────────────────────────────────────
    public String getUsername()       { return username; }
    public String getEmail()          { return email; }
    public String getHashedPassword() { return hashedPassword; }

    // ──────────────────────────────────────────────
    // SETTERS — package-private / chỉ Service được gọi
    // ──────────────────────────────────────────────

    /**
     * Chỉ UserService được phép đổi password qua changePassword().
     */
    public void setPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    // ──────────────────────────────────────────────
    // ABSTRACT
    // ──────────────────────────────────────────────
    public abstract String getDisplayInfo();
}

