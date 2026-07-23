package com.printplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MonthBucketDto {
    private String month;
    private BigDecimal inflow;
    private BigDecimal pending;
    private BigDecimal costs;
    private BigDecimal net;
}
