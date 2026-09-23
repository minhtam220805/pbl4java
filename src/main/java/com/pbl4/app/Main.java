package com.pbl4.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main: Kế thừa Application, khởi chạy giao diện từ LoginView.fxml.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 420, 480);
            primaryStage.setTitle("PBL4 - Đăng Nhập Hệ Thống");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi khi tải LoginView.fxml: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
