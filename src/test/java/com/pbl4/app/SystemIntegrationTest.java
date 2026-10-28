package com.pbl4.app;

import com.pbl4.app.dao.DatabaseConnection;
import com.pbl4.app.dao.LogDAO;
import com.pbl4.app.dao.UserDAO;
import com.pbl4.app.model.ActivityLog;
import com.pbl4.app.model.ChatMessageRecord;
import com.pbl4.app.model.FileTransferRecord;
import com.pbl4.app.model.User;
import com.pbl4.app.network.ChatServer;
import com.pbl4.app.network.P2PFileTransfer;
import com.pbl4.app.security.AESCrypto;
import com.pbl4.app.security.PasswordHelper;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SystemIntegrationTest {

    private static final int TEST_SERVER_PORT = 18889;
    private static ChatServer server;
    private static Thread serverThread;

    private static UserDAO userDAO;
    private static LogDAO logDAO;

    private static final String USER_ALICE = "test_alice_" + System.currentTimeMillis();
    private static final String USER_BOB = "test_bob_" + System.currentTimeMillis();
    private static final String PASS = "Pass@123456";

    @BeforeAll
    public static void setUpAll() throws Exception {
        boolean dbOk = DatabaseConnection.initializeDatabase();
        assertTrue(dbOk, "Database MySQL pbl4_db phải khởi tạo thành công");

        userDAO = new UserDAO();
        logDAO = new LogDAO();

        // Khởi chạy ChatServer trên cổng TEST_SERVER_PORT
        server = new ChatServer(TEST_SERVER_PORT);
        serverThread = new Thread(() -> server.start(), "IntegrationTest-ChatServer");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(600);
    }

    @AfterAll
    public static void tearDownAll() {
        if (server != null) {
            server.stop();
        }
    }

    // =========================================================================
    // 1. KIỂM TRA BẢO MẬT: MÃ HÓA AES & BĂM MẬT KHẨU SHA-256
    // =========================================================================
    @Test
    @Order(1)
    public void testSecurityAndCrypto() {
        System.out.println("===> [TEST 1] Kiểm tra Mã hóa AES-128 & Băm mật khẩu SHA-256");

        // 1.1 Hash password
        String rawPass = "SecretPassword!#123";
        String hashed1 = PasswordHelper.hashPassword(rawPass);
        assertNotNull(hashed1);
        assertEquals(64, hashed1.length(), "SHA-256 hash phải có độ dài đúng 64 ký tự hex");
        assertTrue(PasswordHelper.verifyPassword(rawPass, hashed1), "Verify mật khẩu đúng phải trả về true");
        assertFalse(PasswordHelper.verifyPassword("WrongPassword", hashed1), "Verify mật khẩu sai phải trả về false");

        // 1.2 AES Crypto với tiếng Việt và ký tự đặc biệt
        String secretKey = "CustomKey_PBL4_Test";
        String originalText = "Xin chào Hệ thống PBL4 - Bảo mật Chat & File Transfer 🌟 Tiếng Việt có dấu!";
        String encrypted = AESCrypto.encrypt(originalText, secretKey);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);

        String decrypted = AESCrypto.decrypt(encrypted, secretKey);
        assertEquals(originalText, decrypted, "Giải mã AES phải khớp 100% với chuỗi ban đầu");

        // Giải mã với sai key
        String wrongDecrypted = AESCrypto.decrypt(encrypted, "WrongKey");
        assertNull(wrongDecrypted, "Giải mã sai key phải trả về null");
    }

    // =========================================================================
    // 2. KIỂM TRA ĐĂNG KÝ VÀ ĐĂNG NHẬP QUA SOCKET SERVER
    // =========================================================================
    @Test
    @Order(2)
    public void testRegisterAndLoginViaSocket() throws Exception {
        System.out.println("===> [TEST 2] Kiểm tra Đăng ký & Đăng nhập qua Socket Server");

        // 2.1 Kết nối socket kiểm tra Đăng ký USER_ALICE
        try (Socket socket = new Socket("127.0.0.1", TEST_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Đăng ký USER_ALICE
            writer.println("REGISTER:" + USER_ALICE + ":" + PASS);
            String regResp = reader.readLine();
            assertEquals("REGISTER_SUCCESS", regResp, "Đăng ký USER_ALICE phải thành công");

            // Đăng ký lại cùng tên -> phải thất bại
            writer.println("REGISTER:" + USER_ALICE + ":" + PASS);
            String regDupResp = reader.readLine();
            assertTrue(regDupResp.startsWith("REGISTER_FAIL"), "Đăng ký trùng phải trả về REGISTER_FAIL");

            // Đăng nhập với sai mật khẩu
            writer.println("LOGIN:" + USER_ALICE + ":wrong_pass");
            String loginFailResp = reader.readLine();
            assertTrue(loginFailResp.startsWith("LOGIN_FAIL"), "Sai mật khẩu phải trả về LOGIN_FAIL");

            // Đăng nhập thành công với USER_ALICE
            writer.println("LOGIN:" + USER_ALICE + ":" + PASS);
            String loginResp = reader.readLine();
            assertEquals("LOGIN_SUCCESS:USER", loginResp, "Đăng nhập đúng phải trả về LOGIN_SUCCESS:USER");
        }

        // Đăng ký luôn USER_BOB qua UserDAO để dùng cho các test tiếp theo
        assertTrue(userDAO.register(USER_BOB, PASS), "Đăng ký USER_BOB qua UserDAO phải thành công");
    }

    // =========================================================================
    // 3. KIỂM TRA CHỐNG ĐĂNG NHẬP ĐỒNG THỜI (DUPLICATE SESSION)
    // =========================================================================
    @Test
    @Order(3)
    public void testDuplicateSessionPrevention() throws Exception {
        System.out.println("===> [TEST 3] Kiểm tra Chống đăng nhập đồng thời 1 tài khoản trên 2 client");

        Socket sock1 = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader reader1 = new BufferedReader(new InputStreamReader(sock1.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer1 = new PrintWriter(new OutputStreamWriter(sock1.getOutputStream(), StandardCharsets.UTF_8), true);

        // Client 1 đăng nhập USER_ALICE
        writer1.println("LOGIN:" + USER_ALICE + ":" + PASS);
        String resp1 = reader1.readLine();
        assertEquals("LOGIN_SUCCESS:USER", resp1);

        // Client 2 cố gắng đăng nhập cùng USER_ALICE
        Socket sock2 = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader reader2 = new BufferedReader(new InputStreamReader(sock2.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer2 = new PrintWriter(new OutputStreamWriter(sock2.getOutputStream(), StandardCharsets.UTF_8), true);

        writer2.println("LOGIN:" + USER_ALICE + ":" + PASS);
        String resp2 = reader2.readLine();
        assertTrue(resp2.contains("Tài khoản đang đăng nhập ở một phiên khác"), "Client 2 phải bị từ chối đăng nhập");

        // Đóng cả 2 socket
        sock1.close();
        sock2.close();
        Thread.sleep(300);
    }

    // =========================================================================
    // 4. KIỂM TRA TIN NHẮN NHÓM (CHAT CHUNG) VÀ LƯU VÀO DATABASE
    // =========================================================================
    @Test
    @Order(4)
    public void testGroupChatBroadcastAndPersistence() throws Exception {
        System.out.println("===> [TEST 4] Kiểm tra Chat nhóm, broadcast và lưu CSDL");

        Socket aliceSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader aliceReader = new BufferedReader(new InputStreamReader(aliceSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter aliceWriter = new PrintWriter(new OutputStreamWriter(aliceSock.getOutputStream(), StandardCharsets.UTF_8), true);

        aliceWriter.println("LOGIN:" + USER_ALICE + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", aliceReader.readLine());

        Socket bobSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader bobReader = new BufferedReader(new InputStreamReader(bobSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter bobWriter = new PrintWriter(new OutputStreamWriter(bobSock.getOutputStream(), StandardCharsets.UTF_8), true);

        bobWriter.println("LOGIN:" + USER_BOB + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", bobReader.readLine());

        // Alice gửi tin nhắn nhóm
        String testMsg = "Xin chào toàn thể phòng chat PBL4!";
        aliceWriter.println("MSG:" + testMsg);

        // Bob đọc tin nhắn nhóm
        boolean bobReceivedMsg = false;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 3000) {
            if (bobReader.ready()) {
                String line = bobReader.readLine();
                if (line.equals("MSG:" + USER_ALICE + ":" + testMsg)) {
                    bobReceivedMsg = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(bobReceivedMsg, "Bob phải nhận được tin nhắn nhóm của Alice");

        // Kiểm tra lưu trong CSDL pbl4_db bảng chat_messages
        List<ChatMessageRecord> allChats = logDAO.getAllChatMessages();
        boolean foundInDb = allChats.stream().anyMatch(c ->
                c.getSender().equals(USER_ALICE) &&
                c.getReceiver().equals("ALL") &&
                c.getMessage().equals(testMsg)
        );
        assertTrue(foundInDb, "Tin nhắn nhóm phải được lưu trữ vào bảng chat_messages với receiver='ALL'");

        aliceSock.close();
        bobSock.close();
        Thread.sleep(300);
    }

    // =========================================================================
    // 5. KIỂM TRA TIN NHẮN RIÊNG (PRIVATE CHAT) VÀ PRIVATE ECHO
    // =========================================================================
    @Test
    @Order(5)
    public void testPrivateChatAndEcho() throws Exception {
        System.out.println("===> [TEST 5] Kiểm tra Chat riêng (Private Chat), Echo và lưu CSDL");

        Socket aliceSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader aliceReader = new BufferedReader(new InputStreamReader(aliceSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter aliceWriter = new PrintWriter(new OutputStreamWriter(aliceSock.getOutputStream(), StandardCharsets.UTF_8), true);

        aliceWriter.println("LOGIN:" + USER_ALICE + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", aliceReader.readLine());

        Socket bobSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader bobReader = new BufferedReader(new InputStreamReader(bobSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter bobWriter = new PrintWriter(new OutputStreamWriter(bobSock.getOutputStream(), StandardCharsets.UTF_8), true);

        bobWriter.println("LOGIN:" + USER_BOB + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", bobReader.readLine());

        // Alice gửi tin nhắn riêng cho Bob
        String privateMsg = "Nội dung bí mật chỉ dành riêng cho Bob 🔒";
        aliceWriter.println("PRIVATE_MSG:" + USER_BOB + ":" + privateMsg);

        // Kiểm tra Alice nhận PRIVATE_ECHO
        boolean aliceGotEcho = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (aliceReader.ready()) {
                String line = aliceReader.readLine();
                if (line.equals("PRIVATE_ECHO:" + USER_BOB + ":" + privateMsg)) {
                    aliceGotEcho = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(aliceGotEcho, "Alice phải nhận được PRIVATE_ECHO xác nhận tin nhắn riêng");

        // Kiểm tra Bob nhận được PRIVATE_MSG
        boolean bobGotPrivate = false;
        start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (bobReader.ready()) {
                String line = bobReader.readLine();
                if (line.equals("PRIVATE_MSG:" + USER_ALICE + ":" + privateMsg)) {
                    bobGotPrivate = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(bobGotPrivate, "Bob phải nhận được PRIVATE_MSG từ Alice");

        // Kiểm tra CSDL
        List<ChatMessageRecord> allChats = logDAO.getAllChatMessages();
        boolean foundPrivate = allChats.stream().anyMatch(c ->
                c.getSender().equals(USER_ALICE) &&
                c.getReceiver().equals(USER_BOB) &&
                c.getMessage().equals(privateMsg)
        );
        assertTrue(foundPrivate, "Tin nhắn riêng phải được lưu trong bảng chat_messages với sender=Alice, receiver=Bob");

        aliceSock.close();
        bobSock.close();
        Thread.sleep(300);
    }

    // =========================================================================
    // 6. KIỂM TRA PHÂN TRANG LỊCH SỬ CHAT (LAZY LOADING)
    // =========================================================================
    @Test
    @Order(6)
    public void testLazyLoadingHistory() throws Exception {
        System.out.println("===> [TEST 6] Kiểm tra Phân trang lịch sử tin nhắn (Lazy Loading: LOAD_MORE)");

        Socket aliceSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader aliceReader = new BufferedReader(new InputStreamReader(aliceSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter aliceWriter = new PrintWriter(new OutputStreamWriter(aliceSock.getOutputStream(), StandardCharsets.UTF_8), true);

        aliceWriter.println("LOGIN:" + USER_ALICE + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", aliceReader.readLine());

        // Yêu cầu tải thêm tin nhắn nhóm với offset = 0
        aliceWriter.println("LOAD_MORE:ALL:0");

        boolean gotDone = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (aliceReader.ready()) {
                String line = aliceReader.readLine();
                if (line.startsWith("LOAD_MORE_DONE:ALL:")) {
                    gotDone = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(gotDone, "Server phải trả về phản hồi LOAD_MORE_DONE cho client");

        aliceSock.close();
        Thread.sleep(300);
    }

    // =========================================================================
    // 7. KIỂM TRA TRUYỀN FILE P2P & ĐĂNG KÝ CỔNG P2P
    // =========================================================================
    @Test
    @Order(7)
    public void testP2PRegistrationAndFileTransfer() throws Exception {
        System.out.println("===> [TEST 7] Kiểm tra Đăng ký P2P, Lấy thông tin P2P & Truyền file P2P trực tiếp");

        Socket bobSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader bobReader = new BufferedReader(new InputStreamReader(bobSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter bobWriter = new PrintWriter(new OutputStreamWriter(bobSock.getOutputStream(), StandardCharsets.UTF_8), true);

        bobWriter.println("LOGIN:" + USER_BOB + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", bobReader.readLine());

        // Bob khởi động receiver trên cổng tự do
        File bobSaveDir = Files.createTempDirectory("bob_p2p_receive_").toFile();
        bobSaveDir.deleteOnExit();

        CountDownLatch receiveLatch = new CountDownLatch(1);
        AtomicBoolean receiveSuccess = new AtomicBoolean(false);

        P2PFileTransfer bobP2P = new P2PFileTransfer();
        bobP2P.setServerNotifyWriter(bobWriter);
        int bobPort = bobP2P.startReceiver(19220, bobSaveDir, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {
                receiveSuccess.set(true);
                receiveLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                System.err.println("Bob P2P Error: " + errorMessage);
                receiveLatch.countDown();
            }
        });
        assertTrue(bobPort > 0, "Cổng P2P của Bob phải hợp lệ");

        // Bob đăng ký thông tin P2P với Server
        bobWriter.println("P2P_REGISTER:" + bobPort);
        Thread.sleep(200);

        // Alice kết nối và truy vấn P2P của Bob
        Socket aliceSock = new Socket("127.0.0.1", TEST_SERVER_PORT);
        BufferedReader aliceReader = new BufferedReader(new InputStreamReader(aliceSock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter aliceWriter = new PrintWriter(new OutputStreamWriter(aliceSock.getOutputStream(), StandardCharsets.UTF_8), true);

        aliceWriter.println("LOGIN:" + USER_ALICE + ":" + PASS);
        assertEquals("LOGIN_SUCCESS:USER", aliceReader.readLine());

        aliceWriter.println("GET_P2P_INFO:" + USER_BOB);
        String p2pInfoResp = null;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (aliceReader.ready()) {
                String line = aliceReader.readLine();
                if (line.startsWith("P2P_INFO:" + USER_BOB)) {
                    p2pInfoResp = line;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertNotNull(p2pInfoResp, "Alice phải nhận được P2P_INFO của Bob");
        String[] parts = p2pInfoResp.split(":");
        assertEquals(USER_BOB, parts[1]);
        int queriedPort = Integer.parseInt(parts[3]);
        assertEquals(bobPort, queriedPort, "Port nhận được từ server phải khớp với port Bob đã đăng ký");

        // Alice gửi file thực tế sang Bob qua P2P
        File sampleFile = File.createTempFile("sample_test_transfer_", ".txt");
        sampleFile.deleteOnExit();
        String content = "Chào Bob, đây là file truyền ngang hàng P2P không qua lưu trữ trung gian server! Mã hash chính xác 100%.";
        Files.writeString(sampleFile.toPath(), content, StandardCharsets.UTF_8);

        CountDownLatch sendLatch = new CountDownLatch(1);
        AtomicBoolean sendSuccess = new AtomicBoolean(false);

        P2PFileTransfer aliceP2P = new P2PFileTransfer();
        aliceP2P.setServerNotifyWriter(aliceWriter);
        aliceP2P.sendFile("127.0.0.1", bobPort, sampleFile, USER_BOB, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {
                sendSuccess.set(true);
                sendLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                System.err.println("Alice Send Error: " + errorMessage);
                sendLatch.countDown();
            }
        });

        assertTrue(sendLatch.await(5, TimeUnit.SECONDS), "Gửi file P2P phải hoàn thành trong 5s");
        assertTrue(sendSuccess.get(), "Alice gửi file P2P thành công");
        assertTrue(receiveLatch.await(5, TimeUnit.SECONDS), "Bob nhận file P2P phải hoàn thành trong 5s");
        assertTrue(receiveSuccess.get(), "Bob nhận file P2P thành công");

        // Kiểm tra nội dung file tại thư mục nhận của Bob
        File receivedFile = new File(bobSaveDir, sampleFile.getName());
        assertTrue(receivedFile.exists(), "File nhận được phải tồn tại trên đĩa");
        String receivedContent = Files.readString(receivedFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(content, receivedContent, "Nội dung file nhận được phải hoàn toàn giống file gửi");

        // Alice gửi thông báo FILE_SUCCESS lên ChatServer để ghi nhận lịch sử vào CSDL
        aliceWriter.println("FILE_SUCCESS:" + USER_BOB + ":" + sampleFile.getName() + ":" + sampleFile.length() + " B");
        Thread.sleep(300);

        // Kiểm tra lưu trong CSDL bảng file_transfers
        List<FileTransferRecord> transfers = logDAO.getAllFileTransfers();
        boolean foundTransfer = transfers.stream().anyMatch(t ->
                t.getSender().equals(USER_ALICE) &&
                t.getReceiver().equals(USER_BOB) &&
                t.getFileName().equals(sampleFile.getName())
        );
        assertTrue(foundTransfer, "Lịch sử truyền file P2P phải được ghi vào CSDL bảng file_transfers");

        bobP2P.stopReceiver();
        bobSock.close();
        aliceSock.close();
        Thread.sleep(300);
    }

    // =========================================================================
    // 8. KIỂM TRA CÁC CHỨC NĂNG DÀNH CHO ADMIN & QUẢN TRỊ CSDL
    // =========================================================================
    @Test
    @Order(8)
    public void testAdminFeaturesAndManagement() {
        System.out.println("===> [TEST 8] Kiểm tra Chức năng Admin, Phân quyền Role & Quản trị CSDL");

        // 8.1 Kiểm tra vai trò của tài khoản admin mặc định
        assertEquals("ADMIN", userDAO.getUserRole("admin"), "Tài khoản admin phải có quyền ADMIN");

        // 8.2 Thêm user mới bằng chức năng Admin
        String adminCreatedUser = "admin_sub_user_" + System.currentTimeMillis();
        boolean added = userDAO.addUser(adminCreatedUser, "123456", "USER");
        assertTrue(added, "Admin thêm user mới phải thành công");
        assertTrue(userDAO.userExists(adminCreatedUser));

        // 8.3 Lấy danh sách toàn bộ Users
        List<User> userList = userDAO.getAllUsers();
        assertNotNull(userList);
        assertTrue(userList.size() >= 3, "Danh sách users phải có ít nhất admin, alice, bob");
        User foundUser = userList.stream()
                .filter(u -> u.getUsername().equals(adminCreatedUser))
                .findFirst()
                .orElse(null);
        assertNotNull(foundUser, "User mới tạo bởi admin phải có trong danh sách");

        // 8.4 Đổi vai trò (Role) từ USER lên ADMIN
        boolean roleUpdated = userDAO.updateUserRoleAndStatus(foundUser.getId(), "ADMIN", "ONLINE");
        assertTrue(roleUpdated, "Cập nhật quyền ADMIN cho user phải thành công");
        assertEquals("ADMIN", userDAO.getUserRole(adminCreatedUser), "Quyền sau cập nhật phải là ADMIN");

        // 8.5 Đặt lại mật khẩu (Reset Password)
        String newPass = "NewSecurePass!@#";
        boolean passReset = userDAO.resetPassword(foundUser.getId(), newPass);
        assertTrue(passReset, "Admin đặt lại mật khẩu phải thành công");
        assertTrue(userDAO.login(adminCreatedUser, newPass), "Đăng nhập bằng mật khẩu mới phải thành công");

        // 8.6 Xóa user khỏi hệ thống
        boolean deleted = userDAO.deleteUser(foundUser.getId());
        assertTrue(deleted, "Admin xóa user phải thành công");
        assertFalse(userDAO.userExists(adminCreatedUser), "Sau khi xóa, userExists phải trả về false");

        // 8.7 Kiểm tra nhật ký hoạt động (Activity Logs)
        List<ActivityLog> logs = logDAO.getAllActivityLogs();
        assertNotNull(logs);
        assertFalse(logs.isEmpty(), "Bảng activity_logs phải có dữ liệu ghi nhận hoạt động");
    }
}
