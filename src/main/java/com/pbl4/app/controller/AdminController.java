package com.pbl4.app.controller;

import com.pbl4.app.dao.LogDAO;
import com.pbl4.app.dao.UserDAO;
import com.pbl4.app.model.ActivityLog;
import com.pbl4.app.model.ChatMessageRecord;
import com.pbl4.app.model.FileTransferRecord;
import com.pbl4.app.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Optional;

/**
 * AdminController: Điều khiển giao diện quản trị dành riêng cho tài khoản có quyền ADMIN.
 * Cung cấp đầy đủ quản lý tài khoản người dùng, nhật ký hoạt động (logs), lịch sử tin nhắn và file transfers.
 */
public class AdminController {

    @FXML
    private Label lblAdminUser;

    @FXML
    private Button btnGoToChat;

    @FXML
    private Button btnLogout;

    // --- TAB 1: USERS ---
    @FXML
    private TextField txtSearchUser;
    @FXML
    private TableView<User> tvUsers;
    @FXML
    private TableColumn<User, Integer> colUserId;
    @FXML
    private TableColumn<User, String> colUserUsername;
    @FXML
    private TableColumn<User, String> colUserRole;
    @FXML
    private TableColumn<User, String> colUserStatus;
    @FXML
    private TableColumn<User, String> colUserCreatedAt;

    // --- TAB 2: ACTIVITY LOGS ---
    @FXML
    private TextField txtSearchLog;
    @FXML
    private TableView<ActivityLog> tvActivityLogs;
    @FXML
    private TableColumn<ActivityLog, Integer> colLogId;
    @FXML
    private TableColumn<ActivityLog, String> colLogUsername;
    @FXML
    private TableColumn<ActivityLog, String> colLogAction;
    @FXML
    private TableColumn<ActivityLog, String> colLogDetails;
    @FXML
    private TableColumn<ActivityLog, String> colLogCreatedAt;

    // --- TAB 3: CHAT MESSAGES ---
    @FXML
    private TextField txtSearchChat;
    @FXML
    private TableView<ChatMessageRecord> tvChatMessages;
    @FXML
    private TableColumn<ChatMessageRecord, Integer> colChatId;
    @FXML
    private TableColumn<ChatMessageRecord, String> colChatSender;
    @FXML
    private TableColumn<ChatMessageRecord, String> colChatReceiver;
    @FXML
    private TableColumn<ChatMessageRecord, String> colChatMessage;
    @FXML
    private TableColumn<ChatMessageRecord, String> colChatCreatedAt;

    // --- TAB 4: FILE TRANSFERS ---
    @FXML
    private TextField txtSearchFile;
    @FXML
    private TableView<FileTransferRecord> tvFileTransfers;
    @FXML
    private TableColumn<FileTransferRecord, Integer> colFileId;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileSender;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileReceiver;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileName;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileSize;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileStatus;
    @FXML
    private TableColumn<FileTransferRecord, String> colFileCreatedAt;

    private final UserDAO userDAO = new UserDAO();
    private final LogDAO logDAO = new LogDAO();

    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final ObservableList<ActivityLog> activityLogList = FXCollections.observableArrayList();
    private final ObservableList<ChatMessageRecord> chatMessageList = FXCollections.observableArrayList();
    private final ObservableList<FileTransferRecord> fileTransferList = FXCollections.observableArrayList();

    private FilteredList<User> filteredUsers;
    private FilteredList<ActivityLog> filteredLogs;
    private FilteredList<ChatMessageRecord> filteredChats;
    private FilteredList<FileTransferRecord> filteredFiles;

    // Phiên làm việc kết nối socket tới server
    private String adminUsername;
    private Socket chatSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    @FXML
    public void initialize() {
        setupUserTable();
        setupActivityLogTable();
        setupChatMessageTable();
        setupFileTransferTable();

        // Tự động tìm kiếm tức thì khi người dùng gõ phím (Real-time Filter)
        if (txtSearchUser != null) {
            txtSearchUser.textProperty().addListener((obs, oldVal, newVal) -> onSearchUser(null));
        }
        if (txtSearchLog != null) {
            txtSearchLog.textProperty().addListener((obs, oldVal, newVal) -> onSearchLog(null));
        }
        if (txtSearchChat != null) {
            txtSearchChat.textProperty().addListener((obs, oldVal, newVal) -> onSearchChat(null));
        }
        if (txtSearchFile != null) {
            txtSearchFile.textProperty().addListener((obs, oldVal, newVal) -> onSearchFile(null));
        }

        // Tải dữ liệu ban đầu
        onRefreshUsers(null);
        onRefreshLogs(null);
        onRefreshChat(null);
        onRefreshFiles(null);
    }

    public void initSession(String username, Socket socket, BufferedReader reader, PrintWriter writer) {
        this.adminUsername = username;
        this.chatSocket = socket;
        this.reader = reader;
        this.writer = writer;

        if (lblAdminUser != null) {
            lblAdminUser.setText("Quản trị viên: " + username + " (ADMIN)");
        }
    }

    private void setupUserTable() {
        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUserUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUserRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colUserStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colUserCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        // Badge giao diện cho Cột Vai trò (Role)
        colUserRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
                    Label badge = new Label(isAdmin ? "👑 ADMIN" : "👤 USER");
                    badge.setStyle(isAdmin
                            ? "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11px;"
                            : "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11px;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Badge giao diện cho Cột Trạng thái (Status)
        colUserStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    boolean isOnline = "ONLINE".equalsIgnoreCase(status);
                    Label badge = new Label(isOnline ? "🟢 ONLINE" : "⚪ OFFLINE");
                    badge.setStyle(isOnline
                            ? "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11px;"
                            : "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11px;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        filteredUsers = new FilteredList<>(userList, p -> true);
        tvUsers.setItems(filteredUsers);
    }

    private void setupActivityLogTable() {
        colLogId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colLogUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colLogAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colLogDetails.setCellValueFactory(new PropertyValueFactory<>("details"));
        colLogCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        colLogAction.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String action, boolean empty) {
                super.updateItem(action, empty);
                if (empty || action == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(action);
                    String style = switch (action.toUpperCase()) {
                        case "LOGIN", "REGISTER" -> "-fx-background-color: #d1fae5; -fx-text-fill: #065f46;";
                        case "LOGOUT" -> "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
                        case "ADMIN_DELETE_USER" -> "-fx-background-color: #fef2f2; -fx-text-fill: #dc2626;";
                        default -> "-fx-background-color: #f1f5f9; -fx-text-fill: #334155;";
                    };
                    badge.setStyle(style + " -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 3 8; -fx-font-size: 11px;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        filteredLogs = new FilteredList<>(activityLogList, p -> true);
        tvActivityLogs.setItems(filteredLogs);
    }

    private void setupChatMessageTable() {
        colChatId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colChatSender.setCellValueFactory(new PropertyValueFactory<>("sender"));
        colChatReceiver.setCellValueFactory(new PropertyValueFactory<>("receiver"));
        colChatMessage.setCellValueFactory(new PropertyValueFactory<>("message"));
        colChatCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        filteredChats = new FilteredList<>(chatMessageList, p -> true);
        tvChatMessages.setItems(filteredChats);
    }

    private void setupFileTransferTable() {
        colFileId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFileSender.setCellValueFactory(new PropertyValueFactory<>("sender"));
        colFileReceiver.setCellValueFactory(new PropertyValueFactory<>("receiver"));
        colFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colFileSize.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        colFileStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFileCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        colFileStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label("SUCCESS".equalsIgnoreCase(status) ? "✅ Thành công" : status);
                    badge.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #047857; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 3 8; -fx-font-size: 11px;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        filteredFiles = new FilteredList<>(fileTransferList, p -> true);
        tvFileTransfers.setItems(filteredFiles);
    }

    // =========================================================================
    // QUẢN LÝ TÀI KHOẢN (TAB 1)
    // =========================================================================

    @FXML
    public void onRefreshUsers(ActionEvent event) {
        userList.setAll(userDAO.getAllUsers());
    }

    @FXML
    public void onSearchUser(ActionEvent event) {
        String keyword = txtSearchUser.getText().trim().toLowerCase();
        filteredUsers.setPredicate(user -> {
            if (keyword.isEmpty()) return true;
            return user.getUsername().toLowerCase().contains(keyword);
        });
    }

    @FXML
    public void onAddUser(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Thêm Tài Khoản Mới");
        dialog.setHeaderText("Nhập thông tin người dùng cần tạo:");

        ButtonType btnTypeSave = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnTypeSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtNewUser = new TextField();
        txtNewUser.setPromptText("Tên đăng nhập");
        PasswordField txtNewPass = new PasswordField();
        txtNewPass.setPromptText("Mật khẩu");
        ComboBox<String> cbRole = new ComboBox<>();
        cbRole.getItems().addAll("USER", "ADMIN");
        cbRole.setValue("USER");

        grid.add(new Label("Tên đăng nhập:"), 0, 0);
        grid.add(txtNewUser, 1, 0);
        grid.add(new Label("Mật khẩu:"), 0, 1);
        grid.add(txtNewPass, 1, 1);
        grid.add(new Label("Vai trò (Role):"), 0, 2);
        grid.add(cbRole, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnTypeSave) {
            String u = txtNewUser.getText().trim();
            String p = txtNewPass.getText().trim();
            String r = cbRole.getValue();

            if (u.isEmpty() || p.isEmpty()) {
                showAlert("Cảnh báo", "Vui lòng nhập đầy đủ Tên đăng nhập và Mật khẩu!");
                return;
            }

            if (userDAO.addUser(u, p, r)) {
                logDAO.saveActivityLog(adminUsername, "ADMIN_ADD_USER", "Admin tạo tài khoản mới: " + u + " (" + r + ")");
                showAlert("Thành công", "Đã thêm tài khoản " + u + " thành công!");
                onRefreshUsers(null);
            } else {
                showAlert("Lỗi", "Không thể thêm tài khoản. Có thể tên đăng nhập đã tồn tại.");
            }
        }
    }

    @FXML
    public void onEditUserRole(ActionEvent event) {
        User selected = tvUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Thông báo", "Vui lòng chọn một tài khoản từ danh sách!");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(selected.getRole(), "USER", "ADMIN");
        dialog.setTitle("Thay Đổi Quyền (Role)");
        dialog.setHeaderText("Thay đổi vai trò cho tài khoản: " + selected.getUsername());
        dialog.setContentText("Chọn quyền:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newRole -> {
            if (userDAO.updateUserRoleAndStatus(selected.getId(), newRole, selected.getStatus())) {
                logDAO.saveActivityLog(adminUsername, "ADMIN_CHANGE_ROLE", "Đổi quyền của " + selected.getUsername() + " sang " + newRole);
                showAlert("Thành công", "Đã cập nhật quyền của " + selected.getUsername() + " thành " + newRole);
                onRefreshUsers(null);
            } else {
                showAlert("Lỗi", "Không thể cập nhật quyền người dùng.");
            }
        });
    }

    @FXML
    public void onResetPassword(ActionEvent event) {
        User selected = tvUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Thông báo", "Vui lòng chọn một tài khoản để đổi mật khẩu!");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("123456");
        dialog.setTitle("Đặt Lại Mật Khẩu");
        dialog.setHeaderText("Đặt lại mật khẩu cho tài khoản: " + selected.getUsername());
        dialog.setContentText("Nhập mật khẩu mới:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPass -> {
            String p = newPass.trim();
            if (p.isEmpty()) {
                showAlert("Cảnh báo", "Mật khẩu không được để trống!");
                return;
            }
            if (userDAO.resetPassword(selected.getId(), p)) {
                logDAO.saveActivityLog(adminUsername, "ADMIN_RESET_PASSWORD", "Reset mật khẩu cho tài khoản: " + selected.getUsername());
                showAlert("Thành công", "Đã đổi mật khẩu cho " + selected.getUsername() + " thành công!");
                onRefreshUsers(null);
            } else {
                showAlert("Lỗi", "Không thể đặt lại mật khẩu.");
            }
        });
    }

    @FXML
    public void onDeleteUser(ActionEvent event) {
        User selected = tvUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Thông báo", "Vui lòng chọn một tài khoản để xóa!");
            return;
        }

        if (selected.getUsername().equalsIgnoreCase(adminUsername)) {
            showAlert("Cảnh báo", "Không thể xóa chính tài khoản Admin bạn đang đăng nhập!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác Nhận Xóa Tài Khoản");
        confirm.setHeaderText("Bạn có chắc chắn muốn xóa vĩnh viễn tài khoản: " + selected.getUsername() + "?");
        confirm.setContentText("Thao tác này không thể hoàn tác.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (userDAO.deleteUser(selected.getId())) {
                logDAO.saveActivityLog(adminUsername, "ADMIN_DELETE_USER", "Xóa tài khoản: " + selected.getUsername());
                showAlert("Thành công", "Đã xóa tài khoản: " + selected.getUsername());
                onRefreshUsers(null);
            } else {
                showAlert("Lỗi", "Không thể xóa tài khoản.");
            }
        }
    }

    // =========================================================================
    // QUẢN LÝ NHẬT KÝ HOẠT ĐỘNG (TAB 2)
    // =========================================================================

    @FXML
    public void onRefreshLogs(ActionEvent event) {
        activityLogList.setAll(logDAO.getAllActivityLogs());
    }

    @FXML
    public void onSearchLog(ActionEvent event) {
        String keyword = txtSearchLog.getText().trim().toLowerCase();
        filteredLogs.setPredicate(log -> {
            if (keyword.isEmpty()) return true;
            return (log.getUsername() != null && log.getUsername().toLowerCase().contains(keyword)) ||
                   (log.getAction() != null && log.getAction().toLowerCase().contains(keyword)) ||
                   (log.getDetails() != null && log.getDetails().toLowerCase().contains(keyword));
        });
    }

    @FXML
    public void onDeleteLog(ActionEvent event) {
        ActivityLog selected = tvActivityLogs.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Thông báo", "Vui lòng chọn một bản ghi log cần xóa!");
            return;
        }

        if (logDAO.deleteActivityLog(selected.getId())) {
            showAlert("Thành công", "Đã xóa bản ghi log thành công!");
            onRefreshLogs(null);
        } else {
            showAlert("Lỗi", "Không thể xóa bản ghi log.");
        }
    }

    // =========================================================================
    // QUẢN LÝ LỊCH SỬ TIN NHẮN (TAB 3)
    // =========================================================================

    @FXML
    public void onRefreshChat(ActionEvent event) {
        chatMessageList.setAll(logDAO.getAllChatMessages());
    }

    @FXML
    public void onSearchChat(ActionEvent event) {
        String keyword = txtSearchChat.getText().trim().toLowerCase();
        filteredChats.setPredicate(msg -> {
            if (keyword.isEmpty()) return true;
            return (msg.getSender() != null && msg.getSender().toLowerCase().contains(keyword)) ||
                   (msg.getReceiver() != null && msg.getReceiver().toLowerCase().contains(keyword)) ||
                   (msg.getMessage() != null && msg.getMessage().toLowerCase().contains(keyword));
        });
    }

    // =========================================================================
    // QUẢN LÝ TRUYỀN FILE (TAB 4)
    // =========================================================================

    @FXML
    public void onRefreshFiles(ActionEvent event) {
        fileTransferList.setAll(logDAO.getAllFileTransfers());
    }

    @FXML
    public void onSearchFile(ActionEvent event) {
        String keyword = txtSearchFile.getText().trim().toLowerCase();
        filteredFiles.setPredicate(f -> {
            if (keyword.isEmpty()) return true;
            return (f.getFileName() != null && f.getFileName().toLowerCase().contains(keyword)) ||
                   (f.getSender() != null && f.getSender().toLowerCase().contains(keyword)) ||
                   (f.getReceiver() != null && f.getReceiver().toLowerCase().contains(keyword));
        });
    }

    // =========================================================================
    // ĐIỀU HƯỚNG & HỆ THỐNG
    // =========================================================================

    /**
     * Chuyển sang màn hình chat MainView.fxml (vẫn giữ nguyên kết nối socket)
     */
    @FXML
    public void onGoToChat(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/MainView.fxml"));
            Parent root = loader.load();

            MainController mainController = loader.getController();
            mainController.initSession(adminUsername, "ADMIN", chatSocket, reader, writer);

            Stage stage = (Stage) btnGoToChat.getScene().getWindow();
            stage.setTitle("PBL4 Chat & P2P File Transfer - " + adminUsername + " (Admin)");
            stage.setScene(new Scene(root, 1020, 700));
            stage.setOnCloseRequest(e -> mainController.shutdown());
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể chuyển sang phòng chat: " + e.getMessage());
        }
    }

    /**
     * Đăng xuất quay lại LoginView
     */
    @FXML
    public void onLogout(ActionEvent event) {
        try {
            if (writer != null) {
                writer.println("LOGOUT");
            }
            if (chatSocket != null && !chatSocket.isClosed()) {
                chatSocket.close();
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setTitle("PBL4 Chat & P2P File Transfer - Đăng Nhập");
            stage.setScene(new Scene(root, 440, 550));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
