package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class RedeemCodeRequest {

    @NotBlank(message = "Kod jest wymagany")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
