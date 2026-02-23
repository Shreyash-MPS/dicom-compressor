package com.dicom.compressor.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
public class DicomCompressionService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String TOOL_NAME = IS_WINDOWS ? "dcmcjpls.exe" : "dcmcjpls";
    private static final String TOOL_FOLDER = "tools";
    private static final String ARCHIVE_FOLDER = "archive";

    public int compressFolder(String folderPath, String jcode) {
        System.out.println("==========================================");
        System.out.println("   DICOM COMPRESSION REQUEST");
        System.out.println("   Job Code: " + jcode);
        System.out.println("==========================================");

        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Invalid folder path: " + folderPath);
        }

        File[] files = folder.listFiles();
        int processedCount = 0;

        if (files != null && files.length > 0) {
            System.out.println("Found directory: " + folderPath);
            System.out.println("Starting compression process...");

            for (File file : files) {
                if (file.isFile() && isDicomFile(file)) {
                    compressFile(file);
                    processedCount++;
                }
            }
            System.out.println("\n✅ Task Finished!");
            System.out.println("Total files processed: " + processedCount);
        } else {
            System.out.println("ℹ️ The folder is empty.");
        }

        return processedCount;
    }

    public void compressFile(File originalFile) {

        System.out.println("Processing: " + originalFile.getName());

        // Locate EXE relative to JAR location
        File toolFile = new File(
                System.getProperty("user.dir")
                        + File.separator
                        + TOOL_FOLDER
                        + File.separator
                        + TOOL_NAME);

        String command;
        if (toolFile.exists()) {
            command = toolFile.getAbsolutePath();
        } else if (!IS_WINDOWS) {
            // If on Linux/Mac and it's not in the tools folder, try running from system
            // PATH
            command = TOOL_NAME;
        } else {
            System.err.println("ERROR: Executable not found at: " + toolFile.getAbsolutePath());
            return;
        }

        // Create archive folder if not exists
        File archiveDir = new File(System.getProperty("user.dir") + File.separator + ARCHIVE_FOLDER);

        if (!archiveDir.exists()) {
            archiveDir.mkdirs();
        }

        File tempCompressedFile = new File(originalFile.getAbsolutePath() + ".tmp");

        // Archived copy path
        File archivedFile = new File(archiveDir, originalFile.getName());

        try {

            // ---------- Run Compression ----------
            ProcessBuilder pb = new ProcessBuilder(
                    command,
                    originalFile.getAbsolutePath(),
                    tempCompressedFile.getAbsolutePath());

            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            // ---------- Success ----------
            if (exitCode == 0) {

                // Move ORIGINAL to archive
                Files.move(
                        originalFile.toPath(),
                        archivedFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // Move COMPRESSED to original location
                Files.move(
                        tempCompressedFile.toPath(),
                        originalFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                System.out.println("✅ Compressed & Archived: " + originalFile.getName());

            } else {

                System.err.println("❌ DCMTK Error Code: " + exitCode);
                if (tempCompressedFile.exists()) {
                    tempCompressedFile.delete();
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isDicomFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".dcm") || name.endsWith(".dicom") || !name.contains(".");
    }
}
