package com.pbl4.app.controller;

import com.pbl4.app.network.P2PFileTransfer;
import com.pbl4.app.security.AESCrypto;
import com.pbl4.app.util.FileHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MainController: Điều khiển giao diện chính, phòng chat chung / riêng,
 * bắt sự kiện cuộn chuột để Lazy Load tin nhắn cũ từ MySQL, và truyền file P2P.
 */
public class MainController {

    public static final String GROUP_CHAT_TARGET = "🌐 Tất cả mọi người (Phòng Chat Chung)";

    @FXML
    private ListView<String> lvChatTargets;

    @FXML
    private ListView<String> lvChatMessages;

    @FXML
    private VBox p2pDropZone;

    @FXML
    private TextField txtMessage;

    @FXML
    private Button btnSend;

    @FXML
    private Button btnChooseFile;

    @FXML
    private Button btnChooseFolder;

    @FXML
    private Button btnSendFile;

    @FXML
    private Label lblSelectedFile;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label lblProgressStatus;

    @FXML
    private Label lblCurrentUser;

    @FXML
    private Label lblDbStatus;

    @FXML
    private Label lblChatHeader;

    @FXML
    private Label lblHistoryStatus;

    @FXML
    private Label lblTargetReceiver;

    @FXML
    private Button btnAdminPanel;

    // Quản lý Socket và Chat
    private String currentUsername;
    private Socket chatSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean isRunning = true;

    // Đối tác đang chat hiện tại: "ALL" hoặc username cụ thể
    private String currentReceiver = "ALL";
    private String currentUserRole = "USER";

    // Danh sách mục trò chuyện (Phòng chat chung + người dùng online)
    private final ObservableList<String> chatTargetsList = FXCollections.observableArrayList();
    private final ObservableList<String> chatMessagesList = FXCollections.observableArrayList();

    // Cache tin nhắn tạm theo receiver để chuyển qua lại mượt mà
    private final Map<String, List<String>> messagesCache = new HashMap<>();

    // Cờ chống spam load more khi đang tải
    private volatile boolean isLoadingHistory = false;
    private boolean scrollListenerAttached = false;

    private File selectedFileOrDir;
    private final P2PFileTransfer p2pFileTransfer = new P2PFileTransfer();

    // Dùng cho luồng gửi file P2P không đồng bộ (chờ server trả về IP:port)
    private volatile File   pendingFileToSend     = null;
    private volatile String pendingFileSendTarget = null;

    private static final String SECRET_KEY = "PBL4_SECRET_KEY_2026";

    @FXML
    public void initialize() {
        // Cài đặt danh sách đối tượng chat
        lvChatTargets.setItems(chatTargetsList);
        chatTargetsList.add(GROUP_CHAT_TARGET);
        lvChatTargets.getSelectionModel().select(0);

        // Sự kiện khi người dùng click chọn đối tượng trò chuyện bên trái
        lvChatTargets.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                switchChatTarget(newVal);
            }
        });

        // Thiết lập giao diện tùy biến cho danh sách người dùng và bong bóng chat
        setupTargetCellFactory();
        setupMessageCellFactory();
        setupDragAndDrop();

        // Cài đặt danh sách tin nhắn chat
        lvChatMessages.setItems(chatMessagesList);

        // Gắn sự kiện cuộn chuột lên đỉnh để Lazy Load
        attachScrollEventToListView();

        if (progressBar != null) {
            progressBar.setProgress(0.0);
        }
    }

    /**
     * Tùy biến hiển thị danh sách người dùng và phòng chat với Avatar, Tên và Trạng thái
     */
    private void setupTargetCellFactory() {
        lvChatTargets.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setStyle("-fx-padding: 8 10; -fx-cursor: hand;");

                boolean isGroup = item.contains("Phòng Chat Chung") || GROUP_CHAT_TARGET.equals(item);

                StackPane iconPane = new StackPane();
                iconPane.setPrefSize(34, 34);
                iconPane.setMinSize(34, 34);
                iconPane.setMaxSize(34, 34);

                VBox textBox = new VBox(2);
                textBox.setAlignment(Pos.CENTER_LEFT);

                if (isGroup) {
                    iconPane.setStyle("-fx-background-color: #e0f2fe; -fx-background-radius: 17;");
                    Label ico = new Label("👥");
                    ico.setStyle("-fx-font-size: 16px;");
                    iconPane.getChildren().add(ico);

                    Label title = new Label("Phòng Chat Chung");
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                    Label sub = new Label("Nhóm tất cả thành viên");
                    sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                    textBox.getChildren().addAll(title, sub);
                } else {
                    String initial = item.substring(0, 1).toUpperCase();
                    iconPane.setStyle("-fx-background-color: " + getAvatarColor(item) + "; -fx-background-radius: 17;");
                    Label ico = new Label(initial);
                    ico.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
                    iconPane.getChildren().add(ico);

                    Label title = new Label(item);
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                    Label sub = new Label("● Trực tuyến | P2P Sẵn sàng");
                    sub.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11px;");
                    textBox.getChildren().addAll(title, sub);
                }

                cell.getChildren().addAll(iconPane, textBox);
                setGraphic(cell);
                setText(null);
            }
        });
    }

    /**
     * Tùy biến hiển thị từng dòng tin nhắn dưới dạng Chat Bubbles hiện đại (như Telegram/Messenger)
     */
    private void setupMessageCellFactory() {
        lvChatMessages.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                // Tách thời gian [HH:mm:ss] nếu có
                String time = "";
                String raw = item;
                if (item.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\].*")) {
                    time = item.substring(1, 9);
                    raw = item.substring(11).trim();
                }

                // 1. Tin nhắn hệ thống [Hệ thống]
                if (raw.contains("[Hệ thống]")) {
                    HBox box = new HBox();
                    box.setAlignment(Pos.CENTER);
                    box.setStyle("-fx-padding: 3 0;");

                    Label badge = new Label("ℹ️ " + raw + (time.isEmpty() ? "" : "  •  " + time));
                    badge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold; " +
                                   "-fx-background-radius: 14; -fx-padding: 4 14; -fx-font-size: 11.5px;");
                    box.getChildren().add(badge);

                    setGraphic(box);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                // 2. Thông báo truyền file P2P [P2P File]
                if (raw.startsWith("[P2P File]")) {
                    HBox box = new HBox();
                    box.setAlignment(Pos.CENTER);
                    box.setStyle("-fx-padding: 4 0;");

                    Label card = new Label("📦 " + raw.replace("[P2P File]", "").trim() + (time.isEmpty() ? "" : "  •  " + time));
                    card.setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-font-weight: bold; " +
                                  "-fx-border-color: #a7f3d0; -fx-border-radius: 10; -fx-background-radius: 10; " +
                                  "-fx-padding: 6 16; -fx-font-size: 12px;");
                    box.getChildren().add(card);

                    setGraphic(box);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                // 3. Tin nhắn gửi đi từ "Bạn" (Căn PHẢI - Bong bóng màu Xanh Dương)
                boolean isMe = raw.startsWith("Bạn:") || raw.startsWith("[Bạn ->");
                if (isMe) {
                    HBox row = new HBox();
                    row.setAlignment(Pos.CENTER_RIGHT);
                    row.setStyle("-fx-padding: 3 6 3 40;");

                    VBox bubble = new VBox(3);
                    bubble.setAlignment(Pos.TOP_RIGHT);
                    bubble.setStyle("-fx-background-color: linear-gradient(to bottom right, #3b82f6, #2563eb); " +
                                    "-fx-background-radius: 16 16 3 16; -fx-padding: 8 14; " +
                                    "-fx-effect: dropshadow(three-pass-box, rgba(37,99,235,0.22), 6, 0, 0, 2);");

                    String msgText = raw;
                    int colonIdx = raw.indexOf(':');
                    if (colonIdx != -1) {
                        msgText = raw.substring(colonIdx + 1).trim();
                    }

                    Label lblHeader = new Label("Bạn" + (time.isEmpty() ? "" : " • " + time));
                    lblHeader.setStyle("-fx-text-fill: #bfdbfe; -fx-font-size: 10.5px; -fx-font-weight: bold;");

                    Label lblContent = new Label(msgText);
                    lblContent.setWrapText(true);
                    lblContent.setMaxWidth(460);
                    lblContent.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13px;");

                    bubble.getChildren().addAll(lblHeader, lblContent);
                    row.getChildren().add(bubble);

                    setGraphic(row);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                // 4. Tin nhắn nhận được từ bạn bè (Căn TRÁI - Bong bóng Thẻ Trắng)
                HBox row = new HBox(8);
                row.setAlignment(Pos.TOP_LEFT);
                row.setStyle("-fx-padding: 3 40 3 6;");

                String sender = "Người dùng";
                String msgText = raw;
                boolean isPrivate = raw.contains("[Chat riêng]");

                if (isPrivate) {
                    String clean = raw.replace("[Chat riêng]", "").trim();
                    int cIdx = clean.indexOf(':');
                    if (cIdx != -1) {
                        sender = clean.substring(0, cIdx).trim();
                        msgText = clean.substring(cIdx + 1).trim();
                    }
                } else {
                    int cIdx = raw.indexOf(':');
                    if (cIdx != -1) {
                        sender = raw.substring(0, cIdx).trim();
                        msgText = raw.substring(cIdx + 1).trim();
                    }
                }

                // Avatar tròn đại diện chữ cái đầu của người gửi
                String initial = sender.isEmpty() ? "?" : sender.substring(0, 1).toUpperCase();
                StackPane avatar = new StackPane();
                avatar.setPrefSize(32, 32);
                avatar.setMinSize(32, 32);
                avatar.setMaxSize(32, 32);

                String avatarColor = getAvatarColor(sender);
                avatar.setStyle("-fx-background-color: " + avatarColor + "; -fx-background-radius: 16;");
                Label lblInitial = new Label(initial);
                lblInitial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
                avatar.getChildren().add(lblInitial);

                // Khung Bong Bóng Tin Nhắn
                VBox bubble = new VBox(3);
                bubble.setAlignment(Pos.TOP_LEFT);
                bubble.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1.2; " +
                                "-fx-background-radius: 3 16 16 16; -fx-border-radius: 3 16 16 16; -fx-padding: 8 14; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.04), 4, 0, 0, 1);");

                Label lblSender = new Label((isPrivate ? "🔒 " : "") + sender + (time.isEmpty() ? "" : " • " + time));
                lblSender.setStyle(isPrivate
                        ? "-fx-text-fill: #b45309; -fx-font-weight: bold; -fx-font-size: 11px;"
                        : "-fx-text-fill: #4338ca; -fx-font-weight: bold; -fx-font-size: 11px;");

                Label lblContent = new Label(msgText);
                lblContent.setWrapText(true);
                lblContent.setMaxWidth(460);
                lblContent.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 13px;");

                bubble.getChildren().addAll(lblSender, lblContent);
                row.getChildren().addAll(avatar, bubble);

                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private String getAvatarColor(String name) {
        String[] colors = {"#4f46e5", "#059669", "#d97706", "#dc2626", "#7c3aed", "#0284c7", "#db2777"};
        int hash = Math.abs(name.hashCode());
        return colors[hash % colors.length];
    }

    /**
     * Hỗ trợ Kéo & Thả (Drag and Drop) tệp tin hoặc thư mục trực tiếp vào ứng dụng để gửi P2P
     */
    private void setupDragAndDrop() {
        if (p2pDropZone != null) {
            p2pDropZone.setOnDragOver(event -> {
                if (event.getGestureSource() != p2pDropZone && event.getDragboard().hasFiles()) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    if (!p2pDropZone.getStyleClass().contains("drag-active")) {
                        p2pDropZone.getStyleClass().add("drag-active");
                    }
                }
                event.consume();
            });

            p2pDropZone.setOnDragExited(event -> {
                p2pDropZone.getStyleClass().remove("drag-active");
                event.consume();
            });

            p2pDropZone.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasFiles() && !db.getFiles().isEmpty()) {
                    selectedFileOrDir = db.getFiles().get(0);
                    updateSelectedFileLabel();
                    success = true;
                }
                p2pDropZone.getStyleClass().remove("drag-active");
                event.setDropCompleted(success);
                event.consume();
            });
        }
    }

    /**
     * Chèn emoji nhanh vào ô soạn thảo khi người dùng click
     */
    @FXML
    public void onEmojiClick(ActionEvent event) {
        if (event.getSource() instanceof Button btn && txtMessage != null) {
            String emoji = btn.getText();
            txtMessage.appendText(emoji);
            txtMessage.requestFocus();
        }
    }

    /**
     * Gắn sự kiện bắt hành động cuộn chuột lên đỉnh ListView
     */
    private void attachScrollEventToListView() {
        // Cách 1: Bắt sự kiện OnScroll trực tiếp trên ListView
        lvChatMessages.setOnScroll((ScrollEvent event) -> {
            if (event.getDeltaY() > 0) { // Người dùng đang cuộn chuột lên trên
                checkAndTriggerLoadMore();
            }
        });

        // Cách 2: Lắng nghe thuộc tính vị trí valueProperty của thanh cuộn dọc (ScrollBar)
        Platform.runLater(() -> {
            ScrollBar scrollBar = getVerticalScrollBar(lvChatMessages);
            if (scrollBar != null && !scrollListenerAttached) {
                scrollListenerAttached = true;
                scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() <= 0.01 && oldVal.doubleValue() > 0.01) {
                        checkAndTriggerLoadMore();
                    }
                });
            }
        });
    }

    private ScrollBar getVerticalScrollBar(ListView<?> listView) {
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                return bar;
            }
        }
        return null;
    }

    /**
     * Kiểm tra điều kiện và gửi lệnh LOAD_MORE:receiver:số_lượng_hiện_tại lên Server
     */
    private synchronized void checkAndTriggerLoadMore() {
        if (isLoadingHistory) return;

        int currentCount = chatMessagesList.size();
        if (currentCount == 0) return;

        isLoadingHistory = true;
        if (lblHistoryStatus != null) {
            lblHistoryStatus.setText("⏳ Đang tải 50 tin nhắn cũ...");
        }

        // Gửi lệnh LOAD_MORE:receiver:offset lên Server
        if (writer != null) {
            writer.println("LOAD_MORE:" + currentReceiver + ":" + currentCount);
        }

        // Đặt timeout mở lại cờ nếu mạng chậm
        new Thread(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                isLoadingHistory = false;
                if (lblHistoryStatus != null) {
                    lblHistoryStatus.setText("⬆ Cuộn chuột lên đỉnh để tải tin nhắn cũ");
                }
            });
        }).start();
    }

    /**
     * Chuyển đổi đối tượng chat khi người dùng click bên danh sách trái
     */
    private void switchChatTarget(String targetItem) {
        // Lưu lại tin nhắn của đối tác cũ vào cache
        messagesCache.put(currentReceiver, new ArrayList<>(chatMessagesList));

        if (GROUP_CHAT_TARGET.equals(targetItem)) {
            currentReceiver = "ALL";
            lblChatHeader.setText("💬 Đang trò chuyện: Phòng Chat Chung (ALL)");
            lblTargetReceiver.setText("(Đối tác P2P: Hãy chọn người cụ thể)");
        } else {
            currentReceiver = targetItem;
            lblChatHeader.setText("💬 Đang trò chuyện riêng với: " + targetItem);
            lblTargetReceiver.setText("(Đối tác P2P: " + targetItem + ")");
        }

        chatMessagesList.clear();

        // Nạp từ cache nếu có, nếu chưa có thì gửi LOAD_MORE:receiver:0 để lấy từ CSDL
        if (messagesCache.containsKey(currentReceiver) && !messagesCache.get(currentReceiver).isEmpty()) {
            chatMessagesList.addAll(messagesCache.get(currentReceiver));
            lvChatMessages.scrollTo(chatMessagesList.size() - 1);
        } else {
            if (writer != null) {
                isLoadingHistory = true;
                writer.println("LOAD_MORE:" + currentReceiver + ":0");
            }
        }
    }

    /**
     * Khởi tạo phiên làm việc sau khi đăng nhập thành công (kèm vai trò Role)
     */
    public void initSession(String username, String role, Socket socket, BufferedReader reader, PrintWriter writer) {
        this.currentUserRole = (role != null) ? role : "USER";
        initSession(username, socket, reader, writer);
    }

    /**
     * Khởi tạo phiên làm việc sau khi đăng nhập thành công
     */
    public void initSession(String username, Socket socket, BufferedReader reader, PrintWriter writer) {
        this.currentUsername = username;
        this.chatSocket = socket;
        this.reader = reader;
        this.writer = writer;

        if (lblCurrentUser != null) {
            String displayRole = "ADMIN".equalsIgnoreCase(currentUserRole) ? " (ADMIN)" : "";
            lblCurrentUser.setText("Tài khoản: " + username + displayRole);
        }

        updateAdminButtonVisibility();

        // Cung cấp writer cho P2PFileTransfer để tự động gửi FILE_SUCCESS khi truyền 100%
        p2pFileTransfer.setServerNotifyWriter(writer);

        // Bắt đầu lắng nghe thông điệp từ ChatServer
        startChatListener();

        // Khởi động bộ nhận file P2P
        startP2PReceiver();
    }

    private void updateAdminButtonVisibility() {
        if (btnAdminPanel != null) {
            boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUserRole);
            btnAdminPanel.setVisible(isAdmin);
            btnAdminPanel.setManaged(isAdmin);
        }
    }

    /**
     * Luồng nền lắng nghe các thông điệp từ ChatServer
     */
    private void startChatListener() {
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                if (isRunning) {
                    Platform.runLater(() -> {
                        appendChatMessage("[Hệ thống] Mất kết nối tới máy chủ.");
                        if (lblDbStatus != null) {
                            lblDbStatus.setText("● Server: Ngắt kết nối");
                            lblDbStatus.setStyle("-fx-text-fill: #dc2626;");
                        }
                    });
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Xử lý gói tin nhận từ ChatServer
     */
    private void handleServerMessage(String message) {
        // 1. Cập nhật danh sách người dùng online
        if (message.startsWith("USER_LIST:")) {
            String usersStr = message.substring(10);
            String selected = lvChatTargets.getSelectionModel().getSelectedItem();

            chatTargetsList.clear();
            chatTargetsList.add(GROUP_CHAT_TARGET);

            if (!usersStr.isEmpty()) {
                String[] users = usersStr.split(",");
                for (String u : users) {
                    String trimmed = u.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(currentUsername)) {
                        chatTargetsList.add(trimmed);
                    }
                }
            }

            // Giữ lại lựa chọn hiện tại nếu còn tồn tại
            if (selected != null && chatTargetsList.contains(selected)) {
                lvChatTargets.getSelectionModel().select(selected);
            } else {
                lvChatTargets.getSelectionModel().select(0);
            }
        }
        // 2. Thông báo hệ thống
        else if (message.startsWith("SYSTEM:")) {
            appendChatMessage("[Hệ thống] " + message.substring(7));
        }
        // 3. Xử lý tiền tố HISTORY: theo Yêu cầu 1, 2, 3
        else if (message.startsWith("HISTORY:")) {
            // Định dạng HISTORY:sender:message
            String payload = message.substring(8);
            int firstColon = payload.indexOf(':');
            if (firstColon != -1) {
                String sender = payload.substring(0, firstColon);
                String msgContent = payload.substring(firstColon + 1);

                // Giải mã AES nếu nội dung được mã hóa
                String decrypted = AESCrypto.decrypt(msgContent, SECRET_KEY);
                String displayContent = (decrypted != null) ? decrypted : msgContent;

                String formattedLine;
                if (sender.equals(currentUsername)) {
                    formattedLine = "Bạn: " + displayContent;
                } else {
                    formattedLine = sender + ": " + displayContent;
                }

                // Chèn vào phía trên (đầu danh sách) theo yêu cầu Lazy Loading
                chatMessagesList.add(0, formattedLine);
            }
        }
        // 4. Kết thúc tải 1 lượt tin nhắn cũ (LOAD_MORE_DONE:receiver:count)
        else if (message.startsWith("LOAD_MORE_DONE:")) {
            String[] parts = message.substring(15).split(":");
            int loadedCount = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            isLoadingHistory = false;
            if (lblHistoryStatus != null) {
                if (loadedCount == 0) {
                    lblHistoryStatus.setText("✔ Đã tải hết tin nhắn trong lịch sử.");
                } else {
                    lblHistoryStatus.setText("✔ Đã tải thêm " + loadedCount + " tin nhắn cũ.");
                }
            }
        }
        // 5. Tin nhắn nhóm: MSG:sender:content
        else if (message.startsWith("MSG:")) {
            String payload = message.substring(4);
            int firstColon = payload.indexOf(':');
            if (firstColon != -1) {
                String sender = payload.substring(0, firstColon);
                String rawContent = payload.substring(firstColon + 1);

                // Thử giải mã nếu có mã hóa AES, nếu không thì dùng trực tiếp
                String decrypted = AESCrypto.decrypt(rawContent, SECRET_KEY);
                String displayContent = (decrypted != null) ? decrypted : rawContent;

                String displayLine = sender.equals(currentUsername) ? ("Bạn: " + displayContent) : (sender + ": " + displayContent);

                if ("ALL".equals(currentReceiver)) {
                    appendChatMessage(displayLine);
                } else {
                    // Lưu vào cache của phòng chat chung
                    messagesCache.computeIfAbsent("ALL", k -> new ArrayList<>()).add(displayLine);
                }
            }
        }
        // 6. Tin nhắn riêng nhận được: PRIVATE_MSG:sender:content
        else if (message.startsWith("PRIVATE_MSG:")) {
            String payload = message.substring(12);
            int firstColon = payload.indexOf(':');
            if (firstColon != -1) {
                String sender     = payload.substring(0, firstColon);
                String rawContent = payload.substring(firstColon + 1);

                // Giải mã AES — nếu thất bại dùng nội dung gốc
                String decrypted     = AESCrypto.decrypt(rawContent, SECRET_KEY);
                String displayContent = (decrypted != null) ? decrypted : rawContent;

                String displayLine = "[Chat riêng] " + sender + ": " + displayContent;

                if (sender.equals(currentReceiver)) {
                    appendChatMessage(displayLine);
                } else {
                    messagesCache.computeIfAbsent(sender, k -> new ArrayList<>()).add(displayLine);
                    appendChatMessage("[Có tin nhắn riêng mới từ: " + sender + "]");
                }
            }
        }
        // 7. Tin nhắn riêng gửi đi thành công (echo): PRIVATE_ECHO:receiver:content
        else if (message.startsWith("PRIVATE_ECHO:")) {
            String payload = message.substring(13);
            int firstColon = payload.indexOf(':');
            if (firstColon != -1) {
                String receiver   = payload.substring(0, firstColon);
                String rawContent = payload.substring(firstColon + 1);

                // Giải mã AES
                String decrypted     = AESCrypto.decrypt(rawContent, SECRET_KEY);
                String displayContent = (decrypted != null) ? decrypted : rawContent;

                String displayLine = "[Bạn -> " + receiver + "]: " + displayContent;

                if (receiver.equals(currentReceiver)) {
                    appendChatMessage(displayLine);
                } else {
                    messagesCache.computeIfAbsent(receiver, k -> new ArrayList<>()).add(displayLine);
                }
            }
        }
        // 8. Thông tin IP:port P2P của người nhận: P2P_INFO:username:ip:port
        else if (message.startsWith("P2P_INFO:")) {
            String[] parts = message.substring(9).split(":", 3);
            if (parts.length == 3 && pendingFileToSend != null) {
                String targetIp   = parts[1];
                int    targetPort;
                try {
                    targetPort = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    return;
                }
                File   fileToSend = pendingFileToSend;
                String receiver   = pendingFileSendTarget;
                pendingFileToSend     = null;
                pendingFileSendTarget = null;

                // Thực sự bắt đầu truyền file sau khi biết IP:port chính xác
                doSendFileP2P(targetIp, targetPort, fileToSend, receiver);
            }
        }
        // 9. Lỗi không lấy được thông tin P2P: P2P_INFO_FAIL:username:reason
        else if (message.startsWith("P2P_INFO_FAIL:")) {
            String[] parts = message.substring(14).split(":", 2);
            String reason = parts.length > 1 ? parts[1] : "Không thể kết nối P2P";
            pendingFileToSend     = null;
            pendingFileSendTarget = null;
            if (lblProgressStatus != null) {
                lblProgressStatus.setText("❌ Lỗi P2P: " + reason);
            }
        }
    }

    /**
     * Gửi tin nhắn chat (tự động mã hóa AES trước khi gửi — server chỉ lưu/broadcast của mã hóa)
     */
    @FXML
    public void onSendMessage(ActionEvent event) {
        String msg = txtMessage.getText().trim();
        if (msg.isEmpty()) return;

        if (writer != null) {
            // Mã hóa nội dung trước khi gửi — nếu mã hóa thất bại dùng plain-text
            String encrypted = AESCrypto.encrypt(msg, SECRET_KEY);
            String payload   = (encrypted != null) ? encrypted : msg;

            if ("ALL".equalsIgnoreCase(currentReceiver)) {
                writer.println("MSG:" + payload);
            } else {
                writer.println("PRIVATE_MSG:" + currentReceiver + ":" + payload);
            }
            txtMessage.clear();
        }
    }

    /**
     * Thêm tin nhắn mới vào cuối danh sách và tự cuộn xuống
     */
    private void appendChatMessage(String text) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String fullMsg = "[" + timestamp + "] " + text;
        chatMessagesList.add(fullMsg);
        lvChatMessages.scrollTo(chatMessagesList.size() - 1);
    }

    /**
     * Khởi động bộ nhận P2P đa luồng, sau đó đăng ký IP:port với ChatServer
     */
    private void startP2PReceiver() {
        int preferredPort = Integer.parseInt(SettingController.getProperty("p2p.port", "9999"));
        String saveDirPath = SettingController.getProperty("save.dir",
                System.getProperty("user.home") + File.separator + "Downloads");
        File saveDir = new File(saveDirPath);

        int boundPort = p2pFileTransfer.startReceiver(preferredPort, saveDir, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {
                Platform.runLater(() -> {
                    if (progressBar != null) progressBar.setProgress(progress);
                    if (lblProgressStatus != null) {
                        lblProgressStatus.setText(String.format("Đang nhận: %s / %s (%.1f%%)",
                                FileHelper.formatFileSize(bytesTransferred),
                                FileHelper.formatFileSize(totalBytes),
                                progress * 100));
                    }
                });
            }

            @Override
            public void onSuccess(String message) {
                Platform.runLater(() -> {
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (lblProgressStatus != null) lblProgressStatus.setText(message);
                    appendChatMessage("[P2P File] " + message);
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    if (lblProgressStatus != null) lblProgressStatus.setText("Lỗi: " + errorMessage);
                });
            }
        });

        int actualPort = (boundPort > 0) ? boundPort : preferredPort;
        // Đăng ký thông tin P2P (IP lấy tự động bắt Socket, gửi port thực tế đã bind)
        if (writer != null) {
            writer.println("P2P_REGISTER:" + actualPort);
        }
    }

    /**
     * Chọn file đơn lẻ
     */
    @FXML
    public void onChooseFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn File Cần Gửi");
        File file = fileChooser.showOpenDialog(btnChooseFile.getScene().getWindow());
        if (file != null) {
            selectedFileOrDir = file;
            updateSelectedFileLabel();
        }
    }

    /**
     * Chọn thư mục
     */
    @FXML
    public void onChooseFolder(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Chọn Thư Mục Cần Gửi");
        File dir = dirChooser.showDialog(btnChooseFolder.getScene().getWindow());
        if (dir != null) {
            selectedFileOrDir = dir;
            updateSelectedFileLabel();
        }
    }

    private void updateSelectedFileLabel() {
        if (selectedFileOrDir != null && lblSelectedFile != null) {
            String type = selectedFileOrDir.isDirectory() ? "[Thư mục] " : "[File] ";
            lblSelectedFile.setText(type + selectedFileOrDir.getName() + " (" + FileHelper.formatFileSize(selectedFileOrDir.length()) + ")");
        }
    }

    /**
     * Bắt đầu truyền file P2P:
     * Gửi yêu cầu GET_P2P_INFO lên ChatServer để lấy IP và Port thực tế của đối tác
     */
    @FXML
    public void onSendFileP2P(ActionEvent event) {
        if (selectedFileOrDir == null) {
            showAlert("Thông báo", "Vui lòng chọn file hoặc thư mục trước khi gửi!");
            return;
        }

        if ("ALL".equalsIgnoreCase(currentReceiver)) {
            showAlert("Thông báo", "Vui lòng chọn một người dùng cụ thể trong danh sách bên trái để gửi file P2P!");
            return;
        }

        if (progressBar != null) progressBar.setProgress(0.0);
        if (lblProgressStatus != null) {
            lblProgressStatus.setText("Đang yêu cầu kết nối P2P tới " + currentReceiver + "...");
        }

        // Lưu trạng thái file cần gửi
        pendingFileToSend = selectedFileOrDir;
        pendingFileSendTarget = currentReceiver;

        if (writer != null) {
            writer.println("GET_P2P_INFO:" + currentReceiver);
        } else {
            // Fallback nếu không có kết nối server
            String targetIp = SettingController.getProperty("server.ip", "127.0.0.1");
            int targetPort = Integer.parseInt(SettingController.getProperty("p2p.port", "9999"));
            doSendFileP2P(targetIp, targetPort, pendingFileToSend, pendingFileSendTarget);
            pendingFileToSend = null;
            pendingFileSendTarget = null;
        }
    }

    /**
     * Thực hiện truyền file P2P sau khi có thông tin IP:Port đích
     */
    private void doSendFileP2P(String targetIp, int targetPort, File fileToSend, String receiver) {
        if (fileToSend == null || receiver == null) return;

        if (progressBar != null) progressBar.setProgress(0.0);
        if (lblProgressStatus != null) {
            lblProgressStatus.setText("Đang kết nối P2P tới " + receiver + " (" + targetIp + ":" + targetPort + ")...");
        }

        // Gửi file qua P2P, truyền kèm receiver để P2PFileTransfer tự bắn FILE_SUCCESS lên Server khi 100%
        p2pFileTransfer.sendFile(targetIp, targetPort, fileToSend, receiver, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {
                Platform.runLater(() -> {
                    if (progressBar != null) progressBar.setProgress(progress);
                    if (lblProgressStatus != null) {
                        lblProgressStatus.setText(String.format("Đang gửi: %s / %s (%.1f%%)",
                                FileHelper.formatFileSize(bytesTransferred),
                                FileHelper.formatFileSize(totalBytes),
                                progress * 100));
                    }
                });
            }

            @Override
            public void onSuccess(String message) {
                Platform.runLater(() -> {
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (lblProgressStatus != null) lblProgressStatus.setText(message);
                    appendChatMessage("[P2P File] " + message + " -> " + receiver);
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    if (lblProgressStatus != null) lblProgressStatus.setText("Lỗi gửi file: " + errorMessage);
                });
            }
        });
    }

    /**
     * Mở màn hình Cài đặt
     */
    @FXML
    public void onOpenSettings(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/SettingView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Cài Đặt Cấu Hình");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            showAlert("Lỗi", "Không thể mở màn hình cài đặt: " + e.getMessage());
        }
    }

    /**
     * Mở màn hình Quản Trị Hệ Thống (Dành riêng cho ADMIN)
     */
    @FXML
    public void onOpenAdminPanel(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/AdminView.fxml"));
            Parent root = loader.load();

            AdminController adminController = loader.getController();
            adminController.initSession(currentUsername, chatSocket, reader, writer);

            Stage stage = (Stage) btnAdminPanel.getScene().getWindow();
            stage.setTitle("PBL4 - Trang Quản Trị Hệ Thống (ADMIN)");
            stage.setScene(new Scene(root, 1050, 720));
            stage.setOnCloseRequest(e -> adminController.onLogout(null));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể mở trang quản trị: " + e.getMessage());
        }
    }

    /**
     * Đăng xuất và quay lại màn hình Login
     */
    @FXML
    public void onLogout(ActionEvent event) {
        shutdown();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/pbl4/app/view/LoginView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) btnSend.getScene().getWindow();
            stage.setTitle("PBL4 Chat & P2P File Transfer - Đăng Nhập");
            stage.setScene(new Scene(root, 440, 550));
            stage.show();
        } catch (Exception e) {
            showAlert("Lỗi", "Không thể quay lại màn hình đăng nhập: " + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void shutdown() {
        isRunning = false;
        p2pFileTransfer.stopReceiver();
        try {
            if (writer != null) {
                writer.println("LOGOUT");
            }
            if (chatSocket != null && !chatSocket.isClosed()) {
                chatSocket.close();
            }
        } catch (IOException ignored) {}
    }
}
