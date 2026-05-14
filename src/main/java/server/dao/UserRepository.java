package server.dao;
import model.user.Account;
import model.user.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface UserRepository {

    // ── Thao tác đơn lẻ (tự mở connection) ──────────────────────────────

    /** Lưu User mới — gọi khi register. */
    boolean save(User user);

    /** Chỉ cập nhật số dư ví — tối ưu hơn update() toàn bộ.
     * Dùng cho deposit đơn lẻ*/
    boolean updateBalance(String userId, double newBalance);

    Optional<User> findByEmail(String email);
    Optional<User> findById(String id);

    // ── Overload nhận Connection — chỉ dùng trong transaction ────────────

    /**
     * Trừ tiền Buyer trong transaction của PaymentService.processPayment().
     * Dùng SQL: balance = balance - amount (không cần biết số dư hiện tại).
     * Throw InsufficientFundsException nếu số dư không đủ.
     * Caller chịu trách nhiệm commit/rollback.
     */
    void withdraw(Connection conn, String userId, double amount) throws SQLException;

    /**
     * Cộng tiền Seller trong transaction của PaymentService.processPayment().
     * Dùng SQL: balance = balance + amount (không cần biết số dư hiện tại).
     * Caller chịu trách nhiệm commit/rollback.
     */
    void deposit(Connection conn, String userId, double amount) throws SQLException;

    /**
     * Lấy balance mới ngay trong transaction sau khi thực hiện thanh toán
     */
    double getBalance(Connection conn, String userId) throws SQLException;
}