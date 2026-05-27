package com.printplatform.dto;

import java.time.LocalDateTime;

public class UserProfileDto {
    private String id;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private long listingsCount;
    private long offersCount;

    public UserProfileDto(String id, String email, String role, LocalDateTime createdAt,
                          long listingsCount, long offersCount) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.listingsCount = listingsCount;
        this.offersCount = offersCount;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getListingsCount() { return listingsCount; }
    public long getOffersCount() { return offersCount; }
}
