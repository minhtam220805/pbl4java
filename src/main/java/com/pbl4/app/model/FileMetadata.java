package com.pbl4.app.model;

import java.io.Serializable;

/**
 * FileMetadata: Chứa thông tin gói tin truyền file (Tên file, kích thước, là file hay thư mục).
 */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;
    private long fileSize;
    private boolean isDirectory;
    private String relativePath;

    public FileMetadata() {
    }

    public FileMetadata(String fileName, long fileSize, boolean isDirectory) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.isDirectory = isDirectory;
    }

    public FileMetadata(String fileName, long fileSize, boolean isDirectory, String relativePath) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.isDirectory = isDirectory;
        this.relativePath = relativePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", isDirectory=" + isDirectory +
                ", relativePath='" + relativePath + '\'' +
                '}';
    }
}
