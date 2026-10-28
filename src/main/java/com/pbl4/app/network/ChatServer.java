package com.pbl4.app.network;

import com.pbl4.app.dao.DatabaseConnection;
import com.pbl4.app.dao.LogDAO;
import com.pbl4.app.dao.UserDAO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatServer: TCP Server quản lý xác thực đăng nhập MySQL, phân quyền vai trò (Role),
 * nhật ký hoạt động, lịch sử tin nhắn nhóm/riêng, truyền file P2P và phân trang tin nhắn cũ (Lazy Loading).
 */
public class ChatServer {

    private final int port;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private ExecutorService threadPool;

    private final UserDAO userDAO = new UserDAO();
    private final LogDAO logDAO = new LogDAO();

    public ChatServer(int port) {
        this.port = port;
    }

    /**
     * Khởi động server lắng nghe trên port được cấu hình
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();
            boolean dbOk = DatabaseConnection.initializeDatabase();
            System.out.println("==================================================");
            System.out.println("  ChatServer đã khởi động thành công trên cổng: " + port);
            if (dbOk) {
                System.out.println("  Kết nối CSDL MySQL: " + DatabaseConnection.getDatabaseName() + " [SẴN SÀNG]");
            } else {
                System.err.println("  [CẢNH BÁO] Không thể kết nối hoặc khởi tạo CSDL MySQL: " + DatabaseConnection.getDatabaseName());
                System.err.println("  Vui lòng đảm bảo dịch vụ MySQL (XAMPP) đang chạy trên cổng 3306.");
            }
            System.out.println("==================================================");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Lỗi Server: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Dừng server và đóng tất cả kết nối client
     */
    public void stop() {
        isRunning = false;
        try {
            for (ClientHandler client : clients.values()) {
                client.closeConnection();
            }
            clients.clear();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
            }
            System.out.println("ChatServer đã dừng.");
        } catch (IOException e) {
            System.err.println("Lỗi khi dừng server: " + e.getMessage());
        }
    }

    /**
     * Broadcast tin nhắn đến tất cả các client đang online
     */
    public void broadcastMessage(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    /**
     * Broadcast cập nhật danh sách user online đến tất cả client
     */
    public void broadcastUserList() {
        String userListStr = "USER_LIST:" + String.join(",", clients.keySet());
        broadcastMessage(userListStr);
    }

    /**
     * Quản lý từng kết nối client
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String username;
        private String userRole = "USER";

        // Thông tin P2P của client này (IP + port) — được đăng ký sau khi đăng nhập
        private String p2pIp = null;
        private int    p2pPort = -1;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // Vòng lặp xác thực ban đầu (LOGIN hoặc REGISTER)
                boolean authenticated = false;
                while (!authenticated && isRunning) {
                    String line = reader.readLine();
                    if (line == null) return;

                    if (line.startsWith("REGISTER:")) {
                        // Cú pháp REGISTER:username:password
                        String[] parts = line.substring(9).split(":", 2);
                        if (parts.length == 2) {
                            String regUser = parts[0].trim();
                            String regPass = parts[1].trim();
                            if (regUser.isEmpty() || regPass.isEmpty()) {
                                writer.println("REGISTER_FAIL:Tên đăng nhập và mật khẩu không được để trống!");
                            } else if (regUser.contains(" ") || regUser.contains(":")) {
                                writer.println("REGISTER_FAIL:Tên đăng nhập không được chứa khoảng trắng hoặc dấu hai chấm!");
                            } else if (!DatabaseConnection.testConnection()) {
                                writer.println("REGISTER_FAIL:Không thể kết nối CSDL MySQL! Vui lòng kiểm tra MySQL server.");
                            } else if (userDAO.userExists(regUser)) {
                                writer.println("REGISTER_FAIL:Tên tài khoản đã tồn tại trong hệ thống!");
                            } else if (userDAO.register(regUser, regPass)) {
                                logDAO.saveActivityLog(regUser, "REGISTER", "Đăng ký tài khoản thành công từ " + socket.getInetAddress().getHostAddress());
                                writer.println("REGISTER_SUCCESS");
                            } else {
                                writer.println("REGISTER_FAIL:Lỗi hệ thống khi lưu tài khoản vào cơ sở dữ liệu!");
                            }
                        } else {
                            writer.println("REGISTER_FAIL:Dữ liệu đăng ký không đúng định dạng!");
                        }
                    } else if (line.startsWith("LOGIN:")) {
                        // Cú pháp LOGIN:username:password hoặc LOGIN:username
                        String[] parts = line.substring(6).split(":", 2);
                        String loginUser = parts[0].trim();
                        String loginPass = parts.length > 1 ? parts[1].trim() : "";

                        if (loginUser.isEmpty()) {
                            writer.println("LOGIN_FAIL:Tên người dùng không được để trống.");
                            continue;
                        }

                        if (!DatabaseConnection.testConnection()) {
                            writer.println("LOGIN_FAIL:Không thể kết nối CSDL MySQL! Vui lòng kiểm tra MySQL server.");
                            continue;
                        }

                        // So sánh tài khoản được lưu trong MySQL Database
                        boolean validCredentials = true;
                        if (!loginPass.isEmpty()) {
                            validCredentials = userDAO.login(loginUser, loginPass);
                        } else {
                            validCredentials = userDAO.userExists(loginUser);
                        }

                        if (!validCredentials) {
                            writer.println("LOGIN_FAIL:Sai tên đăng nhập hoặc mật khẩu!");
                            continue;
                        }

                        if (clients.containsKey(loginUser)) {
                            writer.println("LOGIN_FAIL:Tài khoản đang đăng nhập ở một phiên khác!");
                            continue;
                        }

                        // Đăng nhập thành công, lấy thông tin phân quyền Role
                        username = loginUser;
                        userRole = userDAO.getUserRole(username);

                        // Gửi phản hồi LOGIN_SUCCESS ngay lập tức trước khi thêm vào danh sách clients
                        // Điều này ngăn ngừa race condition khi có broadcast ngắt kết nối/tin nhắn từ client khác xen vào
                        writer.println("LOGIN_SUCCESS:" + userRole);
                        authenticated = true;

                        clients.put(username, this);
                        userDAO.updateStatus(username, "ONLINE");

                        // Lưu log LOGIN kèm vai trò
                        logDAO.saveActivityLog(username, "LOGIN", "Đăng nhập thành công (Quyền: " + userRole + ") từ IP " + socket.getInetAddress().getHostAddress());

                        // Mặc định gọi getChatHistory(username, "ALL", 50, 0) để trả về 50 tin nhắn nhóm gần nhất
                        List<String> groupHistory = logDAO.getChatHistory(username, "ALL", 50, 0);
                        for (String historyMsg : groupHistory) {
                            writer.println(historyMsg);
                        }

                        broadcastMessage("SYSTEM:" + username + " đã tham gia phòng chat.");
                        broadcastUserList();
                    }
                }

                // Vòng lặp nhận thông điệp sau khi đăng nhập thành công
                String clientMessage;
                while ((clientMessage = reader.readLine()) != null) {
                    if (clientMessage.startsWith("LOGOUT")) {
                        break;
                    }

                    // 1. Chat nhóm: MSG:nội dung
                    if (clientMessage.startsWith("MSG:")) {
                        String msgContent = clientMessage.substring(4);
                        logDAO.saveChatMessage(username, "ALL", msgContent);
                        broadcastMessage("MSG:" + username + ":" + msgContent);
                    }
                    // 2. Chat riêng: PRIVATE_MSG:receiver:nội dung
                    else if (clientMessage.startsWith("PRIVATE_MSG:")) {
                        String payload = clientMessage.substring(12);
                        int firstColon = payload.indexOf(':');
                        if (firstColon != -1) {
                            String receiver = payload.substring(0, firstColon).trim();
                            String privateContent = payload.substring(firstColon + 1);

                            logDAO.saveChatMessage(username, receiver, privateContent);

                            ClientHandler receiverClient = clients.get(receiver);
                            if (receiverClient != null) {
                                receiverClient.sendMessage("PRIVATE_MSG:" + username + ":" + privateContent);
                            }
                            sendMessage("PRIVATE_ECHO:" + receiver + ":" + privateContent);
                        }
                    }
                    // 3. Tải thêm tin nhắn cũ: LOAD_MORE:receiver:offset
                    else if (clientMessage.startsWith("LOAD_MORE:")) {
                        String payload = clientMessage.substring(10);
                        String[] parts = payload.split(":", 2);
                        if (parts.length == 2) {
                            String receiver = parts[0].trim();
                            try {
                                int offset = Integer.parseInt(parts[1].trim());
                                List<String> olderHistory = logDAO.getChatHistory(username, receiver, 50, offset);
                                for (String historyMsg : olderHistory) {
                                    sendMessage(historyMsg);
                                }
                                sendMessage("LOAD_MORE_DONE:" + receiver + ":" + olderHistory.size());
                            } catch (NumberFormatException nfe) {
                                System.err.println("Sai định dạng offset trong LOAD_MORE: " + payload);
                            }
                        }
                    }
                    // 4. Chuyển file hoàn tất: FILE_SUCCESS:receiver:filename:size
                    else if (clientMessage.startsWith("FILE_SUCCESS:")) {
                        String payload = clientMessage.substring(13);
                        String[] parts = payload.split(":", 3);
                        if (parts.length == 3) {
                            String receiver = parts[0].trim();
                            String fileName = parts[1].trim();
                            String fileSize = parts[2].trim();

                            logDAO.saveFileTransfer(username, receiver, fileName, fileSize, "SUCCESS");

                            String sysMsg = "[Hệ thống] Đã gửi tệp: " + fileName + " (" + fileSize + ")";
                            logDAO.saveChatMessage(username, receiver, sysMsg);

                            String notifyStr = "PRIVATE_MSG:" + username + ":" + sysMsg;
                            ClientHandler receiverClient = clients.get(receiver);
                            if (receiverClient != null) {
                                receiverClient.sendMessage(notifyStr);
                            }
                            sendMessage("PRIVATE_ECHO:" + receiver + ":" + sysMsg);
                        }
                    }
                    // 5. Đăng ký thông tin P2P: P2P_REGISTER:port
                    else if (clientMessage.startsWith("P2P_REGISTER:")) {
                        try {
                            int port = Integer.parseInt(clientMessage.substring(13).trim());
                            this.p2pPort = port;
                            this.p2pIp   = socket.getInetAddress().getHostAddress();
                            System.out.println("[P2P] " + username + " đăng ký P2P tại " + p2pIp + ":" + p2pPort);
                        } catch (NumberFormatException e) {
                            System.err.println("[P2P] P2P_REGISTER sai định dạng từ: " + username);
                        }
                    }
                    // 6. Yêu cầu lấy thông tin P2P của người nhận: GET_P2P_INFO:targetUsername
                    else if (clientMessage.startsWith("GET_P2P_INFO:")) {
                        String target = clientMessage.substring(13).trim();
                        ClientHandler targetHandler = clients.get(target);
                        if (targetHandler != null && targetHandler.p2pPort > 0 && targetHandler.p2pIp != null) {
                            sendMessage("P2P_INFO:" + target + ":" + targetHandler.p2pIp + ":" + targetHandler.p2pPort);
                        } else if (targetHandler == null) {
                            sendMessage("P2P_INFO_FAIL:" + target + ":Người dùng không online!");
                        } else {
                            sendMessage("P2P_INFO_FAIL:" + target + ":Người dùng chưa đăng ký cổng P2P.");
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("Mất kết nối với client: " + username + " (" + e.getMessage() + ")");
            } finally {
                closeConnection();
                if (username != null) {
                    clients.remove(username);
                    userDAO.updateStatus(username, "OFFLINE");
                    logDAO.saveActivityLog(username, "LOGOUT", "Người dùng đã ngắt kết nối / Đăng xuất.");
                    broadcastMessage("SYSTEM:" + username + " đã rời phòng chat.");
                    broadcastUserList();
                }
            }
        }

        public void sendMessage(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        public void closeConnection() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("Lỗi đóng kết nối client: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        int defaultPort = 8888;
        ChatServer server = new ChatServer(defaultPort);
        server.start();
    }
}
