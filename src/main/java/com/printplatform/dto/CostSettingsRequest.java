package com.printplatform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CostSettingsRequest {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal filamentPricePerKg;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal costPerPrintHour;
}
