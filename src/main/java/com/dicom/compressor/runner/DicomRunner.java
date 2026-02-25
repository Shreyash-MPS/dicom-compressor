package com.dicom.compressor.runner;

import com.dicom.compressor.service.DicomCompressionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DicomRunner implements CommandLineRunner {

    private final DicomCompressionService compressionService;

    public DicomRunner(DicomCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==========================================");
        System.out.println("   DICOM LOSSLESS COMPRESSOR (DCMTK)      ");
        System.out.println("==========================================");

        if (args.length == 0) {
            System.out.println("Usage: java -jar compressor.jar <folder_path>");
            System.out.println("Example: java -jar compressor.jar \"C:\\Images\\Batch1\"");
            return;
        }

        // Clean and normalize the input path to handle Windows/PowerShell quote issues
        String inputPath = args[0].replace("\"", "").trim();
        Path path = Paths.get(inputPath).toAbsolutePath().normalize();

        if (Files.exists(path) && Files.isDirectory(path)) {
            try {
                // Generate a random job ID and use the default archive folder
                String jobId = java.util.UUID.randomUUID().toString();
                String defaultArchiveDir = System.getProperty("user.dir") + File.separator + "archive";

                int processedCount = compressionService.compressFolder(path.toString(), jobId,
                        defaultArchiveDir);

                System.out.println("\n✅ Task Finished!");
                System.out.println("Total files processed: " + processedCount);
            } catch (Exception e) {
                System.err.println("❌ Error during compression: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ Invalid folder path: " + path);
            System.err.println("Please check if the folder exists and you have permissions.");
        }
    }

}