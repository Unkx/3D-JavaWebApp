package com.printplatform.controller;

import com.printplatform.dto.ConversationDto;
import com.printplatform.dto.CreateConversationRequest;
import com.printplatform.dto.SendMessageRequest;
import com.printplatform.model.*;
import com.printplatform.repository.ConversationRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.MessageRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.printplatform.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    public ConversationController(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  ListingRepository listingRepository,
                                  UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public Conversation createOrGet(@Valid @RequestBody CreateConversationRequest request,
                                    @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        boolean isOwner = listing.getUser().getId().equals(user.getId());

        if (isOwner) {
            if (request.getOtherUserId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie możesz rozpocząć rozmowy z samym sobą");
            }
            User otherUser = userRepository.findById(request.getOtherUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
            return conversationRepository.findByListingIdAndParticipant2Id(listing.getId(), otherUser.getId())
                    .orElseGet(() -> {
                        Conversation conv = new Conversation();
                        conv.setListing(listing);
                        conv.setParticipant1(listing.getUser());
                        conv.setParticipant2(otherUser);
                        return conversationRepository.save(conv);
                    });
        }

        return conversationRepository.findByListingIdAndParticipant2Id(listing.getId(), user.getId())
                .orElseGet(() -> {
                    Conversation conv = new Conversation();
                    conv.setListing(listing);
                    conv.setParticipant1(listing.getUser());
                    conv.setParticipant2(user);
                    return conversationRepository.save(conv);
                });
    }

    @GetMapping
    public List<ConversationDto> getMyConversations(@AuthenticationPrincipal User user) {
        List<Conversation> conversations = conversationRepository.findByParticipantId(user.getId());
        return conversations.stream().map(conv -> {
            String otherEmail = conv.getParticipant1().getId().equals(user.getId())
                    ? conv.getParticipant2().getEmail()
                    : conv.getParticipant1().getEmail();
            Message lastMsg = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId());
            long unread = messageRepository.countUnreadInConversation(conv.getId(), user.getId());
            return new ConversationDto(
                    conv.getId(),
                    conv.getListing().getId(),
                    conv.getListing().getTitle(),
                    otherEmail,
                    lastMsg != null ? lastMsg.getContent() : null,
                    lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt(),
                    unread
            );
        }).sorted(Comparator.comparing(ConversationDto::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
          .collect(Collectors.toList());
    }

    @GetMapping("/{id}/messages")
    public List<Message> getMessages(@PathVariable UUID id,
                                     @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message sendMessage(@PathVariable UUID id,
                               @Valid @RequestBody SendMessageRequest request,
                               @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        Message msg = new Message();
        msg.setConversation(conv);
        msg.setSender(user);
        msg.setContent(request.getContent());
        return messageRepository.save(msg);
    }

    @PutMapping("/{id}/read")
    @Transactional
    public void markRead(@PathVariable UUID id,
                         @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        messageRepository.markAllRead(conv.getId(), user.getId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", messageRepository.countTotalUnread(user.getId()));
    }

    private Conversation getConversationIfParticipant(UUID conversationId, User user) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rozmowa nie istnieje"));
        if (!conv.getParticipant1().getId().equals(user.getId())
                && !conv.getParticipant2().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu do tej rozmowy");
        }
        return conv;
    }
}
