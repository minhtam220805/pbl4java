package com.pbl4.app.model;

/**
 * User: Đại diện cho thực thể người dùng trong cơ sở dữ liệu.
 */
public class User {
    private int id;
    private String username;
    private String password;
    private String role; // "ADMIN" hoặc "USER"
    private String status; // "ONLINE" hoặc "OFFLINE"
    private String createdAt;

    public User() {
    }

    public User(int id, String username, String password, String role, String status, String createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
