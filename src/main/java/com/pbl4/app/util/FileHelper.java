package com.pbl4.app.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * FileHelper: Hàm nén/giải nén thư mục hoặc duyệt cây thư mục.
 */
public class FileHelper {

    /**
     * Nén một thư mục thành file ZIP
     *
     * @param sourceDir     Thư mục nguồn cần nén
     * @param outputZipFile File zip đầu ra
     * @throws IOException Nếu có lỗi I/O
     */
    public static void zipDirectory(File sourceDir, File outputZipFile) throws IOException {
        Path sourcePath = sourceDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZipFile))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(relativePath));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!sourcePath.equals(dir)) {
                        String relativePath = sourcePath.relativize(dir).toString().replace("\\", "/") + "/";
                        zos.putNextEntry(new ZipEntry(relativePath));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Giải nén file ZIP vào thư mục đích
     *
     * @param zipFile Thư mục hoặc file ZIP nguồn
     * @param destDir Thư mục đích để bung file
     * @throws IOException Nếu có lỗi I/O
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Không thể tạo thư mục " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Không thể tạo thư mục " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    /**
     * Ngăn chặn lỗ hổng Zip Slip
     */
    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry nằm ngoài thư mục đích: " + zipEntry.getName());
        }
        return destFile;
    }

    /**
     * Duyệt toàn bộ danh sách file trong cây thư mục
     *
     * @param dir Thư mục gốc
     * @return Danh sách các file
     */
    public static List<File> listFilesRecursively(File dir) {
        List<File> fileList = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            return fileList;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                fileList.add(file);
                if (file.isDirectory()) {
                    fileList.addAll(listFilesRecursively(file));
                }
            }
        }
        return fileList;
    }

    /**
     * Định dạng kích thước file sang dạng chuỗi dễ đọc (B, KB, MB, GB)
     *
     * @param bytes Số bytes
     * @return Chuỗi định dạng
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
