package com.pbl4.app.dao;

import com.pbl4.app.security.PasswordHelper;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * DatabaseConnection: Quản lý kết nối JDBC đến cơ sở dữ liệu MySQL pbl4_db.
 * Đảm bảo an toàn đa luồng cho hệ thống máy chủ ChatServer và Client.
 * Tự động kiểm tra và khởi tạo CSDL pbl4_db cùng các bảng nếu chưa tồn tại.
 */
public class DatabaseConnection {

    private static final String CONFIG_FILE = "config.properties";

    private static String dbHost = "localhost";
    private static String dbPort = "3306";
    private static String dbName = "pbl4_db";
    private static String dbUser = "root";
    private static String dbPass = "";

    private static volatile boolean initialized = false;

    static {
        loadConfig();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Không tìm thấy MySQL Driver: " + e.getMessage());
        }
    }

    private DatabaseConnection() {
        // Private constructor
    }

    /**
     * Tải cấu hình CSDL từ config.properties nếu có
     */
    public static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(in);
                dbHost = props.getProperty("db.host", dbHost);
                dbPort = props.getProperty("db.port", dbPort);
                dbName = props.getProperty("db.name", dbName);
                dbUser = props.getProperty("db.username", props.getProperty("db.user", dbUser));
                dbPass = props.getProperty("db.password", props.getProperty("db.pass", dbPass));
            } catch (Exception e) {
                System.err.println("Không thể đọc cấu hình CSDL từ config.properties: " + e.getMessage());
            }
        }
    }

    public static String getDatabaseName() {
        return dbName;
    }

    public static String getUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
    }

    private static String getServerUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
    }

    /**
     * Tự động khởi tạo Database pbl4_db và các bảng nếu chưa có.
     * @return true nếu CSDL sẵn sàng sử dụng
     */
    public static synchronized boolean initializeDatabase() {
        if (initialized) {
            return true;
        }

        loadConfig();

        // 1. Tạo Database nếu chưa tồn tại
        try (Connection conn = DriverManager.getConnection(getServerUrl(), dbUser, dbPass);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        } catch (SQLException e) {
            System.err.println("Lưu ý khi tạo CSDL từ Server URL: " + e.getMessage());
        }

        // 2. Tạo các bảng trong CSDL
        try (Connection conn = DriverManager.getConnection(getUrl(), dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            // Bảng users
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id INT AUTO_INCREMENT PRIMARY KEY," +
                "    username VARCHAR(50) NOT NULL UNIQUE," +
                "    password VARCHAR(255) NOT NULL," +
                "    role VARCHAR(20) DEFAULT 'USER'," +
                "    status VARCHAR(20) DEFAULT 'OFFLINE'," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
            );

            // Đảm bảo có cột role cho bảng users
            try {
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER';");
            } catch (SQLException ignored) {}

            // Bảng activity_logs
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS activity_logs (" +
                "    id INT AUTO_INCREMENT PRIMARY KEY," +
                "    username VARCHAR(50) NOT NULL," +
                "    action VARCHAR(50) NOT NULL," +
                "    details TEXT," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    INDEX idx_user_activity (username, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
            );

            // Bảng chat_messages
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "    id INT AUTO_INCREMENT PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    receiver VARCHAR(50) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    INDEX idx_chat_receiver (receiver, created_at)," +
                "    INDEX idx_chat_private (sender, receiver, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
            );

            // Bảng file_transfers
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS file_transfers (" +
                "    id INT AUTO_INCREMENT PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    receiver VARCHAR(50) NOT NULL," +
                "    file_name VARCHAR(255) NOT NULL," +
                "    file_size VARCHAR(50) NOT NULL," +
                "    status VARCHAR(50) DEFAULT 'SUCCESS'," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    INDEX idx_file_sender_receiver (sender, receiver, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
            );

            // Thêm các tài khoản mặc định nếu bảng users chưa có dữ liệu
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Hash mật khẩu mặc định trước khi lưu — KHÔNG bao giờ lưu plain-text
                    String hashedAdmin = PasswordHelper.hashPassword("123456");
                    String hashedAlice = PasswordHelper.hashPassword("123456");
                    String hashedBob   = PasswordHelper.hashPassword("123456");

                    String insertSql = "INSERT INTO users (username, password, role, status) VALUES (?, ?, ?, 'OFFLINE')";
                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        ins.setString(1, "admin"); ins.setString(2, hashedAdmin); ins.setString(3, "ADMIN"); ins.executeUpdate();
                        ins.setString(1, "alice"); ins.setString(2, hashedAlice); ins.setString(3, "USER");  ins.executeUpdate();
                        ins.setString(1, "bob");   ins.setString(2, hashedBob);   ins.setString(3, "USER");  ins.executeUpdate();
                    }
                    System.out.println("Đã thêm tài khoản mặc định (mật khẩu đã được băm SHA-256): admin, alice, bob — mật khẩu gốc: 123456");
                }
            }

            // Tự động migrate mật khẩu plain-text cũ sang SHA-256 hash
            migratePasswords(conn);

            initialized = true;
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi khi kết nối hoặc khởi tạo bảng CSDL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra và migrate mật khẩu plain-text cũ trong DB sang SHA-256 hash.
     * Nhận diện plain-text bằng cách: SHA-256 hash luôn dài đúng 64 ký tự hex.
     */
    private static void migratePasswords(Connection conn) {
        String selectSql = "SELECT id, username, password FROM users WHERE LENGTH(password) <> 64";
        try (Statement sel = conn.createStatement();
             ResultSet rs = sel.executeQuery(selectSql)) {

            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String username = rs.getString("username");
                String plainPass = rs.getString("password");

                // Chỉ băm nếu chưa phải SHA-256 hash
                if (!PasswordHelper.isHashed(plainPass)) {
                    String hashed = PasswordHelper.hashPassword(plainPass);
                    if (hashed != null) {
                        try (PreparedStatement upd = conn.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
                            upd.setString(1, hashed);
                            upd.setInt(2, id);
                            upd.executeUpdate();
                            count++;
                        }
                    }
                }
            }

            if (count > 0) {
                System.out.println("[DB Migration] Đã băm SHA-256 cho " + count + " tài khoản có mật khẩu plain-text.");
            }
        } catch (SQLException e) {
            System.err.println("[DB Migration] Lỗi khi migrate mật khẩu: " + e.getMessage());
        }
    }

    /**
     * Lấy kết nối mới tới MySQL Database pbl4_db.
     * Mỗi luồng sử dụng kết nối riêng trong try-with-resources để đảm bảo an toàn đa luồng.
     *
     * @return Connection đối tượng kết nối
     * @throws SQLException Nếu xảy ra lỗi kết nối
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeDatabase();
        }
        return DriverManager.getConnection(getUrl(), dbUser, dbPass);
    }

    /**
     * Kiểm tra trạng thái kết nối tới cơ sở dữ liệu
     *
     * @return true nếu kết nối thành công
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
