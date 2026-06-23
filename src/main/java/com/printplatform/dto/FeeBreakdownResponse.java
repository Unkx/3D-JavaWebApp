package com.printplatform.dto;

import java.math.BigDecimal;

public class FeeBreakdownResponse {
    private BigDecimal contractorPrice;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFee;
    private BigDecimal shippingPrice;
    private String parcelSize;
    private BigDecimal totalPrice;

    public FeeBreakdownResponse(BigDecimal contractorPrice, BigDecimal platformFeePercent,
                                BigDecimal platformFee, BigDecimal shippingPrice,
                                String parcelSize, BigDecimal totalPrice) {
        this.contractorPrice = contractorPrice;
        this.platformFeePercent = platformFeePercent;
        this.platformFee = platformFee;
        this.shippingPrice = shippingPrice;
        this.parcelSize = parcelSize;
        this.totalPrice = totalPrice;
    }

    public BigDecimal getContractorPrice() { return contractorPrice; }
    public BigDecimal getPlatformFeePercent() { return platformFeePercent; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getShippingPrice() { return shippingPrice; }
    public String getParcelSize() { return parcelSize; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}
