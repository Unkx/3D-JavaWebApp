package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateTrackingRequest {
    @NotBlank(message = "Nazwa przewoźnika jest wymagana")
    @Size(max = 100, message = "Nazwa przewoźnika jest zbyt długa")
    private String carrierName;

    @NotBlank(message = "Numer przesyłki jest wymagany")
    @Size(max = 100, message = "Numer przesyłki jest zbyt długi")
    private String trackingNumber;

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
}
