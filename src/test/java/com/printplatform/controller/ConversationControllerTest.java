package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CreateConversationRequest;
import com.printplatform.dto.SendMessageRequest;
import com.printplatform.model.Conversation;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Message;
import com.printplatform.model.User;
import com.printplatform.repository.ConversationRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ConversationControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;

    private Listing persistListing(User owner) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Listing " + UUID.randomUUID());
        listing.setRequiredMaterial("PLA");
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    private Conversation persistConversation(Listing listing, User p1, User p2) {
        Conversation conv = new Conversation();
        conv.setListing(listing);
        conv.setParticipant1(p1);
        conv.setParticipant2(p2);
        return conversationRepository.save(conv);
    }

    @Test
    void createOrGet_nonOwnerStartsConversationWithListingOwner_returns200() throws Exception {
        User owner = persistUser();
        User interested = persistUser();
        Listing listing = persistListing(owner);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setListingId(listing.getId());

        mockMvc.perform(post("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(interested))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.id").value(listing.getId().toString()));
    }

    @Test
    void createOrGet_ownerWithoutOtherUserId_returns400() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setListingId(listing.getId());

        mockMvc.perform(post("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrGet_listingNotFound_returns404() throws Exception {
        User user = persistUser();

        CreateConversationRequest request = new CreateConversationRequest();
        request.setListingId(UUID.randomUUID());

        mockMvc.perform(post("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrGet_noAuth_returns403() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setListingId(UUID.randomUUID());

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyConversations_returns200() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Listing listing = persistListing(owner);
        persistConversation(listing, owner, other);

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].listingId").value(listing.getId().toString()));
    }

    @Test
    void getMessages_participant_returns200() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Listing listing = persistListing(owner);
        Conversation conv = persistConversation(listing, owner, other);

        Message msg = new Message();
        msg.setConversation(conv);
        msg.setSender(other);
        msg.setContent("Hello!");
        messageRepository.save(msg);

        mockMvc.perform(get("/api/conversations/{id}/messages", conv.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello!"));
    }

    @Test
    void getMessages_nonParticipant_returns403() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner);
        Conversation conv = persistConversation(listing, owner, other);

        mockMvc.perform(get("/api/conversations/{id}/messages", conv.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessages_notFound_returns404() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/conversations/{id}/messages", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendMessage_participant_returns201() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Listing listing = persistListing(owner);
        Conversation conv = persistConversation(listing, owner, other);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Can you do it in red?");

        mockMvc.perform(post("/api/conversations/{id}/messages", conv.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Can you do it in red?"));
    }

    @Test
    void sendMessage_invalidBody_returns400() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Listing listing = persistListing(owner);
        Conversation conv = persistConversation(listing, owner, other);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent(""); // blank -> @NotBlank violation

        mockMvc.perform(post("/api/conversations/{id}/messages", conv.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markRead_participant_returns200() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Listing listing = persistListing(owner);
        Conversation conv = persistConversation(listing, owner, other);

        mockMvc.perform(put("/api/conversations/{id}/read", conv.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void getUnreadCount_returns200() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/conversations/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
