package server.service;

import exception.auth.InvalidCredentialException;
import exception.user.DuplicateEmailException;
import exception.user.UserNotFoundException;
import model.factory.UserFactory;
import model.user.Account;
import model.user.User;
import model.util.PasswordUtil;
import model.util.UserValidator;
import server.config.DatabaseConfig;
import server.dao.UserRepository;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * UserService — business logic cho quản lý người dùng.
 *
 * Các method đơn lẻ (register, login, logout, deposit) gọi thẳng Repository —
 * Repository tự mở và đóng Connection bên trong.
 *
 * deposit(token):Dùng cho user tự nạp tiền qua UI — cập nhật cả RAM lẫn DB.
 *
 * deposit(userId, amount) / withdraw(userId, amount):
 *   Dùng cho PaymentService.processPayment() gọi SAU KHI DB đã commit —
 *   chỉ cập nhật RAM (Wallet object trong SessionManager).
 *   Nếu user không online (không có session) thì bỏ qua — DB đã đúng rồi,
 *   lần login sau sẽ load số dư mới từ DB.
 *
 * Luồng:
 *   register() → validate → Factory tạo Account → save DB → startSession → return LoginResult
 *   login()    → validate → repo.findByEmail() → verifyPassword → startSession → return LoginResult
 *   logout()   → clearSession
 */
public class UserService {
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    public UserService(UserRepository userRepository,
                       SessionManager sessionManager) {
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
    }
    // ──────────────────────────────────────────────────────────────
    // REGISTER
    // ──────────────────────────────────────────────────────────────

    /**
     * Đăng ký tài khoản mới.
     * * Repository tự mở Connection khi save().
     * Sau khi thành công, tạo session luôn — không cần login lại.
     *
     * @return LoginResult chứa token và Account vừa tạo
     */
    public LoginResult register(String username, String email,
                                String rawPassword, String confirmPassword) {
        UserValidator.validateUsername(username);
        UserValidator.validateEmail(email);
        UserValidator.validatePassword(rawPassword);
        UserValidator.validateConfirmPassword(rawPassword, confirmPassword);

        if (userRepository.findByEmail(email.trim()).isPresent())
            throw new DuplicateEmailException(email.trim());

        Account newAccount = UserFactory.createAccount(
                username.trim(), email.trim(), rawPassword);

        boolean saved = userRepository.save(newAccount);
        if (!saved)
            throw new RuntimeException("Không thể lưu tài khoản vào database.");

        String token = sessionManager.startSession(newAccount);
        System.out.println("[UserService] Đăng ký thành công: " + newAccount.getUsername());
        return new LoginResult(token, newAccount);
    }

    // ──────────────────────────────────────────────────────────────
    // LOGIN
    // ──────────────────────────────────────────────────────────────

    /**
     * Đăng nhập.
     * Repository tự mở Connection khi findByEmail().
     *
     * @return LoginResult(token, user)
     */
    public LoginResult login(String enteredEmail, String enteredPassword) {
        UserValidator.validateEmail(enteredEmail);
        UserValidator.validatePassword(enteredPassword);

        User user = userRepository.findByEmail(enteredEmail.trim())
                .orElseThrow(() -> new UserNotFoundException("email", enteredEmail));

        if (!PasswordUtil.verifyPassword(enteredPassword, user.getHashedPassword()))
            throw new InvalidCredentialException();

        String token = sessionManager.startSession(user);
        System.out.println("[UserService] Đăng nhập thành công: " + user.getUsername());
        return new LoginResult(token, user);
    }

    // ──────────────────────────────────────────────────────────────
    // LOGOUT
    // ──────────────────────────────────────────────────────────────
    /**
     * Đăng xuất — chỉ xóa session trong RAM, không cần DB.
     */
    public void logout(String token) {
        sessionManager.clearSession(token);
        System.out.println("[UserService] Đăng xuất thành công.");
    }

    // ──────────────────────────────────────────────────────────────
    // DEPOSIT — nạp tiền vào ví (nạp thông thường, tự Account nạp)
    // ──────────────────────────────────────────────────────────────

    /**
     * User tự nạp tiền qua UI.
     * Cập nhật cả RAM (Wallet) lẫn DB (balance).
     * Repository tự mở Connection khi updateBalance().
     *
     * Nếu DB thất bại → rollback lại RAM để giữ nhất quán.
     *
     * @param token  Session token của Account
     * @param amount Số tiền nạp (phải > 0)
     */
    public void depositForSelf(String token, double amount) {
        Account account = sessionManager.requireAccount(token);
        if (amount <= 0)
            throw new IllegalArgumentException("Số tiền nạp phải > 0, nhận được: " + amount);

        account.getWallet().deposit(amount);

        boolean updated = userRepository.updateBalance(
                account.getId(), account.getWallet().getBalance());

        if (!updated) {
            account.getWallet().withdraw(amount);   // rollback RAM
            throw new RuntimeException("Cập nhật số dư thất bại, giao dịch đã hủy.");
        }
        System.out.printf("[UserService] %s nạp %.2f → Số dư: %.2f%n",
                account.getUsername(), amount, account.getWallet().getBalance());
    }

    // ──────────────────────────────────────────────────────────────
    // PHỤC VỤ ProcessPayment (trong xử lí transaction)
    // ──────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────
    // DEPOSIT (RAM) — PaymentService gọi sau khi DB đã commit
    // ──────────────────────────────────────────────────────────────

    /**
     * Cộng tiền vào Wallet của Seller trong RAM sau khi PaymentService đã commit DB.
     *
     * Chỉ cập nhật RAM — DB đã được WalletRepository.deposit() xử lý trong transaction.
     * Nếu Seller không online (không có session) thì bỏ qua —
     * lần login sau sẽ load số dư mới từ DB.
     *
     * Gọi bởi: PaymentService.processPayment() sau conn.commit()
     *
     * @param sellerId ID của Seller
     * @param amount   Số tiền cộng vào (currentPrice của phiên)
     */
    public void deposit(String sellerId, double amount) {
        sessionManager.findAccountById(sellerId).ifPresent(seller -> {
            seller.getWallet().deposit(amount);
            System.out.printf("[UserService] [RAM] %s +%.2f → Số dư: %.2f%n",
                    seller.getUsername(), amount, seller.getWallet().getBalance());
        });
    }

    // ──────────────────────────────────────────────────────────────
    // WITHDRAW (RAM) — PaymentService gọi sau khi DB đã commit
    // ──────────────────────────────────────────────────────────────

    /**
     * Trừ tiền khỏi Wallet của Buyer trong RAM sau khi PaymentService đã commit DB.
     *
     * Chỉ cập nhật RAM — DB đã được WalletRepository.withdraw() xử lý trong transaction.
     * Nếu Buyer không online (không có session) thì bỏ qua —
     * lần login sau sẽ load số dư mới từ DB.
     *
     * Gọi bởi: PaymentService.processPayment() sau conn.commit()
     *
     * @param buyerId ID của Buyer
     * @param amount  Số tiền trừ đi (currentPrice của phiên)
     */
    public void withdraw(String buyerId, double amount) {
        sessionManager.findAccountById(buyerId).ifPresent(buyer -> {
            buyer.getWallet().withdraw(amount);
            System.out.printf("[UserService] [RAM] %s -%.2f → Số dư: %.2f%n",
                    buyer.getUsername(), amount, buyer.getWallet().getBalance());
        });
    }

    // ──────────────────────────────────────────────────────────────
    // QUERY
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy Account đang login — lấy từ SessionManager (RAM), không query DB.
     * Dùng khi Controller cần hiển thị profile.
     */
    public Account getLoggedInAccount(String token) {
        return sessionManager.requireAccount(token);
    }

    public boolean isSessionValid(String token) {
        return sessionManager.isLoggedIn(token);
    }

    // ──────────────────────────────────────────────────────────────
    // RESULT RECORD
    // ──────────────────────────────────────────────────────────────

    /**
     * Kết quả login / register.
     * Controller lưu token để dùng cho mọi request tiếp theo.
     * User dùng để hiển thị thông tin lên UI ngay mà không cần query thêm.
     */
    public record LoginResult(String token, User user) {
        public boolean isSuccess() { return token != null && user != null; }
    }
}

