package com.printplatform.dto;

public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private String userId;

    public AuthResponse(String token, String email, String role, String userId) {
        this.token = token;
        this.email = email;
        this.role = role;
        this.userId = userId;
    }

    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getUserId() { return userId; }
}
