package com.pbl4.app.model;

/**
 * FileTransferRecord: Đại diện cho một bản ghi lịch sử truyền nhận file trong database.
 */
public class FileTransferRecord {
    private int id;
    private String sender;
    private String receiver;
    private String fileName;
    private String fileSize;
    private String status;
    private String createdAt;

    public FileTransferRecord() {}

    public FileTransferRecord(int id, String sender, String receiver, String fileName, String fileSize, String status, String createdAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
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
}
