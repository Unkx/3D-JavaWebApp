package com.printplatform.dto;

public class PathCountDto {
    private String path;
    private long count;

    public PathCountDto(String path, long count) {
        this.path = path;
        this.count = count;
    }

    public String getPath() { return path; }
    public long getCount() { return count; }
}
