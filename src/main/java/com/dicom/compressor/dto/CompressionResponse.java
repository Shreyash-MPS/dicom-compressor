package com.dicom.compressor.dto;

public class CompressionResponse {
    private boolean success;
    private String message;
    private int filesProcessed;

    public CompressionResponse() {
    }

    public CompressionResponse(boolean success, String message, int filesProcessed) {
        this.success = success;
        this.message = message;
        this.filesProcessed = filesProcessed;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(int filesProcessed) {
        this.filesProcessed = filesProcessed;
    }
}
