package com.pbl4.app;

import com.pbl4.app.network.ChatServer;
import com.pbl4.app.network.P2PFileTransfer;
import com.pbl4.app.security.AESCrypto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class P2PTransferTest {

    private static final int SERVER_PORT = 19999;
    private static ChatServer server;
    private static Thread serverThread;

    @BeforeAll
    public static void setUpServer() {
        server = new ChatServer(SERVER_PORT);
        serverThread = new Thread(() -> server.start(), "Test-ChatServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
    }

    @AfterAll
    public static void tearDownServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testAESEncryptionDecryption() {
        String secretKey = "PBL4_SECRET_KEY_2026";
        String originalMessage = "Xin chào các bạn, đây là tin nhắn bí mật có dấu Tiếng Việt và emoji 🎉🚀 12345!";

        String encrypted = AESCrypto.encrypt(originalMessage, secretKey);
        assertNotNull(encrypted, "Mã hóa AES không được trả về null");
        assertNotEquals(originalMessage, encrypted, "Bản mã phải khác bản rõ");

        String decrypted = AESCrypto.decrypt(encrypted, secretKey);
        assertEquals(originalMessage, decrypted, "Bản giải mã phải khớp 100% với bản rõ ban đầu");
    }

    @Test
    public void testP2PSingleFileTransfer() throws Exception {
        // Tạo file nguồn tạm
        File tempSrcFile = File.createTempFile("p2p_test_src_", ".txt");
        tempSrcFile.deleteOnExit();
        String fileContent = "Dữ liệu truyền file P2P qua DataInputStream/DataOutputStream. Test 100% chính xác!\n";
        Files.writeString(tempSrcFile.toPath(), fileContent);

        // Tạo thư mục đích tạm
        File tempDestDir = Files.createTempDirectory("p2p_test_dest_dir_").toFile();
        tempDestDir.deleteOnExit();

        P2PFileTransfer p2p = new P2PFileTransfer();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        // Bật receiver với cổng động
        int boundPort = p2p.startReceiver(19100, tempDestDir, new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                System.err.println("P2P Receive Error: " + errorMessage);
                latch.countDown();
            }
        });

        assertTrue(boundPort > 0, "Cổng bind P2P phải hợp lệ > 0");
        Thread.sleep(200);

        // Gửi file
        P2PFileTransfer senderP2P = new P2PFileTransfer();
        senderP2P.sendFile("127.0.0.1", boundPort, tempSrcFile, "receiver_user", new P2PFileTransfer.TransferListener() {
            @Override
            public void onProgress(double progress, long bytesTransferred, long totalBytes) {}

            @Override
            public void onSuccess(String message) {}

            @Override
            public void onError(String errorMessage) {
                System.err.println("P2P Send Error: " + errorMessage);
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Truyền file P2P phải hoàn tất trong vòng 5 giây");
        assertTrue(success.get(), "Trạng thái nhận file phải thành công");

        File receivedFile = new File(tempDestDir, tempSrcFile.getName());
        assertTrue(receivedFile.exists(), "File nhận được phải tồn tại trên ổ cứng");
        assertEquals(tempSrcFile.length(), receivedFile.length(), "Kích thước file gửi và nhận phải khớp hoàn toàn");
        assertEquals(fileContent, Files.readString(receivedFile.toPath()), "Nội dung file nhận phải nguyên vẹn");

        p2p.stopReceiver();
    }

    @Test
    public void testP2PExchangeOverServer() throws Exception {
        // Client A login
        Socket socketA = new Socket("127.0.0.1", SERVER_PORT);
        PrintWriter writerA = new PrintWriter(socketA.getOutputStream(), true);
        BufferedReader readerA = new BufferedReader(new InputStreamReader(socketA.getInputStream(), "UTF-8"));
        writerA.println("LOGIN:admin:123456");
        String respA = readerA.readLine();
        assertTrue(respA.startsWith("LOGIN_SUCCESS"), "Admin login phải thành công");

        // Client A đăng ký cổng P2P
        writerA.println("P2P_REGISTER:19555");
        Thread.sleep(200);

        // Client B login
        Socket socketB = new Socket("127.0.0.1", SERVER_PORT);
        PrintWriter writerB = new PrintWriter(socketB.getOutputStream(), true);
        BufferedReader readerB = new BufferedReader(new InputStreamReader(socketB.getInputStream(), "UTF-8"));
        writerB.println("LOGIN:alice:123456");
        String respB = readerB.readLine();
        assertTrue(respB.startsWith("LOGIN_SUCCESS"), "Alice login phải thành công");

        // Client B hỏi P2P info của admin: GET_P2P_INFO:admin
        writerB.println("GET_P2P_INFO:admin");

        String line;
        String p2pInfoResp = null;
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && (line = readerB.readLine()) != null) {
            if (line.startsWith("P2P_INFO:")) {
                p2pInfoResp = line;
                break;
            }
        }

        assertNotNull(p2pInfoResp, "Server phải phản hồi thông tin P2P_INFO");
        // Format: P2P_INFO:admin:ip:19555
        String[] parts = p2pInfoResp.split(":");
        assertEquals(4, parts.length, "Format P2P_INFO phải gồm 4 phần: P2P_INFO:user:ip:port");
        assertEquals("admin", parts[1]);
        assertEquals("19555", parts[3]);

        socketA.close();
        socketB.close();
    }
}
