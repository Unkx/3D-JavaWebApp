package com.printplatform.dto;

import com.printplatform.model.Listing;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminListingDto {
    private String id;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private String ownerEmail;
    private String ownerFirstName;
    private String ownerLastName;
    private BigDecimal maxBudget;

    public AdminListingDto(Listing l) {
        this.id            = l.getId().toString();
        this.title         = l.getTitle();
        this.status        = l.getStatus().name();
        this.createdAt     = l.getCreatedAt();
        this.ownerEmail    = l.getUser().getEmail();
        this.ownerFirstName = l.getUser().getFirstName();
        this.ownerLastName  = l.getUser().getLastName();
        this.maxBudget     = l.getMaxBudget();
    }

    public String getId()            { return id; }
    public String getTitle()         { return title; }
    public String getStatus()        { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getOwnerEmail()    { return ownerEmail; }
    public String getOwnerFirstName() { return ownerFirstName; }
    public String getOwnerLastName()  { return ownerLastName; }
    public BigDecimal getMaxBudget() { return maxBudget; }
}
