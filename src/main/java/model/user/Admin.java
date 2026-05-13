package model.user;

import exception.auth.UnauthorizedActionException;

import java.time.LocalDateTime;
/**
 * Tài khoản quản trị viên hệ thống.
 * Extends User (kế thừa id, timestamps, email, wallet, ...).
 * Admin KHÔNG implement Bidder hay Seller – admin chỉ quản lý, không mua bán.
 *
 * Các quyền riêng của Admin:
 *  - Duyệt / từ chối sản phẩm (checkItem)
 *  - Khóa tài khoản người dùng (banAccount) (Xem xét)
 */
public class Admin extends User {
    // ──────────────────────────────────────────────
    // CONSTRUCTOR 1 — tạo mới (UserFactory gọi)
    // ──────────────────────────────────────────────
    public Admin(String username, String email, String hashedPassword) {
        super(username, email, hashedPassword);
    }

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 2 — load từ DB (Repository gọi)
    // ──────────────────────────────────────────────
    public Admin(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String username, String email, String hashedPassword) {
        super(id, createdAt, updatedAt, username, email, hashedPassword);
    }

    // ──────────────────────────────────────────────
    // OVERRIDE User
    // ──────────────────────────────────────────────
    @Override
    public String getDisplayInfo() {
        return String.format("Admin[%s | %s | %s]",
                getId(), getUsername(), getEmail());
    }
}

