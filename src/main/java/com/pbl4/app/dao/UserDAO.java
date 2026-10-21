package com.pbl4.app.dao;

import com.pbl4.app.model.User;
import com.pbl4.app.security.PasswordHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * UserDAO: Thao tác dữ liệu người dùng (Users) trong cơ sở dữ liệu MySQL,
 * bao gồm xác thực đăng nhập, phân quyền (Role) và các chức năng CRUD quản trị tài khoản cho Admin.
 */
public class UserDAO {

    /**
     * Xác thực đăng nhập người dùng từ CSDL
     *
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @return true nếu thông tin hợp lệ, ngược lại false
     */
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        String cleanUser = username.trim();
        String cleanPass = password.trim();
        String hashedPassword = PasswordHelper.hashPassword(cleanPass);
        if (hashedPassword == null) return false;

        String sql = "SELECT id, password FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cleanUser);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String dbPass = rs.getString("password");

                    // 1. Khớp với mật khẩu đã băm SHA-256
                    if (hashedPassword.equals(dbPass)) {
                        return true;
                    }

                    // 2. Tương thích ngược: Nếu CSDL lưu plain-text cũ (như '123456'), cho phép đăng nhập và tự động băm lại ngay
                    if (cleanPass.equals(dbPass)) {
                        try (PreparedStatement upd = conn.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
                            upd.setString(1, hashedPassword);
                            upd.setInt(2, id);
                            upd.executeUpdate();
                        }
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi đăng nhập CSDL: " + e.getMessage());
            return false;
        }
        return false;
    }

    /**
     * Lấy vai trò (Role) của người dùng: "ADMIN" hoặc "USER"
     *
     * @param username Tên đăng nhập
     * @return Role của người dùng (mặc định "USER" nếu không tìm thấy)
     */
    public String getUserRole(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "USER";
        }
        String sql = "SELECT role FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    return (role != null && !role.isEmpty()) ? role.toUpperCase() : "USER";
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy Role người dùng: " + e.getMessage());
        }
        return "USER";
    }

    /**
     * Kiểm tra xem tên đăng nhập đã tồn tại trong CSDL hay chưa
     *
     * @param username Tên đăng nhập cần kiểm tra
     * @return true nếu đã tồn tại
     */
    public boolean userExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra người dùng tồn tại: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đăng ký tài khoản người dùng mới vào CSDL (Mặc định Role: USER)
     *
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @return true nếu đăng ký thành công, ngược lại false
     */
    public boolean register(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        String cleanUser = username.trim();
        String cleanPass = password.trim();

        if (userExists(cleanUser)) {
            return false;
        }

        String hashedPassword = PasswordHelper.hashPassword(cleanPass);
        if (hashedPassword == null) {
            System.err.println("Lỗi băm mật khẩu khi đăng ký tài khoản: " + username);
            return false;
        }

        String sql = "INSERT INTO users (username, password, role, status) VALUES (?, ?, 'USER', 'OFFLINE')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cleanUser);
            stmt.setString(2, hashedPassword);  // Lưu SHA-256 hash, KHÔNG lưu plain-text

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi đăng ký tài khoản: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái người dùng (ONLINE / OFFLINE)
     *
     * @param username Tên người dùng
     * @param status   Trạng thái mới
     */
    public void updateStatus(String username, String status) {
        String sql = "UPDATE users SET status = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái người dùng: " + e.getMessage());
        }
    }

    // =========================================================================
    // CÁC HÀM QUẢN TRỊ TÀI KHOẢN (DÀNH CHO ADMIN)
    // =========================================================================

    /**
     * Lấy toàn bộ danh sách tài khoản trong hệ thống
     *
     * @return Danh sách đối tượng User
     */
    public List<User> getAllUsers() {
        List<User> userList = new ArrayList<>();
        String sql = "SELECT id, username, password, role, status, created_at FROM users ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                User user = new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role") != null ? rs.getString("role") : "USER",
                        rs.getString("status") != null ? rs.getString("status") : "OFFLINE",
                        rs.getString("created_at")
                );
                userList.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách users: " + e.getMessage());
        }
        return userList;
    }

    /**
     * Admin thêm một tài khoản mới với vai trò chỉ định
     *
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @param role     "ADMIN" hoặc "USER"
     * @return true nếu thêm thành công
     */
    public boolean addUser(String username, String password, String role) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        if (userExists(username.trim())) {
            return false;
        }
        String hashedPassword = PasswordHelper.hashPassword(password.trim());
        if (hashedPassword == null) return false;

        String sql = "INSERT INTO users (username, password, role, status) VALUES (?, ?, ?, 'OFFLINE')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());
            stmt.setString(2, hashedPassword);  // Lưu SHA-256 hash
            stmt.setString(3, (role != null && !role.isEmpty()) ? role.toUpperCase() : "USER");

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin cập nhật thông tin tài khoản (Role, Status)
     *
     * @param id     Mã người dùng
     * @param role   Vai trò mới
     * @param status Trạng thái
     * @return true nếu cập nhật thành công
     */
    public boolean updateUserRoleAndStatus(int id, String role, String status) {
        String sql = "UPDATE users SET role = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, role);
            stmt.setString(2, status);
            stmt.setInt(3, id);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin đặt lại mật khẩu cho một tài khoản
     *
     * @param id          Mã người dùng
     * @param newPassword Mật khẩu mới
     * @return true nếu đổi thành công
     */
    public boolean resetPassword(int id, String newPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        String hashedPassword = PasswordHelper.hashPassword(newPassword.trim());
        if (hashedPassword == null) return false;

        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, hashedPassword);  // Lưu SHA-256 hash
            stmt.setInt(2, id);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi đổi mật khẩu user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin xóa một tài khoản khỏi hệ thống
     *
     * @param id Mã tài khoản
     * @return true nếu xóa thành công
     */
    public boolean deleteUser(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa tài khoản: " + e.getMessage());
            return false;
        }
    }
}
