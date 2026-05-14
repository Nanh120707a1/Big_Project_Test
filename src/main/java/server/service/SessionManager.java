package server.service;


import exception.auth.SessionExpiredException;
import exception.auth.SessionNotFoundException;
import exception.auth.UnauthorizedActionException;
import model.user.Account;
import model.user.Admin;
import model.user.User;

import java.util.*;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý phiên đăng nhập cho NHIỀU người dùng đồng thời.
 *
 * ╔══════════════════════════════════════════════════════╗
 * ║           THIẾT KẾ TOKEN-BASED SESSION               ║
 * ╠══════════════════════════════════════════════════════╣
 * ║ 1. Client gửi email + password → Server xác thực     ║
 * ║ 2. Server tạo token UUID ngẫu nhiên → trả về Client  ║
 * ║ 3. Mọi request tiếp theo: Client gửi kèm token       ║
 * ║ 4. Server tra cứu token → lấy User tương ứng O(1)    ║
 * ║ 5. Logout / hết hạn → xoá token khỏi map             ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Tương thích Socket:
 *   - Server tạo 1 thread per client connection.
 *   - Mỗi thread giữ token riêng (nhận từ client).
 *   - SessionManager.getInstance() dùng chung, thread-safe.
 *   - Controller gọi SessionManager.getUserByToken(token) để lấy User của request đang xử lý.
 *
 * Thread-safe: ConcurrentHashMap (không cần synchronized).
 * Singleton: Eager initialization — an toàn khi multi-thread.
 */
public class SessionManager {

    // ── Singleton ──
    private static final SessionManager INSTANCE = new SessionManager();
    public static SessionManager getInstance() { return INSTANCE; }         // Cần dùng class nay thì gọi SessionManager.getInstance()

    /** Thời gian phiên tồn tại tối đa (phút). Mặc định 360 phút */
    private static final long SESSION_TIMEOUT_MINUTES = 360;

    /** Lưu toàn bộ phiên đang hoạt động: token → SessionEntry */
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /** Daemon thread tự dọn phiên hết hạn mỗi 15 phút */
    private final ScheduledExecutorService cleaner =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SessionCleaner");
                t.setDaemon(true); // setDaemon quyết định luồng đó có “sống vì hệ thống” hay không, khi tất cả UserThread kết thúc -> Daemon thread bị kill ngay lập tức, ko cần chạy xong
                return t; // trả về thread cho executor để chạy task
            });

    private SessionManager() {
        cleaner.scheduleAtFixedRate(                    //lên lịch chạy task định kì
                this::removeExpiredSessions,            // () -> removeExpiredSessions()
                15, 15, TimeUnit.MINUTES);       // delay 15 phút đầu rồi chạy, sau đó lặp 15 phút chạy 1 lần
    }

    // ══════════════════════════════════════════════════════════════
    //  DỮ LIỆU MỘT PHIÊN
    // ══════════════════════════════════════════════════════════════
    /**
     * Thông tin một phiên đăng nhập.
     * lastActive cập nhật mỗi lần getUserByToken() được gọi thành công
     * → phiên không hết hạn khi người dùng đang tích cực sử dụng.
     */
    public static class SessionEntry {
        private final String token;
        private final User user;
        private final LocalDateTime createdAt;
        private volatile LocalDateTime lastActive;
        SessionEntry(String token, User user) {
            this.token      = token;
            this.user       = user;
            this.createdAt  = LocalDateTime.now();
            this.lastActive = LocalDateTime.now();
        }

        public String        getToken()      { return token; }
        public User          getUser()       { return user; }
        public LocalDateTime getCreatedAt()  { return createdAt; }
        public LocalDateTime getLastActive() { return lastActive; }

        void touch() { this.lastActive = LocalDateTime.now(); }  // touch trong có nghĩa là "chạm vào" một file để cập nhật timestamp mà không thay đổi nội dung.

        boolean isExpired() {
            return lastActive.plusMinutes(SESSION_TIMEOUT_MINUTES)
                    .isBefore(LocalDateTime.now());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  TẠO PHIÊN (gọi sau khi xác thực thành công)
    // ══════════════════════════════════════════════════════════════

    /**
     * Tạo phiên mới cho user và trả về token.
     *
     * Nếu user đã có phiên cũ (ví dụ login từ thiết bị khác)
     * → phiên cũ bị thu hồi, tránh nhiều token cùng tồn tại.
     *
     * @param user User vừa xác thực thành công
     * @return sessionToken — UUID ngẫu nhiên, client phải lưu lại
     */
    public String startSession(User user) {
        // Thu hồi phiên cũ nếu còn tồn tại
        invalidateByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(token, user));
        System.out.printf("[Phiên] Tạo phiên — User: %s | Token: %.8s...%n",
                user.getUsername(), token);
        return token;
    }

    // ══════════════════════════════════════════════════════════════
    //  KẾT THÚC PHIÊN (logout)
    // ══════════════════════════════════════════════════════════════
    /**
     * Huỷ phiên theo token (khi người dùng logout).
     *
     * @param token Token cần huỷ
     * @return true nếu token tồn tại và đã bị xoá
     */
    public boolean clearSession(String token) {
        SessionEntry entry = sessions.remove(token);
        if (entry != null) {
            System.out.println("[Phiên] Đã kết thúc phiên của: "
                    + entry.getUser().getUsername());
            return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  TRA CỨU USER THEO TOKEN  (gọi trong mỗi request)
    // ══════════════════════════════════════════════════════════════

    /**
     * Helper nội bộ --> validate token và trả về User, không dùng trực tiếp bên ngoài SessionManager
     * Lấy User theo token. Khi User gửi request, server sẽ gọi đến cái này
     * Cập nhật lastActive → phiên không hết hạn khi đang dùng.
     *
     * @param token Token do client gửi lên trong mỗi request
     * @return Optional<User> — rỗng nếu token không hợp lệ / đã hết hạn
     */
    private Optional<User> getUserByToken(String token) {
        if (token == null || token.isBlank()) {throw new SessionNotFoundException("Tài khoản chưa đăng nhập");};      // Client ko gửi token lên (Chưa login/bug Frontend)
        SessionEntry entry = sessions.get(token);
        if (entry == null) {throw new SessionNotFoundException();}            // Có gửi token nhưng ko tồn tại trong sessions map -> Server start/ đã logout
        if (entry.isExpired()) {
            sessions.remove(token);
            System.out.println("[Phiên] Token hết hạn: " + token.substring(0, 8) + "...");
            throw new SessionExpiredException(token);
        }
        entry.touch(); // Gia hạn sliding window
        return Optional.of(entry.getUser());
    }

    /**
     * Lấy Account từ token — dùng trong mọi Controller của Account.
     * Throw rõ ràng nếu token không hợp lệ hoặc user không phải Account.
     */
    public Account requireAccount(String token) {
        User user = getUserByToken(token)
                .orElseThrow(SessionNotFoundException::new);
        if (!(user instanceof Account account))
            throw new UnauthorizedActionException("Chức năng này chỉ dành cho Account.");
        return account;
    }

    /**
     * Lấy Admin từ token — dùng trong mọi Controller của Admin.
     * Throw rõ ràng nếu token không hợp lệ hoặc user không phải Admin.
     */
    public Admin requireAdmin(String token) {
        User user = getUserByToken(token)
                .orElseThrow(SessionNotFoundException::new);
        if (!(user instanceof Admin admin))
            throw new UnauthorizedActionException("Chức năng này chỉ dành cho Admin.");
        return admin;
    }
    // ══════════════════════════════════════════════════════════════
    //  TRA CỨU ACCOUNT THEO USER ID
    // ══════════════════════════════════════════════════════════════

    /**
     * Tìm Account đang online theo userId — không cần token.
     *
     * Dùng cho UserService.deposit(sellerId) và UserService.withdraw(buyerId):
     * PaymentService cần cập nhật RAM của Seller/Buyer sau khi DB đã commit,
     * nhưng không có token của họ — chỉ có userId từ Auction.
     *
     * Trả về Optional.empty() nếu user không online hoặc không phải Account —
     * caller xử lý bình thường, không cần throw.
     *
     * Không touch lastActive — đây không phải request của user,
     * không nên gia hạn session của họ.
     *
     * @param userId ID của user cần tìm
     * @return Optional<Account> — rỗng nếu không online hoặc đã hết hạn
     */
    public Optional<Account> findAccountById(String userId) {
        return sessions.values().stream()
                .filter(e -> !e.isExpired())
                .filter(e -> e.getUser().getId().equals(userId))
                .filter(e -> e.getUser() instanceof Account)
                .map(e -> (Account) e.getUser())
                .findFirst();
    }

    // ══════════════════════════════════════════════════════════════
    //  KIỂM TRA NHANH
    // ══════════════════════════════════════════════════════════════

    /**
     * Kiểm tra token có hợp lệ và chưa hết hạn không.
     * Không touch lastActive — dùng cho health-check, không phải request thực.
     */
    public boolean isLoggedIn(String token) {
        if (token == null) return false;
        SessionEntry entry = sessions.get(token);
        if (entry == null) return false;
        if (entry.isExpired()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════

    /** Thu hồi phiên cũ của userId — tránh 1 user có nhiều token */
    private void invalidateByUserId(String userId) {
        boolean removed = sessions.entrySet()
                .removeIf(e -> e.getValue().getUser().getId().equals(userId));
        if (removed)
            System.out.println("[Phiên] Huỷ phiên cũ của userId=" + userId);
    }

    /** Daemon task: dọn phiên hết hạn định kỳ */
    private void removeExpiredSessions() {
        long removed = sessions.values().stream()
                .filter(SessionEntry::isExpired)       // lọc những cái hết hạn
                .map(e -> sessions.remove(e.getToken())) // xóa từng cái, trả về cái vừa xóa
                .filter(Objects::nonNull)              // đảm bảo xóa thành công (không null)
                .count();                              // đếm số cái đã xóa thực tế
        if (removed > 0)
            System.out.println("[Phiên] Đã dọn " + removed
                    + " phiên hết hạn. Còn lại: " + sessions.size());
    }
}