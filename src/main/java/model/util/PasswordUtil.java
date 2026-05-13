package model.util;


import org.mindrot.jbcrypt.BCrypt;

/**
 * UTILITY CLASS -> ko cần khai báo, gọi trực tiếp
 */

public class PasswordUtil {
    private static final int COST = 12;

    /**
     * Hash password trước khi lưu xuống DB.
     * Mỗi lần gọi trả về kết quả khác nhau dù cùng rawPassword — đây là đúng,
     * vì BCrypt tự thêm salt ngẫu nhiên vào bên trong.
     */
    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /**
     * Kiểm tra password khi login.
     * KHÔNG hash rawPassword rồi so sánh chuỗi — dùng method này.
     */
    public static boolean verifyPassword(String rawPassword, String hashedPassword) {
        return BCrypt.checkpw(rawPassword, hashedPassword);
    }
}

