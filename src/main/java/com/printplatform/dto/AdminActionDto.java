package com.printplatform.dto;

import com.printplatform.model.AdminAction;
import java.time.LocalDateTime;

public class AdminActionDto {
    private String id;
    private String adminEmail;
    private String actionType;
    private String targetType;
    private String targetId;
    private String details;
    private LocalDateTime createdAt;

    public AdminActionDto(AdminAction a) {
        this.id         = a.getId().toString();
        this.adminEmail = a.getAdminEmail();
        this.actionType = a.getActionType().name();
        this.targetType = a.getTargetType();
        this.targetId   = a.getTargetId().toString();
        this.details    = a.getDetails();
        this.createdAt  = a.getCreatedAt();
    }

    public String getId()          { return id; }
    public String getAdminEmail()  { return adminEmail; }
    public String getActionType()  { return actionType; }
    public String getTargetType()  { return targetType; }
    public String getTargetId()    { return targetId; }
    public String getDetails()     { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
