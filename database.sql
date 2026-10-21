-- =======================================================
-- PBL4: HỆ THỐNG CHAT & TRUYỀN FILE P2P TÍCH HỢP MYSQL
-- CƠ SỞ DỮ LIỆU: pbl4_db
-- =======================================================

CREATE DATABASE IF NOT EXISTS pbl4_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pbl4_db;

-- -------------------------------------------------------
-- 1. Bảng users: Quản lý thông tin tài khoản người dùng & Phân quyền
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER', -- 'ADMIN' hoặc 'USER'
    status VARCHAR(20) DEFAULT 'OFFLINE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Thêm cột role nếu bảng users đã tồn tại trước đó từ phiên bản cũ
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER';

-- -------------------------------------------------------
-- 2. Bảng activity_logs: Quản lý nhật ký hoạt động (Login, Logout, v.v.)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_activity (username, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------
-- 3. Bảng chat_messages: Quản lý lịch sử tin nhắn nhóm và chat riêng
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL, -- 'ALL' nếu là chat nhóm, hoặc username người nhận nếu là chat riêng
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_receiver (receiver, created_at),
    INDEX idx_chat_private (sender, receiver, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------
-- 4. Bảng file_transfers: Quản lý lịch sử truyền nhận tệp P2P
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_transfers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'SUCCESS',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_sender_receiver (sender, receiver, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------
-- Dữ liệu tài khoản thử nghiệm ban đầu (Mật khẩu đăng nhập mặc định: 123456)
-- admin có role 'ADMIN', các user khác có role 'USER'.
-- Chuỗi bên dưới là mã băm SHA-256 (kèm Salt bảo mật) của mật khẩu '123456'.
-- -------------------------------------------------------
INSERT INTO users (username, password, role, status) VALUES 
('admin', '73649ba5810663c1765b962ce60a62adc4b58f9fff87596b3873c5c3cae2ec7b', 'ADMIN', 'OFFLINE'),
('alice', '73649ba5810663c1765b962ce60a62adc4b58f9fff87596b3873c5c3cae2ec7b', 'USER', 'OFFLINE'),
('bob', '73649ba5810663c1765b962ce60a62adc4b58f9fff87596b3873c5c3cae2ec7b', 'USER', 'OFFLINE')
ON DUPLICATE KEY UPDATE password=VALUES(password), role=VALUES(role);
