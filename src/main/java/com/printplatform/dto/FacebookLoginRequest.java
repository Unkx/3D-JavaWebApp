package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class FacebookLoginRequest {

    @NotBlank(message = "Token Facebook jest wymagany")
    private String accessToken;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
