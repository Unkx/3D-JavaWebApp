package com.printplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConversationDto {
    private UUID id;
    private UUID listingId;
    private String listingTitle;
    private String otherParticipantEmail;
    private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    private long unreadCount;

    public ConversationDto() {}

    public ConversationDto(UUID id, UUID listingId, String listingTitle,
                           String otherParticipantEmail, String lastMessageContent,
                           LocalDateTime lastMessageAt, long unreadCount) {
        this.id = id;
        this.listingId = listingId;
        this.listingTitle = listingTitle;
        this.otherParticipantEmail = otherParticipantEmail;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }
    public String getOtherParticipantEmail() { return otherParticipantEmail; }
    public void setOtherParticipantEmail(String otherParticipantEmail) { this.otherParticipantEmail = otherParticipantEmail; }
    public String getLastMessageContent() { return lastMessageContent; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
}
