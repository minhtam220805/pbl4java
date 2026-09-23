package com.pbl4.app.network;

import com.pbl4.app.model.FileMetadata;
import com.pbl4.app.util.FileHelper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * P2PFileTransfer: Mở Socket TCP ngang hàng để gửi/nhận byte file hoặc thư mục đa luồng.
 * Tự động thông báo FILE_SUCCESS lên ChatServer khi truyền đủ 100% byte dữ liệu.
 */
public class P2PFileTransfer {

    public interface TransferListener {
        void onProgress(double progress, long bytesTransferred, long totalBytes);
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    private ServerSocket receiverServerSocket;
    private boolean isListening = false;
    private PrintWriter serverNotifyWriter;

    /**
     * Gắn PrintWriter của kết nối Server để gửi thông điệp giao thức (như FILE_SUCCESS)
     */
    public void setServerNotifyWriter(PrintWriter writer) {
        this.serverNotifyWriter = writer;
    }

    /**
     * Khởi chạy Server lắng nghe nhận file P2P trên một Thread riêng biệt
     *
     * @param port          Cổng lắng nghe P2P
     * @param saveDirectory Thư mục lưu trữ file/thư mục nhận được
     * @param listener      Callback cập nhật UI
     */
    public void startReceiver(int port, File saveDirectory, TransferListener listener) {
        if (isListening) return;

        Thread receiverThread = new Thread(() -> {
            try {
                receiverServerSocket = new ServerSocket(port);
                isListening = true;
                if (!saveDirectory.exists()) {
                    saveDirectory.mkdirs();
                }

                while (isListening && !receiverServerSocket.isClosed()) {
                    Socket socket = receiverServerSocket.accept();
                    // Xử lý từng kết nối nhận file trong 1 Thread riêng
                    new Thread(() -> handleIncomingFile(socket, saveDirectory, listener)).start();
                }
            } catch (IOException e) {
                if (isListening && listener != null) {
                    listener.onError("Lỗi cổng P2P Receiver: " + e.getMessage());
                }
            }
        }, "P2P-Receiver-Main-Thread");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    /**
     * Dừng Server lắng nghe P2P
     */
    public void stopReceiver() {
        isListening = false;
        try {
            if (receiverServerSocket != null && !receiverServerSocket.isClosed()) {
                receiverServerSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Lỗi đóng P2P ServerSocket: " + e.getMessage());
        }
    }

    /**
     * Xử lý nhận byte stream từ người gửi
     */
    private void handleIncomingFile(Socket socket, File saveDirectory, TransferListener listener) {
        try (
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream())
        ) {
            FileMetadata metadata = (FileMetadata) ois.readObject();
            File targetFile = new File(saveDirectory, metadata.getFileName());

            long totalBytes = metadata.getFileSize();
            long bytesReceived = 0;

            try (FileOutputStream fos = new FileOutputStream(targetFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[16384];
                int bytesRead;

                while (bytesReceived < totalBytes && (bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;

                    if (listener != null) {
                        double progress = totalBytes > 0 ? (double) bytesReceived / totalBytes : 1.0;
                        listener.onProgress(progress, bytesReceived, totalBytes);
                    }
                }
                bos.flush();
            }

            // Nếu là thư mục nén, tự động giải nén ra thư mục gốc
            if (metadata.isDirectory()) {
                File unzippedDir = new File(saveDirectory, metadata.getFileName().replace(".zip", ""));
                FileHelper.unzip(targetFile, unzippedDir);
                targetFile.delete(); // Xóa file zip tạm
            }

            if (listener != null) {
                listener.onSuccess("Đã nhận thành công: " + metadata.getFileName());
            }

        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Lỗi nhận file: " + e.getMessage());
            }
        } finally {
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Gửi file hoặc thư mục đến người nhận qua P2P.
     * Khi hoàn tất 100%, tự động gửi lệnh FILE_SUCCESS:receiver:filename:size lên Server.
     *
     * @param targetIp    Địa chỉ IP người nhận
     * @param targetPort  Cổng P2P người nhận
     * @param fileToSend  File hoặc Thư mục cần gửi
     * @param receiver    Tên người nhận (để thông báo lên server)
     * @param listener    Callback cập nhật UI
     */
    public void sendFile(String targetIp, int targetPort, File fileToSend, String receiver, TransferListener listener) {
        Thread sendThread = new Thread(() -> {
            File fileToStream = fileToSend;
            boolean isDir = fileToSend.isDirectory();
            File tempZip = null;

            try {
                if (isDir) {
                    tempZip = File.createTempFile("p2p_", ".zip");
                    FileHelper.zipDirectory(fileToSend, tempZip);
                    fileToStream = tempZip;
                }

                FileMetadata metadata = new FileMetadata(
                        isDir ? fileToSend.getName() + ".zip" : fileToSend.getName(),
                        fileToStream.length(),
                        isDir
                );

                try (
                    Socket socket = new Socket(targetIp, targetPort);
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
                    FileInputStream fis = new FileInputStream(fileToStream);
                    BufferedInputStream bis = new BufferedInputStream(fis)
                ) {
                    oos.writeObject(metadata);
                    oos.flush();

                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    long totalBytes = metadata.getFileSize();
                    long bytesSent = 0;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        bytesSent += bytesRead;

                        if (listener != null) {
                            double progress = totalBytes > 0 ? (double) bytesSent / totalBytes : 1.0;
                            listener.onProgress(progress, bytesSent, totalBytes);
                        }
                    }
                    bos.flush();

                    String formattedSize = FileHelper.formatFileSize(totalBytes);

                    // Yêu cầu 3: Khi hàm truyền byte file hoàn tất 100%, gửi lệnh FILE_SUCCESS:receiver:filename:size lên Server
                    if (serverNotifyWriter != null && receiver != null && !receiver.isEmpty()) {
                        serverNotifyWriter.println("FILE_SUCCESS:" + receiver + ":" + fileToSend.getName() + ":" + formattedSize);
                    }

                    if (listener != null) {
                        listener.onSuccess("Gửi thành công: " + fileToSend.getName() + " (" + formattedSize + ")");
                    }
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Lỗi gửi file: " + e.getMessage());
                }
            } finally {
                if (tempZip != null && tempZip.exists()) {
                    tempZip.delete();
                }
            }
        }, "P2P-Send-Thread");

        sendThread.setDaemon(true);
        sendThread.start();
    }
}
