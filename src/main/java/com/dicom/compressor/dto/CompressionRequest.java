package com.dicom.compressor.dto;

public class CompressionRequest {
    private String folderPath;

    public CompressionRequest() {
    }

    public CompressionRequest(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }
}
