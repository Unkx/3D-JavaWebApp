package com.printplatform.dto;

public class DailyCountDto {
    private String date;
    private long count;

    public DailyCountDto(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() { return date; }
    public long getCount() { return count; }
}
