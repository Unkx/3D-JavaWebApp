package com.printplatform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecurringCostRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monthlyAmount;

    private LocalDate startDate;
    private LocalDate endDate;
}
