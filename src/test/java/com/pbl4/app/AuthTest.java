package com.pbl4.app;

import com.pbl4.app.dao.DatabaseConnection;
import com.pbl4.app.dao.UserDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthTest {

    private static UserDAO userDAO;
    private static final String TEST_USER = "test_junit_user";
    private static final String TEST_PASS = "junit_pass_123";

    @BeforeAll
    public static void setUp() {
        boolean initialized = DatabaseConnection.initializeDatabase();
        assertTrue(initialized, "Khởi tạo database pbl4_db phải thành công");
        userDAO = new UserDAO();

        // Xóa người dùng test nếu có từ trước
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, TEST_USER);
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    public void testDatabaseConnection() {
        assertTrue(DatabaseConnection.testConnection(), "Kết nối CSDL MySQL phải thành công");
    }

    @Test
    @Order(2)
    public void testDefaultAdminLogin() {
        // Tài khoản admin mặc định: admin / 123456
        assertTrue(userDAO.login("admin", "123456"), "Đăng nhập tài khoản admin mặc định phải thành công");
        assertEquals("ADMIN", userDAO.getUserRole("admin"), "Vai trò của admin phải là ADMIN");
    }

    @Test
    @Order(3)
    public void testDefaultUserLogin() {
        // Tài khoản user mặc định: alice / 123456
        assertTrue(userDAO.login("alice", "123456"), "Đăng nhập tài khoản alice mặc định phải thành công");
        assertEquals("USER", userDAO.getUserRole("alice"), "Vai trò của alice phải là USER");
    }

    @Test
    @Order(4)
    public void testLoginWithWrongPassword() {
        assertFalse(userDAO.login("admin", "wrong_password_999"), "Mật khẩu sai phải trả về false");
    }

    @Test
    @Order(5)
    public void testRegisterNewUser() {
        // Kiểm tra chưa tồn tại
        assertFalse(userDAO.userExists(TEST_USER), "Tài khoản test chưa tồn tại trước khi đăng ký");

        // Đăng ký mới
        boolean registered = userDAO.register(TEST_USER, TEST_PASS);
        assertTrue(registered, "Đăng ký tài khoản mới phải thành công");

        // Kiểm tra đã tồn tại
        assertTrue(userDAO.userExists(TEST_USER), "Sau khi đăng ký, userExists phải trả về true");

        // Đăng nhập bằng tài khoản vừa tạo
        assertTrue(userDAO.login(TEST_USER, TEST_PASS), "Đăng nhập bằng tài khoản vừa đăng ký phải thành công");
        assertEquals("USER", userDAO.getUserRole(TEST_USER), "Tài khoản đăng ký mới phải có quyền USER");
    }

    @Test
    @Order(6)
    public void testRegisterDuplicateUser() {
        // Đăng ký lại cùng tên tài khoản phải thất bại
        boolean registeredAgain = userDAO.register(TEST_USER, "another_pass");
        assertFalse(registeredAgain, "Không được phép đăng ký trùng username");
    }

    @Test
    @Order(7)
    public void testRegisterInvalidInputs() {
        assertFalse(userDAO.register("", "pass"), "Username rỗng phải thất bại");
        assertFalse(userDAO.register("   ", "pass"), "Username chỉ có khoảng trắng phải thất bại");
        assertFalse(userDAO.register("validuser", ""), "Password rỗng phải thất bại");
        assertFalse(userDAO.register(null, "pass"), "Username null phải thất bại");
    }

    @Test
    @Order(8)
    public void testServerNetworkAuth() throws Exception {
        int testPort = 18888;
        com.pbl4.app.network.ChatServer server = new com.pbl4.app.network.ChatServer(testPort);
        Thread serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(500);

        String netUser = "net_test_user";
        String netPass = "net_pass_123";

        // Xóa netUser nếu có từ lần chạy trước
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, netUser);
            stmt.executeUpdate();
        } catch (Exception ignored) {}

        // 1. Kiểm tra Register qua socket
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"))) {
            out.println("REGISTER:" + netUser + ":" + netPass);
            String regResp = in.readLine();
            assertEquals("REGISTER_SUCCESS", regResp, "Đăng ký tài khoản qua Server Socket phải thành công");
        }

        // 2. Kiểm tra Register trùng qua socket
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"))) {
            out.println("REGISTER:" + netUser + ":" + netPass);
            String regResp = in.readLine();
            assertTrue(regResp.startsWith("REGISTER_FAIL:"), "Đăng ký trùng qua Server Socket phải trả về lỗi");
        }

        // 3. Kiểm tra Login sai mật khẩu qua socket
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"))) {
            out.println("LOGIN:" + netUser + ":wrongpassword");
            String loginResp = in.readLine();
            assertTrue(loginResp.startsWith("LOGIN_FAIL:"), "Đăng nhập sai mật khẩu qua Socket phải thất bại");
        }

        // 4. Kiểm tra Login thành công tài khoản vừa tạo qua socket
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"))) {
            out.println("LOGIN:" + netUser + ":" + netPass);
            String loginResp = in.readLine();
            assertEquals("LOGIN_SUCCESS:USER", loginResp, "Đăng nhập user mới qua Socket phải thành công với vai trò USER");
        }

        // 5. Kiểm tra Login thành công tài khoản ADMIN qua socket
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), "UTF-8"))) {
            out.println("LOGIN:admin:123456");
            String loginResp = in.readLine();
            assertEquals("LOGIN_SUCCESS:ADMIN", loginResp, "Đăng nhập admin qua Socket phải thành công với vai trò ADMIN");
        }

        // Dọn dẹp
        server.stop();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, netUser);
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    @Test
    @Order(9)
    public void tearDown() {
        // Dọn dẹp tài khoản test
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, TEST_USER);
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }
}
