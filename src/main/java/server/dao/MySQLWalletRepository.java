package server.dao;

import exception.user.InsufficientFundsException;
import server.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * MySQLWalletRepository — implementation của WalletRepository.
 *
 * Điểm quan trọng trong withdraw():
 *  - Dùng SELECT balance FROM wallets WHERE user_id = ? FOR UPDATE
 *    để lock row trong suốt transaction, tránh race condition khi
 *    user thanh toán nhiều phiên cùng lúc (double-spend).
 *  - Kiểm tra số dư SAU KHI lock, không phải trước — tránh TOCTOU bug.
 *
 * Cả withdraw() và deposit() đều KHÔNG commit — PaymentService commit toàn bộ.
 */
public class MySQLWalletRepository implements WalletRepository {

    @Override
    public double getBalance(String userId) throws SQLException {
        String sql = "SELECT balance FROM wallets WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
            return -1; // user không tồn tại
        }
    }

    /**
     * Trừ tiền — luôn chạy trong transaction của PaymentService.
     *
     * Flow:
     *  1. SELECT FOR UPDATE  → lock row, đọc balance mới nhất
     *  2. Kiểm tra đủ tiền   → throw InsufficientFundsException nếu thiếu
     *  3. UPDATE balance      → trừ tiền
     *
     * Caller (PaymentService) sẽ catch InsufficientFundsException và rollback.
     */
    @Override
    public void withdraw(Connection conn, String userId, double amount)
            throws InsufficientFundsException, SQLException {

        // Bước 1: Lock row và đọc balance mới nhất trong transaction
        String selectSql = "SELECT balance FROM wallets WHERE user_id = ? FOR UPDATE";
        double currentBalance;
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Ví không tồn tại cho user: " + userId);
            }
            currentBalance = rs.getDouble("balance");
        }

        // Bước 2: Kiểm tra số dư SAU KHI lock
        if (currentBalance < amount) {
            throw new InsufficientFundsException(currentBalance, amount);
        }

        // Bước 3: Trừ tiền
        String updateSql = """
                UPDATE wallets
                SET balance = balance - ?, updated_at = ?
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setDouble(1,    amount);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Không thể trừ tiền cho user: " + userId);
            }
        }

        System.out.printf("[WalletRepo] withdraw: user=%s | trừ=%.0f | còn lại=%.0f%n",
                userId, amount, currentBalance - amount);
    }

    /**
     * Cộng tiền — luôn chạy trong transaction của PaymentService.
     * Không cần FOR UPDATE vì chỉ cộng thêm, không đọc trước để so sánh.
     */
    @Override
    public void deposit(Connection conn, String userId, double amount) throws SQLException {
        String sql = """
                UPDATE wallets
                SET balance = balance + ?, updated_at = ?
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1,    amount);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3,    userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Không thể cộng tiền cho user: " + userId);
            }
        }

        System.out.printf("[WalletRepo] deposit: user=%s | cộng=%.0f%n", userId, amount);
    }
}