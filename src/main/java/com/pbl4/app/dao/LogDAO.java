package com.pbl4.app.dao;

import com.pbl4.app.model.ActivityLog;
import com.pbl4.app.model.ChatMessageRecord;
import com.pbl4.app.model.FileTransferRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LogDAO: Xử lý thao tác với MySQL cho 3 bảng: activity_logs, chat_messages, file_transfers.
 * Bao gồm các truy vấn phân trang lịch sử chat và các hàm quản trị log dành cho Admin.
 */
public class LogDAO {

    /**
     * Lưu nhật ký hoạt động (LOGIN, LOGOUT, v.v.) vào bảng activity_logs.
     *
     * @param username Tên tài khoản
     * @param action   Hành động (LOGIN, LOGOUT,...)
     * @param details  Chi tiết hoạt động
     * @return true nếu lưu thành công
     */
    public boolean saveActivityLog(String username, String action, String details) {
        String sql = "INSERT INTO activity_logs (username, action, details) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, action);
            stmt.setString(3, details);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu nhật ký hoạt động (saveActivityLog): " + e.getMessage());
            return false;
        }
    }

    /**
     * Lưu tin nhắn chat vào bảng chat_messages (cả chat nhóm và chat riêng).
     *
     * @param sender   Người gửi
     * @param receiver Người nhận ('ALL' nếu chat nhóm, hoặc username người nhận nếu chat riêng)
     * @param message  Nội dung tin nhắn
     * @return true nếu lưu thành công
     */
    public boolean saveChatMessage(String sender, String receiver, String message) {
        String sql = "INSERT INTO chat_messages (sender, receiver, message) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sender);
            stmt.setString(2, receiver);
            stmt.setString(3, message);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu tin nhắn chat (saveChatMessage): " + e.getMessage());
            return false;
        }
    }

    /**
     * Lưu thông tin lịch sử truyền nhận file vào bảng file_transfers.
     *
     * @param sender   Người gửi
     * @param receiver Người nhận
     * @param fileName Tên tệp tin
     * @param size     Kích thước tệp tin dạng chuỗi hiển thị (VD: "2.5 MB")
     * @param status   Trạng thái (SUCCESS, FAILED,...)
     * @return true nếu lưu thành công
     */
    public boolean saveFileTransfer(String sender, String receiver, String fileName, String size, String status) {
        String sql = "INSERT INTO file_transfers (sender, receiver, file_name, file_size, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sender);
            stmt.setString(2, receiver);
            stmt.setString(3, fileName);
            stmt.setString(4, size);
            stmt.setString(5, status);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu thông tin truyền file (saveFileTransfer): " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy lịch sử chat có phân trang (Lazy Loading).
     * Lấy tin nhắn giữa user1 và user2 (hoặc user2='ALL' nếu chat nhóm).
     * Sử dụng truy vấn ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
     * Sau khi lấy xong, đảo ngược danh sách (Collections.reverse) để trả về đúng thứ tự thời gian từ cũ đến mới.
     *
     * @param user1  Người dùng hiện tại
     * @param user2  Đối tác chat hoặc 'ALL'
     * @param limit  Số lượng tin nhắn cần lấy
     * @param offset Vị trí bắt đầu lấy
     * @return Danh sách chuỗi định dạng: HISTORY:sender:message
     */
    public List<String> getChatHistory(String user1, String user2, int limit, int offset) {
        List<String> history = new ArrayList<>();
        boolean isGroupChat = "ALL".equalsIgnoreCase(user2);

        String sql;
        if (isGroupChat) {
            sql = "SELECT sender, message FROM chat_messages " +
                  "WHERE receiver = 'ALL' " +
                  "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT sender, message FROM chat_messages " +
                  "WHERE ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) AND receiver != 'ALL' " +
                  "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (isGroupChat) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);
            } else {
                stmt.setString(1, user1);
                stmt.setString(2, user2);
                stmt.setString(3, user2);
                stmt.setString(4, user1);
                stmt.setInt(5, limit);
                stmt.setInt(6, offset);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String msg = rs.getString("message");
                    history.add("HISTORY:" + sender + ":" + msg);
                }
            }

            // Đảo ngược danh sách để tin nhắn xếp từ cũ đến mới
            Collections.reverse(history);

        } catch (SQLException e) {
            System.err.println("Lỗi lấy lịch sử chat (getChatHistory): " + e.getMessage());
        }

        return history;
    }

    // =========================================================================
    // CÁC HÀM QUẢN LÝ LỊCH SỬ LOG DÀNH CHO ADMIN
    // =========================================================================

    /**
     * Admin: Lấy toàn bộ nhật ký hoạt động (Activity Logs) sắp xếp mới nhất trước
     *
     * @return Danh sách ActivityLog
     */
    public List<ActivityLog> getAllActivityLogs() {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT id, username, action, details, created_at FROM activity_logs ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                logs.add(new ActivityLog(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách activity logs: " + e.getMessage());
        }
        return logs;
    }

    /**
     * Admin: Lấy toàn bộ lịch sử tin nhắn chat
     *
     * @return Danh sách ChatMessageRecord
     */
    public List<ChatMessageRecord> getAllChatMessages() {
        List<ChatMessageRecord> messages = new ArrayList<>();
        String sql = "SELECT id, sender, receiver, message, created_at FROM chat_messages ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                messages.add(new ChatMessageRecord(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("message"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách chat messages: " + e.getMessage());
        }
        return messages;
    }

    /**
     * Admin: Lấy toàn bộ lịch sử truyền nhận file P2P
     *
     * @return Danh sách FileTransferRecord
     */
    public List<FileTransferRecord> getAllFileTransfers() {
        List<FileTransferRecord> transfers = new ArrayList<>();
        String sql = "SELECT id, sender, receiver, file_name, file_size, status, created_at FROM file_transfers ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                transfers.add(new FileTransferRecord(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("file_name"),
                        rs.getString("file_size"),
                        rs.getString("status"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách file transfers: " + e.getMessage());
        }
        return transfers;
    }

    /**
     * Admin: Xóa một bản ghi nhật ký hoạt động theo ID
     */
    public boolean deleteActivityLog(int id) {
        String sql = "DELETE FROM activity_logs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa nhật ký log: " + e.getMessage());
            return false;
        }
    }
}
