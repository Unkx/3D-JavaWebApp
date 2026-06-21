package com.printplatform.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CreateConversationRequest {
    @NotNull(message = "Identyfikator zlecenia jest wymagany")
    private UUID listingId;

    private UUID otherUserId;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public UUID getOtherUserId() { return otherUserId; }
    public void setOtherUserId(UUID otherUserId) { this.otherUserId = otherUserId; }
}
