package server.dao;
import model.user.Account;
import model.user.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
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

    // ── Overload nhận Connection — dùng trong transaction ────────────────

    /**
     * Dùng trong transaction thanh toán:
     *   updateBalance(conn, bidderId, bidderNewBalance) ← trừ tiền Bidder
     *   updateBalance(conn, sellerId, sellerNewBalance) ← cộng tiền Seller
     *   cùng 1 transaction với updateStatus Auction + PaymentObligation
     */
    boolean updateBalance(Connection conn, String userId, double newBalance) throws SQLException;
}