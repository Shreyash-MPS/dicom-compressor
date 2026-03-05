package com.dicom.compressor.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DicomCompressionService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String TOOL_NAME = IS_WINDOWS ? "dcmcjpls.exe" : "dcmcjpls";
    private static final String TOOL_FOLDER = "tools";
    private static final String ARCHIVE_FOLDER = "archive";

    // Store job details for async polling
    private final Map<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();

    public static class JobStatus {
        public String id;
        public String status; // PENDING, PROCESSING, COMPLETED, FAILED
        public int processedCount;
        public int totalFiles;
        public String resultZipPath;
        public String errorMsg;
        public String originalFilename;

        public JobStatus(String id) {
            this.id = id;
            this.status = "PENDING";
        }
    }

    public JobStatus getJobStatus(String jobId) {
        return jobStatuses.get(jobId);
    }

    public JobStatus createJobStatus(String jobId) {
        JobStatus ts = new JobStatus(jobId);
        jobStatuses.put(jobId, ts);
        return ts;
    }

    public void discardJobStatus(String jobId) {
        jobStatuses.remove(jobId);
    }

    public int compressFolder(String folderPath, String jobId, String customArchiveDir) {
        JobStatus job = jobStatuses.getOrDefault(jobId, new JobStatus(jobId));
        job.status = "PROCESSING";
        jobStatuses.put(jobId, job);

        System.out.println("==========================================");
        System.out.println("   DICOM COMPRESSION REQUEST");
        System.out.println("   Job ID: " + jobId);
        System.out.println("==========================================");
        System.out.println(
                "[LOG] OS: " + System.getProperty("os.name") + " | user.dir: " + System.getProperty("user.dir"));
        System.out.println("[LOG] Archive dir: " + customArchiveDir);

        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            job.status = "FAILED";
            job.errorMsg = "Invalid folder path: " + folderPath;
            throw new IllegalArgumentException(job.errorMsg);
        }

        int processedCount = 0;
        int successCount = 0;
        int failCount = 0;

        try (Stream<Path> pathStream = Files.walk(folder.toPath())) {
            List<File> allFiles = pathStream
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .filter(this::isDicomFile)
                    .collect(Collectors.toList());

            if (!allFiles.isEmpty()) {
                job.totalFiles = allFiles.size();
                System.out.println("Found directory with " + allFiles.size() + " DICOM files: " + folderPath);
                System.out.println("Starting compression process...");

                for (File file : allFiles) {
                    boolean ok = compressFile(file, customArchiveDir);
                    if (ok) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                    processedCount++;
                    job.processedCount = processedCount;
                }
                System.out.println("\n==========================================");
                System.out.println("   COMPRESSION SUMMARY");
                System.out.println("   Total files : " + processedCount);
                System.out.println("   ✅ Succeeded : " + successCount);
                System.out.println("   ❌ Failed    : " + failCount);
                System.out.println("==========================================");
            } else {
                System.out.println("ℹ️ No DICOM files found in the folder or its subdirectories.");
            }
        } catch (Exception e) {
            job.status = "FAILED";
            job.errorMsg = "Error scanning directory: " + e.getMessage();
            throw new RuntimeException(e);
        }

        // We do NOT set status=COMPLETED here yet because re-zipping happens in the
        // controller.
        return processedCount;
    }

    // Kept for backward compatibility
    public int compressFolder(String folderPath) {
        return compressFolder(folderPath, UUID.randomUUID().toString(),
                System.getProperty("user.dir") + File.separator + ARCHIVE_FOLDER);
    }

    /**
     * Compresses a single DICOM file using dcmcjpls.
     * 
     * @return true if compression succeeded, false otherwise.
     */
    public boolean compressFile(File originalFile, String archiveDirPath) {

        System.out.println("\n------------------------------------------");
        System.out.println("[LOG] Processing: " + originalFile.getName());
        System.out.println("[LOG]   Full path : " + originalFile.getAbsolutePath());
        System.out.println("[LOG]   File size : " + originalFile.length() + " bytes");
        System.out.println("[LOG]   Readable  : " + originalFile.canRead());
        System.out.println("[LOG]   Writable  : " + originalFile.canWrite());

        if (!originalFile.exists()) {
            System.err.println("[ERROR] File does not exist: " + originalFile.getAbsolutePath());
            return false;
        }

        if (originalFile.length() == 0) {
            System.err.println("[ERROR] File is empty (0 bytes): " + originalFile.getAbsolutePath());
            return false;
        }

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
            System.out.println("[LOG]   Tool found: " + command);
        } else if (!IS_WINDOWS) {
            // If on Linux/Mac and it's not in the tools folder, try running from system
            // PATH
            command = TOOL_NAME;
            System.out.println("[LOG]   Tool not in tools/ folder, using system PATH: " + command);
        } else {
            System.err.println("[ERROR] Executable not found at: " + toolFile.getAbsolutePath());
            return false;
        }

        // Create archive folder if not exists
        File archiveDir = new File(archiveDirPath);

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

            // Set DCMDICTPATH so dcmcjpls can find the DICOM data dictionary
            String dictPath = System.getProperty("user.dir")
                    + File.separator + TOOL_FOLDER
                    + File.separator + "dicom.dic";
            File dictFile = new File(dictPath);
            if (dictFile.exists()) {
                pb.environment().put("DCMDICTPATH", dictFile.getAbsolutePath());
                System.out.println("[LOG]   DictPath  : " + dictFile.getAbsolutePath());
            } else {
                System.out.println("[WARN]  dicom.dic NOT found at: " + dictPath
                        + " — DCMTK may fail without the data dictionary!");
            }

            System.out.println("[LOG]   Command   : " + String.join(" ", pb.command()));

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read the process output (stdout + stderr merged)
            String processOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            // ---------- Success ----------
            if (exitCode == 0) {

                long originalSize = archivedFile.exists() ? archivedFile.length() : originalFile.length();

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

                long compressedSize = originalFile.length();
                System.out.println("✅ Compressed & Archived: " + originalFile.getName()
                        + " (" + originalSize + " → " + compressedSize + " bytes)");
                return true;

            } else {

                System.err.println("❌ DCMTK Error Code: " + exitCode + " for file: " + originalFile.getName());
                System.err.println("❌ DCMTK Output:");
                if (processOutput != null && !processOutput.isBlank()) {
                    System.err.println(processOutput.trim());
                } else {
                    System.err.println("   (no output captured from dcmcjpls)");
                }

                if (tempCompressedFile.exists()) {
                    tempCompressedFile.delete();
                }
                return false;

            }

        } catch (Exception e) {
            System.err.println("[ERROR] Exception while compressing " + originalFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Kept for backward compatibility
    public void compressFile(File originalFile) {
        compressFile(originalFile, System.getProperty("user.dir") + File.separator + ARCHIVE_FOLDER);
    }

    private boolean isDicomFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".dcm") || name.endsWith(".dicom") || !name.contains(".");
    }
}
