package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CreateListingRequest;
import com.printplatform.dto.UpdateListingRequest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ListingControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;

    private Listing persistListing(User owner, ListingStatus status) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Test listing " + UUID.randomUUID());
        listing.setDescription("Some description");
        listing.setRequiredMaterial("PLA");
        listing.setMaxBudget(BigDecimal.valueOf(100));
        listing.setStatus(status);
        return listingRepository.save(listing);
    }

    @Test
    void getOpenListings_returnsPageOfOpenListings() throws Exception {
        User owner = persistUser();
        persistListing(owner, ListingStatus.OPEN);
        persistListing(owner, ListingStatus.CLOSED);

        mockMvc.perform(get("/api/listings").param("page", "0").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getListing_found_returns200() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(get("/api/listings/{id}", listing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(listing.getId().toString()))
                .andExpect(jsonPath("$.title").value(listing.getTitle()));
    }

    @Test
    void getListing_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/listings/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyListings_authenticated_returnsOwnListings() throws Exception {
        User owner = persistUser();
        persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(get("/api/listings/my")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void getMyListings_noAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/listings/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListing_valid_returns201() throws Exception {
        User owner = persistUser();

        CreateListingRequest request = new CreateListingRequest();
        request.setTitle("New printable thing");
        request.setDescription("A nice description");
        request.setRequiredMaterial("PETG");
        request.setMaxBudget(BigDecimal.valueOf(50));

        mockMvc.perform(post("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New printable thing"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createListing_invalidBody_returns400() throws Exception {
        User owner = persistUser();

        CreateListingRequest request = new CreateListingRequest();
        request.setTitle(""); // blank -> @NotBlank violation
        request.setRequiredMaterial("PLA");

        mockMvc.perform(post("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateListing_owner_returns200() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        UpdateListingRequest request = new UpdateListingRequest();
        request.setDescription("Updated description");
        request.setRequiredMaterial("ABS");

        mockMvc.perform(patch("/api/listings/{id}", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void updateListing_nonOwner_returns403() throws Exception {
        User owner = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        UpdateListingRequest request = new UpdateListingRequest();
        request.setDescription("Hijacked");

        mockMvc.perform(patch("/api/listings/{id}", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateListing_notFound_returns404() throws Exception {
        User owner = persistUser();

        UpdateListingRequest request = new UpdateListingRequest();
        request.setDescription("does not matter");

        mockMvc.perform(patch("/api/listings/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void closeListing_owner_returns200AndClosesListing() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(put("/api/listings/{id}/close", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void deleteListing_owner_returns204() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(delete("/api/listings/{id}", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteListing_nonOwner_returns403() throws Exception {
        User owner = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(delete("/api/listings/{id}", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteListing_adminCanDeleteOthersListing_returns204() throws Exception {
        User owner = persistUser();
        User admin = persistUser(Role.ADMIN);
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(delete("/api/listings/{id}", listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void downloadStl_noFileNoUrl_returns404() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(get("/api/listings/{id}/stl", listing.getId()))
                .andExpect(status().isNotFound());
    }

    /**
     * Regression test for a fixed bug: Listing.java's `user` field (the listing owner) now
     * carries @JsonIgnoreProperties({"password", ...}) matching Offer.user, Payment.buyer/seller,
     * Conversation.participant1/2 and Message.sender, so the owner's bcrypt password hash must
     * never appear in a serialized Listing response.
     */
    @Test
    void getListing_doesNotLeakOwnerPasswordHash() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        mockMvc.perform(get("/api/listings/{id}", listing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }
}
