package com.printplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPublicProfileDto {
    private String id;
    private String displayName;
    private String city;
    private boolean emailVerified;
    private boolean hasGoogleAuth;
    private boolean hasFacebookAuth;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean hasAvatarData;
    private String avatarUrl;
    private long activeListingsCount;

    public UserPublicProfileDto(String id, String displayName, String city, boolean emailVerified,
                                boolean hasGoogleAuth, boolean hasFacebookAuth, LocalDateTime createdAt,
                                LocalDateTime lastLoginAt, boolean hasAvatarData, String avatarUrl,
                                long activeListingsCount) {
        this.id = id;
        this.displayName = displayName;
        this.city = city;
        this.emailVerified = emailVerified;
        this.hasGoogleAuth = hasGoogleAuth;
        this.hasFacebookAuth = hasFacebookAuth;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
        this.hasAvatarData = hasAvatarData;
        this.avatarUrl = avatarUrl;
        this.activeListingsCount = activeListingsCount;
    }

    public String getId()                  { return id; }
    public String getDisplayName()         { return displayName; }
    public String getCity()                { return city; }
    public boolean isEmailVerified()       { return emailVerified; }
    public boolean isHasGoogleAuth()       { return hasGoogleAuth; }
    public boolean isHasFacebookAuth()     { return hasFacebookAuth; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getLastLoginAt()  { return lastLoginAt; }
    public boolean isHasAvatarData()       { return hasAvatarData; }
    public String getAvatarUrl()           { return avatarUrl; }
    public long getActiveListingsCount()   { return activeListingsCount; }
}
