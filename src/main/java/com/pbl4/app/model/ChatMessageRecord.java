package com.pbl4.app.model;

/**
 * ChatMessageRecord: Đại diện cho một bản ghi tin nhắn chat trong database.
 */
public class ChatMessageRecord {
    private int id;
    private String sender;
    private String receiver;
    private String message;
    private String createdAt;

    public ChatMessageRecord() {}

    public ChatMessageRecord(int id, String sender, String receiver, String message, String createdAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
