package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateShipmentRequest {
    @NotBlank(message = "Paczkomat nadania jest wymagany")
    private String senderPaczkomat;

    public String getSenderPaczkomat() { return senderPaczkomat; }
    public void setSenderPaczkomat(String senderPaczkomat) { this.senderPaczkomat = senderPaczkomat; }
}
