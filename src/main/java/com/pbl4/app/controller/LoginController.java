package com.pbl4.app.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * LoginController: Xử lý đăng nhập và đăng ký tài khoản qua ChatServer & MySQL,
 * đồng thời phân quyền vai trò (Role): ADMIN chuyển vào Admin Dashboard, USER chuyển vào Chat View.
 */
public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtServerIp;

    @FXML
    private TextField txtServerPort;

    @FXML
    private Button btnLogin;

    @FXML
    private Button btnRegister;

    @FXML
    private Label lblStatus;

    @FXML
    public void initialize() {
        // Tải cấu hình mặc định từ SettingController nếu có
        String defaultIp = SettingController.getProperty("server.ip", "127.0.0.1");
        String defaultPort = SettingController.getProperty("server.port", "8888");

        if (txtServerIp != null) txtServerIp.setText(defaultIp);
        if (txtServerPort != null) txtServerPort.setText(defaultPort);

        // Phím Enter chuyển nhanh giữa các ô và đăng nhập
        if (txtUsername != null) txtUsername.setOnAction(e -> {
            if (txtPassword != null) txtPassword.requestFocus();
        });
        if (txtPassword != null) txtPassword.setOnAction(e -> onLogin(null));
        if (txtServerIp != null) txtServerIp.setOnAction(e -> {
            if (txtServerPort != null) txtServerPort.requestFocus();
        });
        if (txtServerPort != null) txtServerPort.setOnAction(e -> onLogin(null));
    }

    /**
     * Xử lý sự kiện nhấn nút Đăng nhập
     */
    @FXML
    public void onLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword != null ? txtPassword.getText().trim() : "";
        String ip = txtServerIp.getText().trim();
        String portStr = txtServerPort.getText().trim();

        if (username.isEmpty()) {
            setStatus("Vui lòng nhập tên đăng nhập!", true);
            return;
        }

        if (username.contains(" ") || username.contains(":")) {
            setStatus("Tên đăng nhập không được chứa khoảng trắng hoặc ký tự ':'!", true);
            return;
        }

        if (password.isEmpty()) {
            setStatus("Vui lòng nhập mật khẩu!", true);
            return;
        }

        if (ip.isEmpty() || portStr.isEmpty()) {
            setStatus("Vui lòng nhập IP và Cổng Server!", true);
            return;
        }

        setButtonsDisabled(true);
        setStatus("Đang kết nối đến máy chủ và xác thực...", false);

        // Chạy kết nối trong Thread riêng để tránh đơ giao diện
        new Thread(() -> {
            try {
                int port = Integer.parseInt(portStr);
                Socket socket = new Socket(ip, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // Gửi thông điệp đăng nhập có mật khẩu: LOGIN:username:password
                writer.println("LOGIN:" + username + ":" + password);
                String response = reader.readLine();

                Platform.runLater(() -> {
                    if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                        // Xác định vai trò (Role): LOGIN_SUCCESS:ADMIN hoặc LOGIN_SUCCESS:USER
                        String role = "USER";
                        if (response.contains(":")) {
                            role = response.substring(response.indexOf(':') + 1).trim();
                        }

                        setStatus("Đăng nhập thành công! Quyền: " + role, false);

                        // Phân quyền màn hình sau khi đăng nhập
                        if ("ADMIN".equalsIgnoreCase(role)) {
                            navigateToAdminScreen(username, socket, reader, writer);
                        } else {
                            navigateToMainScreen(username, role, socket, reader, writer);
                        }
                    } else {
                        String errMsg = (response != null && response.startsWith("LOGIN_FAIL:"))
                                ? response.substring(11)
                                : "Đăng nhập thất bại.";
                        setStatus(errMsg, true);
                        setButtonsDisabled(false);
                        try {
                            socket.close();
                        } catch (Exception ignored) {}
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String msg = e.getMessage();
                    if (e instanceof java.net.ConnectException || (msg != null && msg.contains("Connection refused"))) {
                        msg = "Không thể kết nối! Vui lòng đảm bảo ChatServer đang chạy tại " + ip + ":" + portStr;
                    }
                    setStatus(msg, true);
                    setButtonsDisabled(false);
                });
            }
        }).start();
    }

    /**
     * Xử lý sự kiện nhấn nút Đăng ký tài khoản mới
     */
    @FXML
    public void onRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword != null ? txtPassword.getText().trim() : "";
        String ip = txtServerIp.getText().trim();
        String portStr = txtServerPort.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu để đăng ký!", true);
            return;
        }

        if (username.contains(" ") || username.contains(":")) {
            setStatus("Tên đăng nhập không được chứa khoảng trắng hoặc ký tự ':'!", true);
            return;
        }

        if (ip.isEmpty() || portStr.isEmpty()) {
            setStatus("Vui lòng nhập IP và Cổng Server!", true);
            return;
        }

        setButtonsDisabled(true);
        setStatus("Đang gửi yêu cầu đăng ký tài khoản...", false);

        new Thread(() -> {
            try {
                int port = Integer.parseInt(portStr);
                Socket socket = new Socket(ip, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // Gửi thông điệp đăng ký: REGISTER:username:password
                writer.println("REGISTER:" + username + ":" + password);
                String response = reader.readLine();

                Platform.runLater(() -> {
                    setButtonsDisabled(false);
                    if (response != null && response.equals("REGISTER_SUCCESS")) {
                        setStatus("Đăng ký tài khoản thành công! Bạn có thể nhấn 'Đăng Nhập' ngay.", false);
                    } else {
                        String errMsg = (response != null && response.startsWith("REGISTER_FAIL:"))
                                ? response.substring(14)
                                : "Đăng ký không thành công.";
                        setStatus(errMsg, true);
                    }
                    try {
                        socket.close();
                    } catch (Exception ignored) {}
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String msg = e.getMessage();
                    if (e instanceof java.net.ConnectException || (msg != null && msg.contains("Connection refused"))) {
                        msg = "Không thể kết nối! Vui lòng đảm bảo ChatServer đang chạy tại " + ip + ":" + portStr;
                    }
                    setStatus(msg, true);
                    setButtonsDisabled(false);
                });
            }
        }).start();
    }

    private void setButtonsDisabled(boolean disabled) {
        if (btnLogin != null) btnLogin.setDisable(disabled);
        if (btnRegister != null) btnRegister.setDisable(disabled);
    }

    /**
     * Chuyển sang màn hình quản trị dành cho ADMIN
     */
    private void navigateToAdminScreen(String username, Socket socket, BufferedReader reader, PrintWriter writer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/AdminView.fxml"));
            Parent root = loader.load();

            AdminController adminController = loader.getController();
            adminController.initSession(username, socket, reader, writer);

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setTitle("PBL4 - Trang Quản Trị Hệ Thống (ADMIN)");
            stage.setScene(new Scene(root, 1050, 720));
            stage.setResizable(true);
            stage.setOnCloseRequest(e -> adminController.onLogout(null));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Lỗi tải màn hình quản trị: " + e.getMessage(), true);
            setButtonsDisabled(false);
        }
    }

    /**
     * Chuyển sang màn hình Chat chính MainView.fxml (dành cho USER thông thường)
     */
    private void navigateToMainScreen(String username, String role, Socket socket, BufferedReader reader, PrintWriter writer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/MainView.fxml"));
            Parent root = loader.load();

            MainController mainController = loader.getController();
            mainController.initSession(username, role, socket, reader, writer);

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setTitle("PBL4 Chat & P2P File Transfer - " + username);
            stage.setScene(new Scene(root, 1020, 700));
            stage.setResizable(true);
            stage.setOnCloseRequest(e -> mainController.shutdown());
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Lỗi tải màn hình chính: " + e.getMessage(), true);
            setButtonsDisabled(false);
        }
    }

    private void setStatus(String message, boolean isError) {
        if (lblStatus != null) {
            lblStatus.setText(message);
            lblStatus.setStyle(isError ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;" : "-fx-text-fill: #16a34a; -fx-font-weight: bold;" );
        }
    }
}
