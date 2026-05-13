package exception;

/**
 * Ném ra khi số dư ví không đủ để thực hiện giao dịch.
 *
 * Là checked exception vì PaymentService PHẢI xử lý trường hợp này
 * (rollback transaction và trả thông báo về cho user).
 *
 * Không extends RuntimeException để compiler bắt buộc caller catch/declare.
 */
public class InsufficientFundsException extends Exception {

    private final String userId;
    private final double required;
    private final double actual;

    public InsufficientFundsException(String userId, double required, double actual) {
        super(String.format(
                "Số dư không đủ. User=%s | Cần=%.0f | Có=%.0f",
                userId, required, actual));
        this.userId   = userId;
        this.required = required;
        this.actual   = actual;
    }

    public String getUserId()  { return userId; }
    public double getRequired() { return required; }
    public double getActual()   { return actual; }
}
