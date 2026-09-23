package com.pbl4.app.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * SettingController: Lưu cấu hình IP Server, Port, thư mục lưu file.
 */
public class SettingController {

    @FXML
    private TextField txtServerIp;

    @FXML
    private TextField txtServerPort;

    @FXML
    private TextField txtP2PPort;

    @FXML
    private TextField txtSaveDirectory;

    @FXML
    private Button btnBrowse;

    @FXML
    private Button btnSave;

    private static final String CONFIG_FILE = "config.properties";

    @FXML
    public void initialize() {
        loadSettings();
    }

    /**
     * Tải cài đặt từ file cấu hình config.properties
     */
    public void loadSettings() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                if (txtServerIp != null) txtServerIp.setText(props.getProperty("server.ip", "127.0.0.1"));
                if (txtServerPort != null) txtServerPort.setText(props.getProperty("server.port", "8888"));
                if (txtP2PPort != null) txtP2PPort.setText(props.getProperty("p2p.port", "9999"));
                if (txtSaveDirectory != null) txtSaveDirectory.setText(props.getProperty("save.dir", System.getProperty("user.home") + File.separator + "Downloads"));
            } catch (Exception e) {
                System.err.println("Lỗi đọc cấu hình: " + e.getMessage());
            }
        } else {
            // Giá trị mặc định
            if (txtServerIp != null) txtServerIp.setText("127.0.0.1");
            if (txtServerPort != null) txtServerPort.setText("8888");
            if (txtP2PPort != null) txtP2PPort.setText("9999");
            if (txtSaveDirectory != null) txtSaveDirectory.setText(System.getProperty("user.home") + File.separator + "Downloads");
        }
    }

    /**
     * Mở hộp thoại chọn thư mục lưu trữ file
     */
    @FXML
    public void onBrowseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Chọn Thư Mục Lưu File");
        File selectedDir = directoryChooser.showDialog(txtSaveDirectory.getScene().getWindow());
        if (selectedDir != null) {
            txtSaveDirectory.setText(selectedDir.getAbsolutePath());
        }
    }

    /**
     * Lưu cấu hình vào file config.properties
     */
    @FXML
    public void onSaveSettings() {
        Properties props = new Properties();
        props.setProperty("server.ip", txtServerIp.getText().trim());
        props.setProperty("server.port", txtServerPort.getText().trim());
        props.setProperty("p2p.port", txtP2PPort.getText().trim());
        props.setProperty("save.dir", txtSaveDirectory.getText().trim());

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "PBL4 Chat & P2P Configuration");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText("Cài đặt đã được lưu thành công!");
            alert.showAndWait();

            // Đóng cửa sổ cài đặt nếu là popup
            Stage stage = (Stage) btnSave.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setContentText("Không thể lưu cấu hình: " + e.getMessage());
            alert.showAndWait();
        }
    }

    public static String getProperty(String key, String defaultValue) {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                return props.getProperty(key, defaultValue);
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}
