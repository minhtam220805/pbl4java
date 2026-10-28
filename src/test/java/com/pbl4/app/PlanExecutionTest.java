package com.pbl4.app;

import com.pbl4.app.dao.DatabaseConnection;
import com.pbl4.app.dao.LogDAO;
import com.pbl4.app.dao.UserDAO;
import com.pbl4.app.model.ActivityLog;
import com.pbl4.app.model.ChatMessageRecord;
import com.pbl4.app.model.FileMetadata;
import com.pbl4.app.model.FileTransferRecord;
import com.pbl4.app.model.User;
import com.pbl4.app.network.ChatServer;
import com.pbl4.app.network.P2PFileTransfer;
import com.pbl4.app.security.AESCrypto;
import com.pbl4.app.security.PasswordHelper;
import com.pbl4.app.util.FileHelper;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlanExecutionTest: Lớp kiểm thử tự động toàn diện theo đúng kế hoạch test_plan_pbl4.md
 * Bao phủ đầy đủ 22 kịch bản kiểm thử:
 * - TC-AUTH-01 -> TC-AUTH-06
 * - TC-CHAT-01 -> TC-CHAT-05
 * - TC-P2P-01  -> TC-P2P-05
 * - TC-ADM-01  -> TC-ADM-07
 * - TC-SET-01  -> TC-SET-02
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlanExecutionTest {

    private static final int PLAN_SERVER_PORT = 18777;
    private static ChatServer server;
    private static Thread serverThread;

    private static UserDAO userDAO;
    private static LogDAO logDAO;

    private static final String ALICE_USER = "plan_alice_" + System.currentTimeMillis();
    private static final String BOB_USER = "plan_bob_" + System.currentTimeMillis();
    private static final String TEST_PASSWORD = "Password@123";

    @BeforeAll
    public static void setUpSuite() throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("   BẮT ĐẦU THỰC HIỆN KIỂM THỬ THEO TEST PLAN PBL4      ");
        System.out.println("=======================================================\n");

        boolean dbOk = DatabaseConnection.initializeDatabase();
        assertTrue(dbOk, "Khởi tạo CSDL MySQL pbl4_db phải thành công");

        userDAO = new UserDAO();
        logDAO = new LogDAO();

        // Khởi động ChatServer cho toàn bộ suite
        server = new ChatServer(PLAN_SERVER_PORT);
        serverThread = new Thread(() -> server.start(), "PlanExecution-Server");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(600);

        // Đăng ký trước 2 tài khoản Alice và Bob phục vụ kiểm thử
        userDAO.register(ALICE_USER, TEST_PASSWORD);
        userDAO.register(BOB_USER, TEST_PASSWORD);
    }

    @AfterAll
    public static void tearDownSuite() {
        if (server != null) {
            server.stop();
        }
        System.out.println("\n=======================================================");
        System.out.println("   HOÀN TẤT THỰC HIỆN KIỂM THỬ THEO TEST PLAN PBL4     ");
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // PHÂN HỆ 1: XÁC THỰC & PHÂN QUYỀN (TC-AUTH-01 -> TC-AUTH-06)
    // =========================================================================

    @Test
    @Order(1)
    public void test_TC_AUTH_01_LoginValidUser() throws Exception {
        System.out.print("[TEST] TC-AUTH-01: Đăng nhập tài khoản hợp lệ (USER) -> ");
        try (Socket socket = new Socket("127.0.0.1", PLAN_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
            String response = reader.readLine();
            assertEquals("LOGIN_SUCCESS:USER", response, "Đăng nhập user hợp lệ phải trả về LOGIN_SUCCESS:USER");
            System.out.println("PASS");
        }
    }

    @Test
    @Order(2)
    public void test_TC_AUTH_02_LoginValidAdmin() throws Exception {
        System.out.print("[TEST] TC-AUTH-02: Đăng nhập tài khoản hợp lệ (ADMIN) -> ");
        try (Socket socket = new Socket("127.0.0.1", PLAN_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println("LOGIN:admin:123456");
            String response = reader.readLine();
            assertEquals("LOGIN_SUCCESS:ADMIN", response, "Đăng nhập admin mặc định phải trả về LOGIN_SUCCESS:ADMIN");
            System.out.println("PASS");
        }
    }

    @Test
    @Order(3)
    public void test_TC_AUTH_03_LoginInvalidPassword() throws Exception {
        System.out.print("[TEST] TC-AUTH-03: Đăng nhập sai mật khẩu -> ");
        try (Socket socket = new Socket("127.0.0.1", PLAN_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println("LOGIN:" + ALICE_USER + ":incorrect_pass_999");
            String response = reader.readLine();
            assertTrue(response.startsWith("LOGIN_FAIL:"), "Mật khẩu sai phải trả về thông báo lỗi LOGIN_FAIL");
            System.out.println("PASS");
        }
    }

    @Test
    @Order(4)
    public void test_TC_AUTH_04_RegisterNewUser() throws Exception {
        System.out.print("[TEST] TC-AUTH-04: Đăng ký tài khoản mới thành công -> ");
        String newUser = "plan_new_" + System.currentTimeMillis();
        try (Socket socket = new Socket("127.0.0.1", PLAN_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println("REGISTER:" + newUser + ":Pass@12345");
            String response = reader.readLine();
            assertEquals("REGISTER_SUCCESS", response, "Đăng ký mới phải trả về REGISTER_SUCCESS");
            assertTrue(userDAO.userExists(newUser), "Người dùng mới phải tồn tại trong CSDL MySQL");
            System.out.println("PASS");
        }
    }

    @Test
    @Order(5)
    public void test_TC_AUTH_05_RegisterDuplicateUser() throws Exception {
        System.out.print("[TEST] TC-AUTH-05: Đăng ký với tên tài khoản đã tồn tại -> ");
        try (Socket socket = new Socket("127.0.0.1", PLAN_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Cố gắng đăng ký lại tên ALICE_USER đã có
            writer.println("REGISTER:" + ALICE_USER + ":AnotherPassword");
            String response = reader.readLine();
            assertTrue(response.startsWith("REGISTER_FAIL:"), "Không được phép đăng ký trùng tài khoản đã có");
            System.out.println("PASS");
        }
    }

    @Test
    @Order(6)
    public void test_TC_AUTH_06_DuplicateSessionPrevention() throws Exception {
        System.out.print("[TEST] TC-AUTH-06: Chống đăng nhập đồng thời 1 tài khoản (Single Session) -> ");
        Socket client1 = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader reader1 = new BufferedReader(new InputStreamReader(client1.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer1 = new PrintWriter(new OutputStreamWriter(client1.getOutputStream(), StandardCharsets.UTF_8), true);

        // Client 1 đăng nhập ALICE_USER
        writer1.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        assertEquals("LOGIN_SUCCESS:USER", reader1.readLine());

        // Client 2 cố gắng đăng nhập cùng ALICE_USER
        Socket client2 = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader reader2 = new BufferedReader(new InputStreamReader(client2.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer2 = new PrintWriter(new OutputStreamWriter(client2.getOutputStream(), StandardCharsets.UTF_8), true);

        writer2.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        String response2 = reader2.readLine();
        assertTrue(response2.contains("Tài khoản đang đăng nhập ở một phiên khác"), "Client 2 phải bị từ chối kết nối");

        client1.close();
        client2.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    // =========================================================================
    // PHÂN HỆ 2: CHAT TRỰC TUYẾN (TC-CHAT-01 -> TC-CHAT-05)
    // =========================================================================

    @Test
    @Order(7)
    public void test_TC_CHAT_01_OnlineStatusTracking() throws Exception {
        System.out.print("[TEST] TC-CHAT-01: Cập nhật trạng thái người dùng Online/Offline -> ");
        Socket clientAlice = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader readerAlice = new BufferedReader(new InputStreamReader(clientAlice.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writerAlice = new PrintWriter(new OutputStreamWriter(clientAlice.getOutputStream(), StandardCharsets.UTF_8), true);
        writerAlice.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        readerAlice.readLine();

        // Bob đăng nhập
        Socket clientBob = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader readerBob = new BufferedReader(new InputStreamReader(clientBob.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writerBob = new PrintWriter(new OutputStreamWriter(clientBob.getOutputStream(), StandardCharsets.UTF_8), true);
        writerBob.println("LOGIN:" + BOB_USER + ":" + TEST_PASSWORD);
        readerBob.readLine();

        // Alice kiểm tra nhận được thông báo Bob online
        boolean gotOnlineNotification = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (readerAlice.ready()) {
                String line = readerAlice.readLine();
                if (line.contains("USER_ONLINE:" + BOB_USER) || line.contains(BOB_USER)) {
                    gotOnlineNotification = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(gotOnlineNotification, "Alice phải nhận thông tin trạng thái Online của Bob");

        clientAlice.close();
        clientBob.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    @Test
    @Order(8)
    public void test_TC_CHAT_02_GroupChatBroadcastAndDb() throws Exception {
        System.out.print("[TEST] TC-CHAT-02: Chat Nhóm (Broadcast 'ALL') & Lưu CSDL -> ");
        Socket sockAlice = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rAlice = new BufferedReader(new InputStreamReader(sockAlice.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wAlice = new PrintWriter(new OutputStreamWriter(sockAlice.getOutputStream(), StandardCharsets.UTF_8), true);
        wAlice.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        rAlice.readLine();

        Socket sockBob = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rBob = new BufferedReader(new InputStreamReader(sockBob.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wBob = new PrintWriter(new OutputStreamWriter(sockBob.getOutputStream(), StandardCharsets.UTF_8), true);
        wBob.println("LOGIN:" + BOB_USER + ":" + TEST_PASSWORD);
        rBob.readLine();

        // Alice gửi tin nhắn nhóm
        String groupMsg = "Chào cả phòng chat PBL4 [Test Plan] 🌍";
        wAlice.println("MSG:" + groupMsg);

        // Bob kiểm tra nhận được
        boolean bobReceived = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (rBob.ready()) {
                String line = rBob.readLine();
                if (line.equals("MSG:" + ALICE_USER + ":" + groupMsg)) {
                    bobReceived = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(bobReceived, "Bob phải nhận được broadcast tin nhắn nhóm từ Alice");

        // Kiểm tra lưu CSDL
        List<ChatMessageRecord> chats = logDAO.getAllChatMessages();
        boolean foundInDb = chats.stream().anyMatch(c ->
                c.getSender().equals(ALICE_USER) &&
                c.getReceiver().equals("ALL") &&
                c.getMessage().equals(groupMsg)
        );
        assertTrue(foundInDb, "Tin nhắn nhóm phải được lưu vào CSDL bảng chat_messages");

        sockAlice.close();
        sockBob.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    @Test
    @Order(9)
    public void test_TC_CHAT_03_PrivateChatAndEchoAndDb() throws Exception {
        System.out.print("[TEST] TC-CHAT-03: Chat Riêng 1-1, Private Echo & Lưu CSDL -> ");
        Socket sockAlice = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rAlice = new BufferedReader(new InputStreamReader(sockAlice.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wAlice = new PrintWriter(new OutputStreamWriter(sockAlice.getOutputStream(), StandardCharsets.UTF_8), true);
        wAlice.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        rAlice.readLine();

        Socket sockBob = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rBob = new BufferedReader(new InputStreamReader(sockBob.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wBob = new PrintWriter(new OutputStreamWriter(sockBob.getOutputStream(), StandardCharsets.UTF_8), true);
        wBob.println("LOGIN:" + BOB_USER + ":" + TEST_PASSWORD);
        rBob.readLine();

        // Alice gửi tin nhắn riêng cho Bob
        String privateMsg = "Nội dung riêng tư chỉ dành cho Bob 🔒 [Test Plan]";
        wAlice.println("PRIVATE_MSG:" + BOB_USER + ":" + privateMsg);

        // Alice nhận PRIVATE_ECHO
        boolean aliceGotEcho = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (rAlice.ready()) {
                String line = rAlice.readLine();
                if (line.equals("PRIVATE_ECHO:" + BOB_USER + ":" + privateMsg)) {
                    aliceGotEcho = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(aliceGotEcho, "Alice phải nhận được PRIVATE_ECHO");

        // Bob nhận PRIVATE_MSG
        boolean bobGotMsg = false;
        start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (rBob.ready()) {
                String line = rBob.readLine();
                if (line.equals("PRIVATE_MSG:" + ALICE_USER + ":" + privateMsg)) {
                    bobGotMsg = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(bobGotMsg, "Bob phải nhận được PRIVATE_MSG từ Alice");

        // Kiểm tra lưu CSDL
        List<ChatMessageRecord> chats = logDAO.getAllChatMessages();
        boolean foundPrivate = chats.stream().anyMatch(c ->
                c.getSender().equals(ALICE_USER) &&
                c.getReceiver().equals(BOB_USER) &&
                c.getMessage().equals(privateMsg)
        );
        assertTrue(foundPrivate, "Tin nhắn riêng phải được lưu trong bảng chat_messages");

        sockAlice.close();
        sockBob.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    @Test
    @Order(10)
    public void test_TC_CHAT_04_AESCrypto() {
        System.out.print("[TEST] TC-CHAT-04: Bảo mật tin nhắn với Mã hóa AES-128 -> ");
        String key = "PBL4_AES_KEY_TEST_2026";
        String plainText = "Bảo mật thông tin trao đổi P2P: Tiếng Việt có dấu, ký tự @#$%^&*() và emoji 🛡️💎";

        String cipherText = AESCrypto.encrypt(plainText, key);
        assertNotNull(cipherText);
        assertNotEquals(plainText, cipherText);

        String decrypted = AESCrypto.decrypt(cipherText, key);
        assertEquals(plainText, decrypted, "Giải mã AES phải trùng khớp 100% bản rõ ban đầu");

        // Giải mã với key sai
        assertNull(AESCrypto.decrypt(cipherText, "InvalidKey_XYZ"));
        System.out.println("PASS");
    }

    @Test
    @Order(11)
    public void test_TC_CHAT_05_LazyLoadingChatHistory() throws Exception {
        System.out.print("[TEST] TC-CHAT-05: Lazy Loading phân trang lịch sử tin nhắn (LOAD_MORE) -> ");
        Socket sock = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);

        writer.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        reader.readLine();

        writer.println("LOAD_MORE:ALL:0");
        boolean gotDone = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line.startsWith("LOAD_MORE_DONE:ALL:")) {
                    gotDone = true;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertTrue(gotDone, "Server phải trả về phản hồi LOAD_MORE_DONE");

        sock.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    // =========================================================================
    // PHÂN HỆ 3: TRUYỀN TỆP NGANG HÀNG P2P (TC-P2P-01 -> TC-P2P-05)
    // =========================================================================

    @Test
    @Order(12)
    public void test_TC_P2P_01_P2PRegistrationAndQuery() throws Exception {
        System.out.print("[TEST] TC-P2P-01: Đăng ký & Truy vấn cổng P2P qua Server -> ");
        Socket sockBob = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rBob = new BufferedReader(new InputStreamReader(sockBob.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wBob = new PrintWriter(new OutputStreamWriter(sockBob.getOutputStream(), StandardCharsets.UTF_8), true);
        wBob.println("LOGIN:" + BOB_USER + ":" + TEST_PASSWORD);
        rBob.readLine();

        // Bob đăng ký cổng P2P
        int testPort = 19888;
        wBob.println("P2P_REGISTER:" + testPort);
        Thread.sleep(200);

        // Alice truy vấn P2P của Bob
        Socket sockAlice = new Socket("127.0.0.1", PLAN_SERVER_PORT);
        BufferedReader rAlice = new BufferedReader(new InputStreamReader(sockAlice.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter wAlice = new PrintWriter(new OutputStreamWriter(sockAlice.getOutputStream(), StandardCharsets.UTF_8), true);
        wAlice.println("LOGIN:" + ALICE_USER + ":" + TEST_PASSWORD);
        rAlice.readLine();

        wAlice.println("GET_P2P_INFO:" + BOB_USER);
        String p2pInfo = null;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            if (rAlice.ready()) {
                String line = rAlice.readLine();
                if (line.startsWith("P2P_INFO:" + BOB_USER)) {
                    p2pInfo = line;
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }
        assertNotNull(p2pInfo, "Alice phải nhận được thông tin P2P của Bob");
        String[] parts = p2pInfo.split(":");
        assertEquals(BOB_USER, parts[1]);
        assertEquals(testPort, Integer.parseInt(parts[3]));

        sockBob.close();
        sockAlice.close();
        Thread.sleep(300);
        System.out.println("PASS");
    }

    @Test
    @Order(13)
    public void test_TC_P2P_02_P2PSingleFileTransfer() throws Exception {
        System.out.print("[TEST] TC-P2P-02: Truyền file P2P đơn lẻ & Ghi nhật ký vào CSDL -> ");
        // Tạo file nguồn tạm
        File tempSrc = File.createTempFile("p2p_single_", ".txt");
        tempSrc.deleteOnExit();
        String originalContent = "Dữ liệu truyền file kiểm thử tự động theo Test Plan PBL4. Nội dung nguyên vẹn 100%!";
        Files.writeString(tempSrc.toPath(), originalContent, StandardCharsets.UTF_8);

        // Thư mục đích nhận file
        File tempDestDir = Files.createTempDirectory("p2p_dest_").toFile();
        tempDestDir.deleteOnExit();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        // Khởi động P2P receiver
        P2PFileTransfer receiver = new P2PFileTransfer();
        int boundPort = receiver.startReceiver(19333, tempDestDir, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                latch.countDown();
            }
        });
        assertTrue(boundPort > 0);
        Thread.sleep(200);

        // Sender gửi file
        P2PFileTransfer sender = new P2PFileTransfer();
        CountDownLatch sendLatch = new CountDownLatch(1);
        AtomicBoolean sendOk = new AtomicBoolean(false);

        sender.sendFile("127.0.0.1", boundPort, tempSrc, BOB_USER, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {
                sendOk.set(true);
                sendLatch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                sendLatch.countDown();
            }
        });

        assertTrue(sendLatch.await(5, TimeUnit.SECONDS), "Sender gửi file phải xong trong 5s");
        assertTrue(sendOk.get());
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Receiver nhận file phải xong trong 5s");
        assertTrue(success.get());

        // Kiểm tra file nhận được
        File received = new File(tempDestDir, tempSrc.getName());
        assertTrue(received.exists(), "File nhận được phải tồn tại trên ổ đĩa");
        assertEquals(originalContent, Files.readString(received.toPath(), StandardCharsets.UTF_8));

        // Ghi nhật ký vào CSDL
        boolean saved = logDAO.saveFileTransfer(ALICE_USER, BOB_USER, tempSrc.getName(), tempSrc.length() + " B", "SUCCESS");
        assertTrue(saved, "Lịch sử file transfer phải được lưu vào CSDL");

        receiver.stopReceiver();
        System.out.println("PASS");
    }

    @Test
    @Order(14)
    public void test_TC_P2P_03_FileMetadataAndFormatting() {
        System.out.print("[TEST] TC-P2P-03: Nhận diện siêu dữ liệu tệp tin (FileMetadata) & Định dạng size -> ");
        FileMetadata meta = new FileMetadata("test_presentation.pdf", 10485760L, false);
        assertEquals("test_presentation.pdf", meta.getFileName());
        assertEquals(10485760L, meta.getFileSize());
        assertFalse(meta.isDirectory());

        String formattedSize = FileHelper.formatFileSize(10485760L);
        assertTrue(formattedSize.contains("MB") || formattedSize.contains("10"), "Định dạng kích thước phải ra đơn vị MB");
        System.out.println("PASS");
    }

    @Test
    @Order(15)
    public void test_TC_P2P_04_FolderTransferZipUnzip() throws Exception {
        System.out.print("[TEST] TC-P2P-04: Đóng gói và truyền thư mục (Zip/Unzip Folder) -> ");
        // Tạo cây thư mục tạm với các file con
        Path tempDir = Files.createTempDirectory("plan_folder_src_");
        File file1 = new File(tempDir.toFile(), "sub_file1.txt");
        File file2 = new File(tempDir.toFile(), "sub_file2.txt");
        Files.writeString(file1.toPath(), "Nội dung tệp con 1");
        Files.writeString(file2.toPath(), "Nội dung tệp con 2");

        // Nén thư mục
        File zipOutput = File.createTempFile("plan_zipped_", ".zip");
        zipOutput.deleteOnExit();
        FileHelper.zipDirectory(tempDir.toFile(), zipOutput);
        assertTrue(zipOutput.exists() && zipOutput.length() > 0, "File zip thư mục phải được tạo thành công");

        // Giải nén ra thư mục đích
        Path destDir = Files.createTempDirectory("plan_folder_dest_");
        FileHelper.unzip(zipOutput, destDir.toFile());

        File unzippedFile1 = new File(destDir.toFile(), "sub_file1.txt");
        File unzippedFile2 = new File(destDir.toFile(), "sub_file2.txt");
        assertTrue(unzippedFile1.exists(), "Tệp con 1 sau khi giải nén phải tồn tại");
        assertTrue(unzippedFile2.exists(), "Tệp con 2 sau khi giải nén phải tồn tại");
        assertEquals("Nội dung tệp con 1", Files.readString(unzippedFile1.toPath()));
        System.out.println("PASS");
    }

    @Test
    @Order(16)
    public void test_TC_P2P_05_P2PErrorHandlingOffline() throws Exception {
        System.out.print("[TEST] TC-P2P-05: Xử lý ngoại lệ P2P khi bên nhận Offline hoặc sai cổng -> ");
        File dummyFile = File.createTempFile("dummy_p2p_", ".txt");
        dummyFile.deleteOnExit();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean gotError = new AtomicBoolean(false);

        P2PFileTransfer p2p = new P2PFileTransfer();
        // Cố gắng gửi vào cổng 19998 không có ai lắng nghe
        p2p.sendFile("127.0.0.1", 19998, dummyFile, "offline_user", new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {}

            @Override
            public void onError(String errorMessage) {
                gotError.set(true);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback onError phải được gọi khi đối phương offline");
        assertTrue(gotError.get(), "Hệ thống phải bắt lỗi an toàn không làm crash socket");
        System.out.println("PASS");
    }

    // =========================================================================
    // PHÂN HỆ 4: BẢNG ĐIỀU KHIỂN QUẢN TRỊ ADMIN (TC-ADM-01 -> TC-ADM-07)
    // =========================================================================

    @Test
    @Order(17)
    public void test_TC_ADM_01_UserRoleAuthorization() {
        System.out.print("[TEST] TC-ADM-01: Phân quyền vai trò người dùng (RBAC Authorization) -> ");
        assertEquals("USER", userDAO.getUserRole(ALICE_USER));
        assertEquals("ADMIN", userDAO.getUserRole("admin"));
        System.out.println("PASS");
    }

    @Test
    @Order(18)
    public void test_TC_ADM_02_AdminAddNewUser() {
        System.out.print("[TEST] TC-ADM-02: Admin thêm người dùng mới -> ");
        String adminCreated = "plan_u_created_" + System.currentTimeMillis();
        boolean added = userDAO.addUser(adminCreated, "Pass12345", "USER");
        assertTrue(added, "Admin thêm user mới phải thành công");
        assertTrue(userDAO.userExists(adminCreated));
        System.out.println("PASS");
    }

    @Test
    @Order(19)
    public void test_TC_ADM_03_AdminChangeRole() {
        System.out.print("[TEST] TC-ADM-03: Admin thay đổi vai trò (USER -> ADMIN) -> ");
        String uName = "plan_role_test_" + System.currentTimeMillis();
        userDAO.addUser(uName, "Pass12345", "USER");
        User user = userDAO.getAllUsers().stream().filter(u -> u.getUsername().equals(uName)).findFirst().orElse(null);
        assertNotNull(user);

        boolean updated = userDAO.updateUserRoleAndStatus(user.getId(), "ADMIN", "ONLINE");
        assertTrue(updated, "Cập nhật role ADMIN phải thành công");
        assertEquals("ADMIN", userDAO.getUserRole(uName));
        System.out.println("PASS");
    }

    @Test
    @Order(20)
    public void test_TC_ADM_04_AdminResetPassword() {
        System.out.print("[TEST] TC-ADM-04: Admin đặt lại mật khẩu (Reset Password) -> ");
        String uName = "plan_reset_test_" + System.currentTimeMillis();
        userDAO.addUser(uName, "OldPass", "USER");
        User user = userDAO.getAllUsers().stream().filter(u -> u.getUsername().equals(uName)).findFirst().orElse(null);
        assertNotNull(user);

        String newPass = "NewResetPassword@789";
        boolean reset = userDAO.resetPassword(user.getId(), newPass);
        assertTrue(reset, "Admin reset mật khẩu phải thành công");
        assertTrue(userDAO.login(uName, newPass), "Đăng nhập với mật khẩu mới phải thành công");
        assertFalse(userDAO.login(uName, "OldPass"), "Mật khẩu cũ không còn hợp lệ");
        System.out.println("PASS");
    }

    @Test
    @Order(21)
    public void test_TC_ADM_05_AdminDeleteUser() {
        System.out.print("[TEST] TC-ADM-05: Admin xóa người dùng khỏi hệ thống -> ");
        String uName = "plan_del_test_" + System.currentTimeMillis();
        userDAO.addUser(uName, "Pass123", "USER");
        User user = userDAO.getAllUsers().stream().filter(u -> u.getUsername().equals(uName)).findFirst().orElse(null);
        assertNotNull(user);

        boolean deleted = userDAO.deleteUser(user.getId());
        assertTrue(deleted, "Xóa user phải thành công");
        assertFalse(userDAO.userExists(uName), "User đã xóa không còn tồn tại trong DB");
        System.out.println("PASS");
    }

    @Test
    @Order(22)
    public void test_TC_ADM_06_ActivityLogsAuditAndFilter() {
        System.out.print("[TEST] TC-ADM-06: Giám sát & Tra cứu Activity Logs -> ");
        logDAO.saveActivityLog(ALICE_USER, "TEST_ACTION", "Chi tiết hoạt động kiểm thử Test Plan");
        List<ActivityLog> logs = logDAO.getAllActivityLogs();
        assertNotNull(logs);
        assertFalse(logs.isEmpty());

        boolean found = logs.stream().anyMatch(l -> l.getUsername().equals(ALICE_USER) && l.getAction().equals("TEST_ACTION"));
        assertTrue(found, "Log hoạt động vừa lưu phải được tìm thấy");
        System.out.println("PASS");
    }

    @Test
    @Order(23)
    public void test_TC_ADM_07_ChatAndFileTransferAudit() {
        System.out.print("[TEST] TC-ADM-07: Tra cứu Lịch sử Tin nhắn & Lịch sử Truyền File -> ");
        List<ChatMessageRecord> chats = logDAO.getAllChatMessages();
        assertNotNull(chats);
        assertFalse(chats.isEmpty());

        List<FileTransferRecord> files = logDAO.getAllFileTransfers();
        assertNotNull(files);
        assertFalse(files.isEmpty());
        System.out.println("PASS");
    }

    // =========================================================================
    // PHÂN HỆ 5: CÀI ĐẶT HỆ THỐNG (TC-SET-01 -> TC-SET-02)
    // =========================================================================

    @Test
    @Order(24)
    public void test_TC_SET_01_SaveDirectorySetting() throws Exception {
        System.out.print("[TEST] TC-SET-01: Cấu hình thư mục lưu file nhận mặc định -> ");
        String testSaveDir = "D:\\PBL4_Test_Downloads";
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            }
        }
        props.setProperty("save.dir", testSaveDir);
        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            props.store(out, "PBL4 Test Config");
        }

        // Tải lại và kiểm tra
        Properties reloaded = new Properties();
        try (FileInputStream in = new FileInputStream("config.properties")) {
            reloaded.load(in);
        }
        assertEquals(testSaveDir, reloaded.getProperty("save.dir"), "Cấu hình save.dir phải được lưu chính xác");
        System.out.println("PASS");
    }

    @Test
    @Order(25)
    public void test_TC_SET_02_ServerConfigSetting() throws Exception {
        System.out.print("[TEST] TC-SET-02: Cấu hình IP và Port máy chủ -> ");
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            }
        }
        props.setProperty("server.ip", "127.0.0.1");
        props.setProperty("server.port", "8888");
        props.setProperty("p2p.port", "9999");
        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            props.store(out, "PBL4 Test Config");
        }

        Properties reloaded = new Properties();
        try (FileInputStream in = new FileInputStream("config.properties")) {
            reloaded.load(in);
        }
        assertEquals("127.0.0.1", reloaded.getProperty("server.ip"));
        assertEquals("8888", reloaded.getProperty("server.port"));
        assertEquals("9999", reloaded.getProperty("p2p.port"));
        System.out.println("PASS");
    }
}
