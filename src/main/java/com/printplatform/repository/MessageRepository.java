package com.printplatform.repository;

import com.printplatform.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.read = false")
    long countUnreadInConversation(UUID conversationId, UUID userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender.id <> :userId AND m.read = false AND (m.conversation.participant1.id = :userId OR m.conversation.participant2.id = :userId)")
    long countTotalUnread(UUID userId);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.read = false")
    void markAllRead(UUID conversationId, UUID userId);

    Message findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
