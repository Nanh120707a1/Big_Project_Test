package model.user;

import exception.auth.UnauthorizedActionException;
import model.payment.Wallet;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Tài khoản người dùng thường — vừa là Bidder vừa là Seller.
 *
 * Thay đổi so với phiên bản cũ:
 *  - ĐÃ BỎ: Map<String, Item> inventory → chuyển sang InventoryCache (server/cache)
 *  - ĐÃ BỎ: validate business logic trong placeBid(), submitItem() → chuyển về Service
 *  - Account chỉ giữ state (wallet, roles) và phương thức pay() đơn giản
 *
 * Mọi business logic của Bidder → AuctionService
 * Mọi business logic của Seller → ItemService
 */
public class Account extends User {

    private final Set<Role> roles;
    private final Wallet wallet;

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 1 — đăng ký lần đầu (UserFactory gọi)
    // ──────────────────────────────────────────────
    public Account(String username, String email, String hashedPassword) {
        super(username, email, hashedPassword);
        this.roles = EnumSet.of(Role.BIDDER, Role.SELLER);
        this.wallet = new Wallet();
    }

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 2 — load từ DB (Repository gọi)
    // ──────────────────────────────────────────────
    public Account(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                   String username, String email, String hashedPassword,
                   Wallet wallet, Set<Role> roles) {
        super(id, createdAt, updatedAt, username, email, hashedPassword);
        this.roles = roles;
        this.wallet = wallet;
    }

    // ──────────────────────────────────────────────
    // GETTERS
    // ──────────────────────────────────────────────

    public Wallet getWallet() {
        return wallet;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    // ──────────────────────────────────────────────
    // OVERRIDE User
    // ──────────────────────────────────────────────
    @Override
    public String getDisplayInfo() {
        return String.format("Account[%s | %s | %s | Số dư: %.2f]",
                getId(), getUsername(), getEmail(), wallet.getBalance());
    }
}