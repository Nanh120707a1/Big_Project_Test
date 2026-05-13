package exception.root;

/**
 * Lớp exception gốc của toàn bộ hệ thống đấu giá.
 * Thay vì ném nhiều loại lỗi rời rạc (IllegalArgumentException, NullPointerException, IllegalStateException...),
 * Gom tất cả về một “họ lỗi” chung là AppException.
 * Throwable
 *    └── Exception
 *         └── RuntimeException
 *              └── AppException
 *
 * Mọi custom exception đều kế thừa từ đây — cho phép Controller
 * catch một lần duy nhất bằng: catch (AppException e)
 *
 * errorCode: mã lỗi định danh duy nhất, dùng để:
 *   - Map sang HTTP status khi chuyển sang REST
 *   - Hiển thị thông báo lỗi cụ thể trên JavaFX UI
 *   - Ghi log phân loại
 */
public class AppException extends RuntimeException {

    private final String errorCode; //errorCode -> mã lỗi chuẩn hoá để máy xử lý

    // Constructor 1: Dùng khi chỉ cần mã lỗi + nội dung lỗi.
    public AppException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // Constructor 2: Dùng khi lỗi này sinh ra từ lỗi khác.
    public AppException(String errorCode, String message, Throwable cause) {
        super(message, cause);  // Cause giúp truy vết nguyên nhân gây ra lỗi
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "[" + errorCode + "] " + getMessage();
    }
}