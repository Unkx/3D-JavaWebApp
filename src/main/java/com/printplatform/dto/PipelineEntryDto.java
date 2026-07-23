package com.printplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PipelineEntryDto {
    private String status;
    private long count;
    private BigDecimal value;
}
