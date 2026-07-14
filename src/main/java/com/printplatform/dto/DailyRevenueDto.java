package com.printplatform.dto;

import java.math.BigDecimal;

public class DailyRevenueDto {
    private String date;
    private BigDecimal platformFee;
    private BigDecimal totalVolume;

    public DailyRevenueDto(String date, BigDecimal platformFee, BigDecimal totalVolume) {
        this.date = date;
        this.platformFee = platformFee;
        this.totalVolume = totalVolume;
    }

    public String getDate() { return date; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getTotalVolume() { return totalVolume; }
}
