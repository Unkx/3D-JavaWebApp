package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CreateRatingRequest;
import com.printplatform.model.*;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RatingControllerTest extends AbstractControllerTest {

    @Autowired private ListingRepository listingRepository;
    @Autowired private OfferRepository offerRepository;

    private Offer persistDeliveredOffer(User buyer, User seller) {
        Listing listing = new Listing();
        listing.setUser(buyer);
        listing.setTitle("Test listing");
        Listing savedListing = listingRepository.save(listing);

        Offer offer = new Offer();
        offer.setListing(savedListing);
        offer.setUser(seller);
        offer.setPrice(BigDecimal.TEN);
        offer.setPrintingTimeHours(2.0);
        offer.setFilamentGrams(100);
        offer.setStatus(OfferStatus.DELIVERED);
        return offerRepository.save(offer);
    }

    @Test
    void createRating_buyerRatingSeller_returns201() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistDeliveredOffer(buyer, seller);

        CreateRatingRequest request = new CreateRatingRequest();
        request.setStars(5);
        request.setComment("Bardzo dobra jakość!");

        mockMvc.perform(post("/api/offers/" + offer.getId() + "/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stars").value(5))
                .andExpect(jsonPath("$.ratedUserId").value(seller.getId().toString()));
    }

    @Test
    void createRating_invalidStars_returns400() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistDeliveredOffer(buyer, seller);

        CreateRatingRequest request = new CreateRatingRequest();
        request.setStars(6);

        mockMvc.perform(post("/api/offers/" + offer.getId() + "/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRatingsForOffer_partyToOffer_returns200() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistDeliveredOffer(buyer, seller);

        mockMvc.perform(get("/api/offers/" + offer.getId() + "/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getRatingsForOffer_notAParty_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Offer offer = persistDeliveredOffer(buyer, seller);

        mockMvc.perform(get("/api/offers/" + offer.getId() + "/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserRatings_noAuth_returns200() throws Exception {
        User seller = persistUser();

        mockMvc.perform(get("/api/users/" + seller.getId() + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings.content").isArray());
    }
}
