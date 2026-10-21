package com.pbl4.app.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PasswordHelper: Hỗ trợ băm (hash) và xác thực mật khẩu bằng SHA-256 + Salt.
 * Đảm bảo mật khẩu KHÔNG BAO GIỜ lưu plain-text vào cơ sở dữ liệu.
 * SHA-256 output = 64 ký tự hex — dùng để nhận diện mật khẩu đã băm.
 */
public class PasswordHelper {

    /**
     * Salt tĩnh cố định của ứng dụng. Nên dùng per-user salt nếu nâng cấp sau này.
     */
    private static final String APP_SALT = "PBL4_SECURE_SALT_2026";

    private PasswordHelper() {
        // Utility class
    }

    /**
     * Băm mật khẩu bằng SHA-256 kết hợp salt ứng dụng.
     *
     * @param plainPassword Mật khẩu gốc do người dùng nhập
     * @return Chuỗi hex SHA-256 dài 64 ký tự, hoặc null nếu lỗi
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return null;
        }
        try {
            String salted = plainPassword + APP_SALT;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(salted.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Lỗi SHA-256 không tồn tại: " + e.getMessage());
            return null;
        }
    }

    /**
     * Xác thực mật khẩu người dùng nhập với hash đang lưu trong CSDL.
     *
     * @param plainPassword  Mật khẩu gốc người dùng nhập
     * @param hashedPassword Hash SHA-256 lưu trong DB
     * @return true nếu mật khẩu khớp
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        String hashed = hashPassword(plainPassword);
        return hashed != null && hashed.equals(hashedPassword);
    }

    /**
     * Kiểm tra xem chuỗi truyền vào có phải là SHA-256 hash hay không.
     * SHA-256 hash = 64 ký tự hex lowercase.
     *
     * @param value Chuỗi cần kiểm tra
     * @return true nếu là SHA-256 hash
     */
    public static boolean isHashed(String value) {
        if (value == null) return false;
        return value.length() == 64 && value.matches("[0-9a-f]+");
    }
}
