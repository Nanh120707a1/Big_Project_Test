package server.dao;
import exception.user.InsufficientFundsException;
import model.payment.Wallet;
import model.user.Account;
import model.user.Admin;
import model.user.Role;
import model.user.User;
import server.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Các method đơn lẻ tự mở và đóng Connection bên trong (try-with-resources).
 * Ngoại lệ duy nhất: updateBalance(Connection, ...) nhận conn từ ngoài
 * để tham gia transaction của AuctionService.pay().
 */
public class MySQLUserRepository implements UserRepository {

    // =====================================================================
    // SAVE — tạo mới User khi register
    // =====================================================================

    @Override
    public boolean save(User user) {
        String sql = """
                INSERT INTO users
                    (id, user_type, username, email, hashed_password, balance, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user instanceof Account ? "ACCOUNT" : "ADMIN");
            ps.setString(3, user.getUsername());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getHashedPassword());
            ps.setDouble(6, user instanceof Account a ? a.getWallet().getBalance() : 0.0);
            ps.setTimestamp(7, Timestamp.valueOf(user.getCreatedAt()));
            ps.setTimestamp(8, Timestamp.valueOf(user.getUpdatedAt()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserRepo] save thất bại: " + user.getId());
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================================
    // UPDATE BALANCE (đơn lẻ) — gọi khi UserService.deposit(token, amount)
    // Set thẳng giá trị mới — caller đã tính sẵn số dư mới.
    // =====================================================================

    @Override
    public boolean updateBalance(String userId, double newBalance) {
        String sql = "UPDATE users SET balance = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1,    newBalance);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[UserRepo] updateBalance thất bại: " + userId);
            e.printStackTrace();
            return false;
        }
    }
    // =====================================================================
    // WITHDRAW (transaction) — gọi bởi PaymentService.processPayment()
    //
    // Dùng "balance = balance - ?" thay vì set giá trị tuyệt đối:
    //   - Không cần query số dư hiện tại trước khi trừ
    //   - Tránh race condition nếu có 2 transaction chạy song song
    //
    // Kiểm tra số dư đủ bằng WHERE balance >= ? —
    // nếu 0 row bị ảnh hưởng → số dư không đủ → throw InsufficientFundsException.
    // =====================================================================

    @Override
    public void withdraw(Connection conn, String userId, double amount) throws SQLException {
        String sql = """
                UPDATE users
                SET balance = balance - ?, updated_at = ?
                WHERE id = ? AND balance >= ?               
                """;
        // WHERE id = ? AND balance >= ? --> check số dư >= amount trong Database
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1,    amount);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    userId);
            ps.setDouble(4,    amount);

            int rows = ps.executeUpdate();
            if (rows == 0)
                throw new InsufficientFundsException(
                        "Số dư không đủ để thanh toán. Cần: " + amount);
        }
    }

    // =====================================================================
    // DEPOSIT (transaction) — gọi bởi PaymentService.processPayment()
    //
    // Dùng "balance = balance + ?" — không cần biết số dư hiện tại.
    // =====================================================================

    @Override
    public void deposit(Connection conn, String userId, double amount) throws SQLException {
        String sql = """
                UPDATE users
                SET balance = balance + ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1,    amount);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    userId);
            ps.executeUpdate();
        }
    }
    // ==============================================================================================================
    // GET BALANCE (transaction) — gọi bởi PaymentService.processPayment() --> lấy balance mới ngay trong transaction
    // ==============================================================================================================
    public double getBalance(Connection conn, String userId) throws SQLException {
        String sql = "SELECT balance FROM users WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
                throw new SQLException("User không tồn tại: " + userId);
            }
        }
    }


    // =====================================================================
    // FIND BY EMAIL — dùng khi login
    // =====================================================================

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[UserRepo] findByEmail thất bại: " + email);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // =====================================================================
    // FIND BY ID — dùng khi load profile
    // =====================================================================

    @Override
    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[UserRepo] findById thất bại: " + id);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // =====================================================================
    // PRIVATE HELPER — map ResultSet → User
    // =====================================================================

    private User mapRow(ResultSet rs) throws SQLException {
        String        id          = rs.getString("id");
        String        userType    = rs.getString("user_type");
        String        username    = rs.getString("username");
        String        email       = rs.getString("email");
        String        hashedPw    = rs.getString("hashed_password");
        double        balance     = rs.getDouble("balance");
        LocalDateTime createdAt   = rs.getTimestamp("created_at").toLocalDateTime();
        Timestamp     updatedAtTs = rs.getTimestamp("updated_at");
        LocalDateTime updatedAt   = updatedAtTs != null
                ? updatedAtTs.toLocalDateTime() : createdAt;

        if ("ACCOUNT".equals(userType)) {
            return new Account(id, createdAt, updatedAt,
                    username, email, hashedPw,
                    new Wallet(balance),
                    EnumSet.of(Role.BIDDER, Role.SELLER));
        }
        return new Admin(id, createdAt, updatedAt, username, email, hashedPw);
    }
}