package com.printplatform.dto;

import java.math.BigDecimal;
import java.util.List;

public class RevenueSummaryDto {
    private List<DailyRevenueDto> byDay;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalVolume;
    private long paidCount;
    private long pendingCount;

    public RevenueSummaryDto(List<DailyRevenueDto> byDay, BigDecimal totalPlatformFee, BigDecimal totalVolume,
                              long paidCount, long pendingCount) {
        this.byDay = byDay;
        this.totalPlatformFee = totalPlatformFee;
        this.totalVolume = totalVolume;
        this.paidCount = paidCount;
        this.pendingCount = pendingCount;
    }

    public List<DailyRevenueDto> getByDay() { return byDay; }
    public BigDecimal getTotalPlatformFee() { return totalPlatformFee; }
    public BigDecimal getTotalVolume() { return totalVolume; }
    public long getPaidCount() { return paidCount; }
    public long getPendingCount() { return pendingCount; }
}
