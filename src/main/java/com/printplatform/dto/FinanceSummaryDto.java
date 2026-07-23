package com.printplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class FinanceSummaryDto {
    private BigDecimal totalReleased;
    private BigDecimal totalHeld;
    private BigDecimal monthProfit;
    private BigDecimal monthCosts;
    private List<MonthBucketDto> months;
}
