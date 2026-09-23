package com.pbl4.app.security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AESCrypto: Lớp xử lý mã hóa AES (chuẩn CBC) an toàn cho Chat và File.
 */
public class AESCrypto {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";

    /**
     * Tạo SecretKeySpec 128-bit từ chuỗi khóa bí mật bằng thuật toán băm SHA-256
     */
    private static SecretKeySpec getKey(String secretKey) {
        try {
            byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Lấy 16 byte (128 bit) đầu tiên
            return new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo AES key: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PHẦN 1: DÀNH CHO TRUYỀN NHẬN TIN NHẮN CHAT (DẠNG CHUỖI TEXT)
    // =========================================================================

    /**
     * Mã hóa chuỗi văn bản (dùng cho chat)
     */
    public static String encrypt(String strToEncrypt, String secretKey) {
        try {
            if (strToEncrypt == null || secretKey == null) return null;
            byte[] encryptedBytes = encryptBytes(strToEncrypt.getBytes(StandardCharsets.UTF_8), secretKey);
            if (encryptedBytes == null) return null;
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("Lỗi khi mã hóa chuỗi AES: " + e.getMessage());
            return null;
        }
    }

    /**
     * Giải mã chuỗi văn bản (dùng cho chat)
     */
    public static String decrypt(String strToDecrypt, String secretKey) {
        try {
            if (strToDecrypt == null || secretKey == null) return null;
            byte[] encryptedBytes = Base64.getDecoder().decode(strToDecrypt);
            byte[] decryptedBytes = decryptBytes(encryptedBytes, secretKey);
            if (decryptedBytes == null) return null;
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Lỗi khi giải mã chuỗi AES: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // PHẦN 2: DÀNH CHO TRUYỀN FILE BẰNG P2P (DẠNG MẢNG BYTE)
    // =========================================================================

    /**
     * Mã hóa mảng byte nguyên thủy (dùng khi đọc file lên để truyền qua Socket)
     */
    public static byte[] encryptBytes(byte[] data, String secretKey) {
        try {
            SecretKeySpec keySpec = getKey(secretKey);
            
            // Tạo IV (Initialization Vector) ngẫu nhiên 16 byte để tăng cường bảo mật cho CBC
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encryptedData = cipher.doFinal(data);
            
            // Gộp IV vào phần đầu của dữ liệu mã hóa (để khi giải mã có thể tách ra)
            byte[] finalData = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, finalData, 0, iv.length);
            System.arraycopy(encryptedData, 0, finalData, iv.length, encryptedData.length);
            
            return finalData;
        } catch (Exception e) {
            System.err.println("Lỗi mã hóa byte AES: " + e.getMessage());
            return null;
        }
    }

    /**
     * Giải mã mảng byte nguyên thủy (dùng khi nhận file từ Socket trước khi ghi xuống ổ cứng)
     */
    public static byte[] decryptBytes(byte[] encryptedDataWithIv, String secretKey) {
        try {
            // Tách 16 byte đầu tiên ra làm IV
            byte[] iv = Arrays.copyOfRange(encryptedDataWithIv, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Lấy phần dữ liệu thực sự (từ byte thứ 16 đến hết)
            byte[] cipherText = Arrays.copyOfRange(encryptedDataWithIv, 16, encryptedDataWithIv.length);
            
            SecretKeySpec keySpec = getKey(secretKey);
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            System.err.println("Lỗi giải mã byte AES: " + e.getMessage());
            return null;
        }
    }
}