package server.dao;

import exception.user.InsufficientFundsException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * WalletRepository — đọc/ghi số dư ví người dùng.
 *
 * Schema bảng wallets (hoặc accounts tuỳ project):
 *   user_id VARCHAR PK
 *   balance DECIMAL(15,2) NOT NULL DEFAULT 0
 *   updated_at DATETIME
 *
 * Quy ước transaction giống AuctionRepository:
 *  - Method có Connection tham số → caller (PaymentService) quản lý transaction.
 *  - Repository chỉ execute SQL, KHÔNG commit/rollback/close connection đó.
 *
 * Cả withdraw() và deposit() đều nhận Connection để PaymentService
 * có thể gộp 3 bước (withdraw + deposit + updateStatus) vào 1 transaction duy nhất.
 */
public interface WalletRepository {

    /**
     * Lấy số dư hiện tại của user.
     * Tự mở connection — dùng để hiển thị số dư trên UI.
     *
     * @param userId ID người dùng
     * @return số dư hiện tại, hoặc -1 nếu user không tồn tại
     */
    double getBalance(String userId) throws SQLException;

    /**
     * Trừ tiền khỏi ví — chạy trong transaction của PaymentService.
     *
     * Dùng SELECT ... FOR UPDATE để lock row, tránh race condition
     * khi user thực hiện nhiều thanh toán cùng lúc.
     *
     * @param conn   Connection do PaymentService tạo và quản lý
     * @param userId ID người dùng
     * @param amount Số tiền cần trừ (phải > 0)
     * @throws InsufficientFundsException nếu số dư < amount
     * @throws SQLException               nếu lỗi DB
     */
    void withdraw(Connection conn, String userId, double amount)
            throws InsufficientFundsException, SQLException;

    /**
     * Cộng tiền vào ví — chạy trong transaction của PaymentService.
     *
     * @param conn   Connection do PaymentService tạo và quản lý
     * @param userId ID người dùng
     * @param amount Số tiền cần cộng (phải > 0)
     * @throws SQLException nếu lỗi DB
     */
    void deposit(Connection conn, String userId, double amount)
            throws SQLException;
}