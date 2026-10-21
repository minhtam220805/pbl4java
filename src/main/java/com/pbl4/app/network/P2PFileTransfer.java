package com.pbl4.app.network;

import com.pbl4.app.util.FileHelper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * P2PFileTransfer: Mở Socket TCP ngang hàng để gửi/nhận byte file hoặc thư mục đa luồng.
 *
 * <p>FIX BUG: Phiên bản trước dùng ObjectInputStream + BufferedInputStream từ cùng 1 Socket —
 * ObjectInputStream tự buffer dữ liệu, khiến BufferedInputStream đọc SAI vị trí.
 * Phiên bản này dùng DataInputStream/DataOutputStream nhất quán, đảm bảo stream chính xác.</p>
 *
 * Giao thức truyền:
 * <pre>
 *   [UTF string]  fileName
 *   [long 8 bytes] fileSize
 *   [boolean 1 byte] isDirectory
 *   [raw bytes]  file data
 * </pre>
 */
public class P2PFileTransfer {

    public interface TransferListener {
        void onProgress(double progress, long bytesTransferred, long totalBytes);
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    private ServerSocket receiverServerSocket;
    private volatile boolean isListening = false;
    private PrintWriter serverNotifyWriter;

    /**
     * Gắn PrintWriter của kết nối Server để gửi thông điệp giao thức (như FILE_SUCCESS)
     */
    public void setServerNotifyWriter(PrintWriter writer) {
        this.serverNotifyWriter = writer;
    }

    private int listeningPort = -1;

    public int getListeningPort() {
        return listeningPort;
    }

    /**
     * Khởi chạy Server lắng nghe nhận file P2P trên một Thread riêng biệt.
     * Tự động thử các cổng kế tiếp nếu cổng chỉ định đang bị chiếm dụng (hỗ trợ nhiều client trên cùng máy).
     *
     * @param port          Cổng lắng nghe P2P ưu tiên
     * @param saveDirectory Thư mục lưu trữ file/thư mục nhận được
     * @param listener      Callback cập nhật UI
     * @return Cổng thực tế đã được mở thành công (hoặc -1 nếu lỗi)
     */
    public int startReceiver(int port, File saveDirectory, TransferListener listener) {
        if (isListening) return listeningPort;

        try {
            ServerSocket ss = null;
            int attemptPort = port;
            for (int i = 0; i < 20; i++) {
                try {
                    ss = new ServerSocket(attemptPort);
                    break;
                } catch (IOException e) {
                    attemptPort++;
                }
            }
            if (ss == null) {
                ss = new ServerSocket(0); // Cổng ngẫu nhiên khả dụng từ OS
            }
            receiverServerSocket = ss;
            listeningPort = ss.getLocalPort();
            isListening = true;

            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }
            System.out.println("[P2P] Đang lắng nghe nhận file trên cổng " + listeningPort);

            Thread receiverThread = new Thread(() -> {
                try {
                    while (isListening && !receiverServerSocket.isClosed()) {
                        Socket socket = receiverServerSocket.accept();
                        // Mỗi kết nối nhận file xử lý trong Thread riêng
                        new Thread(() -> handleIncomingFile(socket, saveDirectory, listener),
                                "P2P-Receive-Worker").start();
                    }
                } catch (IOException e) {
                    if (isListening && listener != null) {
                        listener.onError("Lỗi cổng P2P Receiver: " + e.getMessage());
                    }
                }
            }, "P2P-Receiver-Main-Thread");

            receiverThread.setDaemon(true);
            receiverThread.start();

            return listeningPort;
        } catch (IOException e) {
            if (listener != null) {
                listener.onError("Không thể mở cổng P2P Receiver: " + e.getMessage());
            }
            return -1;
        }
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
     * Xử lý nhận byte stream từ người gửi.
     *
     * <p>Dùng DataInputStream DUY NHẤT cho toàn bộ quá trình nhận —
     * đọc header (tên file, kích thước, loại) rồi tiếp tục đọc raw bytes file.</p>
     */
    private void handleIncomingFile(Socket socket, File saveDirectory, TransferListener listener) {
        // Dùng 1 DataInputStream duy nhất — tránh bug double-stream
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()))) {

            // --- Đọc header giao thức ---
            String fileName   = dis.readUTF();      // UTF-8 encoded filename
            long   fileSize   = dis.readLong();     // 8 bytes: kích thước file
            boolean isDirectory = dis.readBoolean(); // 1 byte: có phải thư mục nén không

            // Đảm bảo an toàn đường dẫn (Chống Path Traversal)
            String safeFileName = new File(fileName).getName();
            if (safeFileName.isEmpty() || safeFileName.contains("..")) {
                safeFileName = "received_file_" + System.currentTimeMillis();
            }

            // Đảm bảo thư mục lưu tồn tại
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }
            File targetFile = new File(saveDirectory, safeFileName);

            // --- Đọc raw bytes và ghi xuống file ---
            long bytesReceived = 0;
            try (FileOutputStream fos = new FileOutputStream(targetFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[16384];
                int bytesRead;

                while (bytesReceived < fileSize) {
                    int toRead = (int) Math.min(buffer.length, fileSize - bytesReceived);
                    bytesRead = dis.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;

                    bos.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;

                    if (listener != null) {
                        double progress = fileSize > 0 ? (double) bytesReceived / fileSize : 1.0;
                        listener.onProgress(progress, bytesReceived, fileSize);
                    }
                }
                bos.flush();
            }

            // Nếu là thư mục nén .zip, tự động giải nén
            if (isDirectory) {
                String folderName = safeFileName.endsWith(".zip") ?
                        safeFileName.substring(0, safeFileName.length() - 4) : safeFileName;
                File unzippedDir = new File(saveDirectory, folderName);
                FileHelper.unzip(targetFile, unzippedDir);
                targetFile.delete(); // Xóa file .zip tạm
                if (listener != null) {
                    listener.onSuccess("Đã nhận và giải nén thư mục: " + folderName);
                }
            } else {
                if (listener != null) {
                    listener.onSuccess("Đã nhận thành công: " + safeFileName);
                }
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
     * <p>Dùng DataOutputStream DUY NHẤT — tránh bug double-stream của phiên bản cũ.</p>
     *
     * @param targetIp    Địa chỉ IP của người nhận (lấy từ server qua P2P_INFO)
     * @param targetPort  Cổng P2P người nhận đang lắng nghe
     * @param fileToSend  File hoặc Thư mục cần gửi
     * @param receiver    Tên người nhận (để thông báo lên server)
     * @param listener    Callback cập nhật UI
     */
    public void sendFile(String targetIp, int targetPort, File fileToSend, String receiver,
                         TransferListener listener) {
        Thread sendThread = new Thread(() -> {
            File fileToStream = fileToSend;
            boolean isDir     = fileToSend.isDirectory();
            File tempZip      = null;

            try {
                // Nén thư mục thành .zip trước khi gửi
                if (isDir) {
                    tempZip = File.createTempFile("p2p_dir_", ".zip");
                    FileHelper.zipDirectory(fileToSend, tempZip);
                    fileToStream = tempZip;
                }

                String fileName  = isDir ? fileToSend.getName() + ".zip" : fileToSend.getName();
                long   totalBytes = fileToStream.length();

                // Dùng 1 DataOutputStream duy nhất — đảm bảo ghi tuần tự
                try (Socket socket = new Socket(targetIp, targetPort);
                     DataOutputStream dos = new DataOutputStream(
                             new BufferedOutputStream(socket.getOutputStream()));
                     FileInputStream fis = new FileInputStream(fileToStream);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {

                    // --- Ghi header giao thức (phải đúng thứ tự với bên nhận) ---
                    dos.writeUTF(fileName);      // tên file
                    dos.writeLong(totalBytes);   // kích thước
                    dos.writeBoolean(isDir);     // flag thư mục nén
                    dos.flush();

                    // --- Ghi raw bytes file ---
                    byte[] buffer = new byte[16384];
                    int    bytesRead;
                    long   bytesSent = 0;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                        bytesSent += bytesRead;

                        if (listener != null) {
                            double progress = totalBytes > 0 ? (double) bytesSent / totalBytes : 1.0;
                            listener.onProgress(progress, bytesSent, totalBytes);
                        }
                    }
                    dos.flush();

                    String formattedSize = FileHelper.formatFileSize(totalBytes);

                    // Thông báo FILE_SUCCESS lên ChatServer
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
                // Xóa file zip tạm nếu có
                if (tempZip != null && tempZip.exists()) {
                    tempZip.delete();
                }
            }
        }, "P2P-Send-Thread");

        sendThread.setDaemon(true);
        sendThread.start();
    }
}
