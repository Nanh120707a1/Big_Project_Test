package exception.auth;

/**
 * Ném khi user đã đăng nhập nhưng KHÔNG CÓ QUYỀN thực hiện hành động.
 *
 * Khác biệt:
 *   - SessionNotFoundException / SessionExpiredException → chưa / hết login (401)
 *   - UnauthorizedActionException                        → đã login, thiếu quyền (403)
 *
 * Các trường hợp dùng:
 *   - Admin cố gắng placeBid / submitItem (Admin không phải Bidder/Seller)
 *   - Account không có Role.BIDDER cố gắng đặt giá
 *   - Account cố sửa/xoá item của người khác (dùng ItemOwnershipException thay thế)
 *
 * Ví dụ sử dụng:
 *   throw new UnauthorizedActionException("Tài khoản Admin không thể đặt giá.");
 *   throw new UnauthorizedActionException(Role.BIDDER, "placeBid");
 */
public class UnauthorizedActionException extends AuthException {

    private static final String CODE = "AUTH_UNAUTHORIZED_ACTION";

    public UnauthorizedActionException(String message) {
        super(CODE, message);
    }

    /**
     * Constructor tiện dụng: tự tạo message khi thiếu Role cụ thể.
     *
     * @param requiredRole Tên role cần có (ví dụ: "BIDDER", "SELLER")
     * @param action       Hành động bị từ chối (ví dụ: "placeBid", "submitItem")
     */
    public UnauthorizedActionException(String requiredRole, String action) {
        super(CODE, "Tài khoản không có quyền [" + requiredRole + "] để thực hiện: " + action + ".");
    }
}
