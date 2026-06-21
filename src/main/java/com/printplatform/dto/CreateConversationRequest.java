package com.printplatform.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CreateConversationRequest {
    @NotNull(message = "Identyfikator zlecenia jest wymagany")
    private UUID listingId;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
}
