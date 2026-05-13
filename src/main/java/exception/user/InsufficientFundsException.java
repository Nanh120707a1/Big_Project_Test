package exception.user;

/**
 * Ném khi số dư ví không đủ để thực hiện giao dịch.
 *
 * Dùng tại:
 *   - Account.placeBid()    → hasSufficientFunds(amount) == false
 *   - Account.setAutoBid()  → hasSufficientFunds(maxBid) == false
 *   - Account.pay()         → hasSufficientFunds(amount) == false
 *   - Wallet.withdraw()     → amount > balance
 *
 * Ví dụ sử dụng:
 *   throw new InsufficientFundsException(wallet.getBalance(), requiredAmount);
 */
public class InsufficientFundsException extends UserException {

    private static final String CODE = "USER_INSUFFICIENT_FUNDS";

    /**
     * @param currentBalance Số dư hiện tại của ví
     * @param required       Số tiền cần có
     */
    public InsufficientFundsException(double currentBalance, double required) {
        super(CODE, String.format(
                "Số dư ví không đủ. Hiện có: %.2f — Cần: %.2f.",
                currentBalance, required));
    }

    public InsufficientFundsException(String message) {
        super(CODE, message);
    }
}
