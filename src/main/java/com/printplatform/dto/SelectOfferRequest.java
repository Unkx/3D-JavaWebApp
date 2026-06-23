package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class SelectOfferRequest {
    @NotBlank(message = "Paczkomat odbioru jest wymagany")
    private String receiverPaczkomat;

    public String getReceiverPaczkomat() { return receiverPaczkomat; }
    public void setReceiverPaczkomat(String receiverPaczkomat) { this.receiverPaczkomat = receiverPaczkomat; }
}
