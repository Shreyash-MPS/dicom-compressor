package com.dicom.compressor.controller;

import com.dicom.compressor.dto.CompressionRequest;
import com.dicom.compressor.dto.CompressionResponse;
import com.dicom.compressor.service.DicomCompressionService;
import com.dicom.compressor.util.ZipUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/dicom")
public class DicomController {

    private final DicomCompressionService compressionService;

    public DicomController(DicomCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @PostMapping("/compress")
    public ResponseEntity<CompressionResponse> compressDicomFiles(@RequestBody CompressionRequest request) {
        try {
            int processedCount = compressionService.compressFolder(request.getFolderPath());

            CompressionResponse response = new CompressionResponse(
                    true,
                    "Compression completed successfully",
                    processedCount);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            CompressionResponse response = new CompressionResponse(
                    false,
                    e.getMessage(),
                    0);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            CompressionResponse response = new CompressionResponse(
                    false,
                    "Error during compression: " + e.getMessage(),
                    0);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping(value = "/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a valid ZIP file."));
        }

        String originalFilename = file.getOriginalFilename();
        String jobId = UUID.randomUUID().toString();
        DicomCompressionService.JobStatus jobStatus = compressionService.createJobStatus(jobId);
        jobStatus.originalFilename = originalFilename;

        // Define temp directories for this specific job
        String baseTempDir = System.getProperty("user.dir") + File.separator + "temp" + File.separator + jobId;
        String uploadedZipPath = baseTempDir + File.separator + "uploaded.zip";

        File baseDirFile = new File(baseTempDir);
        if (!baseDirFile.exists()) {
            baseDirFile.mkdirs();
        }

        try {
            // Save uploaded file
            File uploadedFile = new File(uploadedZipPath);
            file.transferTo(uploadedFile);

            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "status", "UPLOADED",
                    "message", "Upload successful, ready for compression."));

        } catch (IOException e) {
            compressionService.discardJobStatus(jobId);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to save uploaded file: " + e.getMessage()));
        }
    }

    @PostMapping("/start/{jobId}")
    public ResponseEntity<?> startCompression(@PathVariable String jobId) {
        DicomCompressionService.JobStatus jobStatus = compressionService.getJobStatus(jobId);

        if (jobStatus == null) {
            return ResponseEntity.notFound().build();
        }

        String baseTempDir = System.getProperty("user.dir") + File.separator + "temp" + File.separator + jobId;
        String extractDir = baseTempDir + File.separator + "extracted";
        String archiveDir = baseTempDir + File.separator + "archive";
        String uploadedZipPath = baseTempDir + File.separator + "uploaded.zip";
        String finalZipPath = baseTempDir + File.separator + "compressed_" + jobStatus.originalFilename;

        // Run process asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Unzip
                ZipUtil.unzip(uploadedZipPath, extractDir);

                // 2. Compress (this updates jobStatus internally)
                compressionService.compressFolder(extractDir, jobId, archiveDir);

                // 3. Zip the extract dir (which now contains the compressed files)
                ZipUtil.zipDirectory(extractDir, finalZipPath);

                jobStatus.status = "COMPLETED";
                jobStatus.resultZipPath = finalZipPath;

            } catch (Exception e) {
                jobStatus.status = "FAILED";
                jobStatus.errorMsg = e.getMessage();
                e.printStackTrace();
            }
        });

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "PROCESSING",
                "message", "Compression started."));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        DicomCompressionService.JobStatus status = compressionService.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> downloadCompressedZip(@PathVariable String jobId) {
        DicomCompressionService.JobStatus jobStatus = compressionService.getJobStatus(jobId);

        if (jobStatus == null || !"COMPLETED".equals(jobStatus.status)) {
            return ResponseEntity.badRequest().body(null);
        }

        File zipFile = new File(jobStatus.resultZipPath);
        if (!zipFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(zipFile);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFile.getName() + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(zipFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/job/{jobId}")
    public ResponseEntity<?> deleteJobFiles(@PathVariable String jobId) {
        String baseTempDir = System.getProperty("user.dir") + File.separator + "temp" + File.separator + jobId;
        ZipUtil.deleteDirectory(new File(baseTempDir));
        compressionService.discardJobStatus(jobId);
        System.out.println("Cleaned up temp files for job: " + jobId);
        return ResponseEntity.ok().build();
    }
}
