package com.printplatform.dto;

import com.printplatform.model.StlFile;

import java.time.LocalDateTime;

public class StlFileDto {
    private String id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String kind; // "image" or "stl"
    private LocalDateTime createdAt;

    public StlFileDto(StlFile f) {
        this.id = f.getId().toString();
        this.fileName = f.getFileName();
        this.fileSize = f.getFileSize();
        this.contentType = f.getContentType();
        this.kind = (f.getContentType() != null && f.getContentType().startsWith("image/")) ? "image" : "stl";
        this.createdAt = f.getCreatedAt();
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public Long getFileSize() { return fileSize; }
    public String getContentType() { return contentType; }
    public String getKind() { return kind; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
