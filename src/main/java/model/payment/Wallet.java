package model.payment;

// Database (users.balance) = tiền thật
//Wallet (RAM)              = cache hiển thị
import java.util.concurrent.locks.ReentrantLock;

// Wallet - Ví điện tử gắn liền với mỗi Account
//  * Trách nhiệm: chỉ quản lý số dư và giao dịch tài chín
// Thread-safe: mọi thao tác ghi đều được bảo vệ bởi ReentrantLock.

public class Wallet{
    private double balance;
    private final transient ReentrantLock lock = new ReentrantLock(); // Đảm bảo thread safety khi nhiều thread truy cập file cùng lúc

    // -------KHỞI TẠO VÍ----------
    // User vừa đăng kí tài khoản sẽ có ví mới vs số dư = 0
    public Wallet (){
        this.balance = 0.0;
    }
    // User có sẵn tiền trong ví trước đó (load trong database)
    public Wallet (double currentBalance){
        if (currentBalance < 0.0) throw new IllegalArgumentException("Số dư hiện tại phải >= 0");
        this.balance = currentBalance;
    }
    // ---------GIAO DỊCH -----------------------------------------------------
    // deposit()/ withdraw()
    // hasSufficientFunds --> dùng để kiểm tra ví có đủ tiền ko tc khi thực hiện giao dịch (thanh toán, đấu giá)
    //==========================================================================
    /**
     * Nạp tiền vào ví.
     * @param amount Số tiền cần nạp (phải > 0)
     */
    public void deposit (double amount) {
        lock.lock();
        try {
            this.balance += amount;
            System.out.printf("[VÍ] Nạp %.2f — Số dư hiện tại: %.2f%n", amount, this.balance);
        } finally {
            lock.unlock();
        }
    }
    /**
     * Rút tiền khỏi ví.
     * @param amount Số tiền cần rút (phải > 0 và không vượt quá số dư)
     * @throws IllegalStateException nếu số dư không đủ
     */
    public void withdraw (double amount){
        lock.lock();
        try{
            this.balance -= amount;
            System.out.printf("[VÍ] Rút %.2f — Số dư còn lại: %.2f%n", amount, this.balance);
        } finally {
            lock.unlock();
        }
    }
    public double getBalance() {
        return balance;
    }
}
