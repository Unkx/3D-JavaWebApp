package com.printplatform.dto;

public class RatingSummaryDto {
    private Double averageStars;
    private long count;

    public RatingSummaryDto(Double averageStars, long count) {
        this.averageStars = averageStars;
        this.count = count;
    }

    public Double getAverageStars() { return averageStars; }
    public long getCount()          { return count; }
}
