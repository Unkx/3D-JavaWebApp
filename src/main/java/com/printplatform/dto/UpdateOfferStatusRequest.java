package com.printplatform.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateOfferStatusRequest {
    @NotNull(message = "Status jest wymagany")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
