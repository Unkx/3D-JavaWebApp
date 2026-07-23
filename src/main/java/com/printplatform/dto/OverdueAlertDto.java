package com.printplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class OverdueAlertDto {
    private UUID offerId;
    private UUID listingId;
    private String listingTitle;
    private String buyerName;
    private BigDecimal price;
    private long daysOverdue;
}
