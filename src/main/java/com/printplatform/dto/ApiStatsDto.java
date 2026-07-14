package com.printplatform.dto;

public class ApiStatsDto {
    private long totalRequests;
    private long errorCount;
    private double avgDurationMs;

    public ApiStatsDto(long totalRequests, long errorCount, double avgDurationMs) {
        this.totalRequests = totalRequests;
        this.errorCount = errorCount;
        this.avgDurationMs = avgDurationMs;
    }

    public long getTotalRequests() { return totalRequests; }
    public long getErrorCount() { return errorCount; }
    public double getAvgDurationMs() { return avgDurationMs; }
}
