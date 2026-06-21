package com.printplatform.repository;

import com.printplatform.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByListingIdAndParticipant2Id(UUID listingId, UUID participant2Id);

    @Query("SELECT c FROM Conversation c WHERE c.participant1.id = :userId OR c.participant2.id = :userId ORDER BY c.createdAt DESC")
    List<Conversation> findByParticipantId(UUID userId);
}
