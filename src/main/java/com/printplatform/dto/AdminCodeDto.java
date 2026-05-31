package com.printplatform.dto;

import com.printplatform.model.AdminCode;

import java.time.LocalDateTime;

public class AdminCodeDto {
    private String code;
    private boolean used;
    private String createdByEmail;
    private String usedByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;

    public AdminCodeDto(AdminCode c) {
        this.code = c.getCode();
        this.used = c.isUsed();
        this.createdByEmail = c.getCreatedByEmail();
        this.usedByEmail = c.getUsedByEmail();
        this.createdAt = c.getCreatedAt();
        this.redeemedAt = c.getRedeemedAt();
    }

    public String getCode() { return code; }
    public boolean isUsed() { return used; }
    public String getCreatedByEmail() { return createdByEmail; }
    public String getUsedByEmail() { return usedByEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRedeemedAt() { return redeemedAt; }
}
