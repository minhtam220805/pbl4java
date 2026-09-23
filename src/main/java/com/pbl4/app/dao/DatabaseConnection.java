package com.pbl4.app.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DatabaseConnection: Quản lý kết nối JDBC đến cơ sở dữ liệu MySQL pbl4_db.
 * Đảm bảo an toàn đa luồng cho hệ thống máy chủ ChatServer và Client.
 */
public class DatabaseConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/pbl4_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    static {
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
     * Lấy kết nối mới tới MySQL Database pbl4_db.
     * Mỗi luồng sử dụng kết nối riêng trong try-with-resources để đảm bảo an toàn đa luồng.
     *
     * @return Connection đối tượng kết nối
     * @throws SQLException Nếu xảy ra lỗi kết nối
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
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
            System.err.println("Lỗi kiểm tra kết nối CSDL: " + e.getMessage());
            return false;
        }
    }
}
