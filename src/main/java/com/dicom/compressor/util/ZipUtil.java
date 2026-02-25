package com.dicom.compressor.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class ZipUtil {

    private static final int BUFFER_SIZE = 4096;

    /**
     * Unzips a file into a target directory.
     *
     * @param zipFilePath   The path to the zip file.
     * @param destDirectory The directory where the extracted files should go.
     * @throws IOException If an I/O error occurs.
     */
    public static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();
                File newFile = new File(filePath);

                // Prevent Zip Slip vulnerability
                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + entry.getName());
                }

                if (!entry.isDirectory()) {
                    // Create parent directories if they don't exist
                    newFile.getParentFile().mkdirs();
                    extractFile(zipIn, filePath);
                } else {
                    newFile.mkdirs();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    /**
     * Extracts a single file safely.
     *
     * @param zipIn    The zip input stream.
     * @param filePath The local file path.
     * @throws IOException If an I/O error occurs.
     */
    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    /**
     * Zips an entire directory into a single zip file.
     *
     * @param sourceDirPath The directory to zip.
     * @param zipFilePath   The resulting zip file path.
     * @throws IOException If an I/O error occurs.
     */
    public static void zipDirectory(String sourceDirPath, String zipFilePath) throws IOException {
        Path sourcePath = Paths.get(sourceDirPath);
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString().replace("\\", "/"));
                        try {
                            zipOut.putNextEntry(zipEntry);
                            Files.copy(path, zipOut);
                            zipOut.closeEntry();
                        } catch (IOException e) {
                            System.err.println("Failed to zip " + path + ": " + e.getMessage());
                        }
                    });
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     *
     * @param directoryToBeDeleted The directory to clean up.
     * @return true if successful, false otherwise.
     */
    public static boolean deleteDirectory(File directoryToBeDeleted) {
        if (!directoryToBeDeleted.exists()) {
            return true;
        }
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
