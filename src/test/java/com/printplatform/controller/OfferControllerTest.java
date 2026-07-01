package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CreateOfferRequest;
import com.printplatform.dto.SelectOfferRequest;
import com.printplatform.dto.UpdateOfferStatusRequest;
import com.printplatform.dto.UpdateTrackingRequest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Offer;
import com.printplatform.model.OfferStatus;
import com.printplatform.model.OrderTracking;
import com.printplatform.model.Payment;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.OrderTrackingRepository;
import com.printplatform.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class OfferControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderTrackingRepository orderTrackingRepository;

    private Listing persistListing(User owner, ListingStatus status) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Listing " + UUID.randomUUID());
        listing.setRequiredMaterial("PLA");
        listing.setStatus(status);
        listing.setEstimatorSize("medium");
        return listingRepository.save(listing);
    }

    private Offer persistOffer(Listing listing, User seller, OfferStatus status) {
        Offer offer = new Offer();
        offer.setListing(listing);
        offer.setUser(seller);
        offer.setPrice(BigDecimal.valueOf(80));
        offer.setPrintingTimeHours(3.0);
        offer.setFilamentGrams(120);
        offer.setPrinterModel("Prusa MK3S");
        offer.setStatus(status);
        return offerRepository.save(offer);
    }

    @Test
    void getOffersForListing_returnsOffers() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        persistOffer(listing, seller, OfferStatus.PENDING);

        mockMvc.perform(get("/api/offers/listing/{listingId}", listing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].listing.id").value(listing.getId().toString()));
    }

    @Test
    void getMyOffers_authenticated_returns200() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        persistOffer(listing, seller, OfferStatus.PENDING);

        mockMvc.perform(get("/api/offers/my")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void getMyOffers_noAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/offers/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOffer_valid_returns201() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);

        CreateOfferRequest request = new CreateOfferRequest();
        request.setListingId(listing.getId());
        request.setPrice(BigDecimal.valueOf(99));
        request.setPrintingTimeHours(5.0);
        request.setFilamentGrams(200);
        request.setPrinterModel("Ender 3");

        mockMvc.perform(post("/api/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOffer_onOwnListing_returns400() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner, ListingStatus.OPEN);

        CreateOfferRequest request = new CreateOfferRequest();
        request.setListingId(listing.getId());
        request.setPrice(BigDecimal.valueOf(50));
        request.setPrintingTimeHours(2.0);
        request.setFilamentGrams(100);
        request.setPrinterModel("Ender 3");

        mockMvc.perform(post("/api/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOffer_listingNotFound_returns404() throws Exception {
        User seller = persistUser();

        CreateOfferRequest request = new CreateOfferRequest();
        request.setListingId(UUID.randomUUID());
        request.setPrice(BigDecimal.valueOf(50));
        request.setPrintingTimeHours(2.0);
        request.setFilamentGrams(100);
        request.setPrinterModel("Ender 3");

        mockMvc.perform(post("/api/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void selectOffer_owner_returns200AndCreatesPayment() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.PENDING);

        SelectOfferRequest request = new SelectOfferRequest();
        request.setReceiverPaczkomat("WAW01A");

        mockMvc.perform(put("/api/offers/{offerId}/select", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED"));

        Payment payment = paymentRepository.findByOfferId(offer.getId()).orElseThrow();
        assertThat(payment.getReceiverPaczkomat()).isEqualTo("WAW01A");

        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.AWARDED);
    }

    @Test
    void selectOffer_nonOwner_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.PENDING);

        SelectOfferRequest request = new SelectOfferRequest();
        request.setReceiverPaczkomat("WAW01A");

        mockMvc.perform(put("/api/offers/{offerId}/select", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void selectOffer_notFound_returns404() throws Exception {
        User buyer = persistUser();

        SelectOfferRequest request = new SelectOfferRequest();
        request.setReceiverPaczkomat("WAW01A");

        mockMvc.perform(put("/api/offers/{offerId}/select", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void feeBreakdown_returns200WithComputedFee() throws Exception {
        mockMvc.perform(get("/api/offers/fee-breakdown")
                        .param("price", "100")
                        .param("estimatorSize", "small"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelSize").value("A"))
                .andExpect(jsonPath("$.contractorPrice").value(100));
    }

    @Test
    void updateOfferStatus_sellerAdvancesSelectedToPrinting_returns200() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.SELECTED);

        UpdateOfferStatusRequest request = new UpdateOfferStatusRequest();
        request.setStatus("PRINTING");

        mockMvc.perform(put("/api/offers/{offerId}/status", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRINTING"));

        assertThat(orderTrackingRepository.findByOfferId(offer.getId())).isPresent();
    }

    @Test
    void updateOfferStatus_invalidTransition_returns400() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.PENDING);

        UpdateOfferStatusRequest request = new UpdateOfferStatusRequest();
        request.setStatus("SHIPPED"); // PENDING has no valid transitions configured

        mockMvc.perform(put("/api/offers/{offerId}/status", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTracking_sellerAddsTracking_advancesToShipped() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.PRINTING);
        OrderTracking tracking = new OrderTracking();
        tracking.setOffer(offer);
        orderTrackingRepository.save(tracking);

        UpdateTrackingRequest request = new UpdateTrackingRequest();
        request.setCarrierName("InPost");
        request.setTrackingNumber("TRACK-123");

        mockMvc.perform(put("/api/offers/{offerId}/tracking", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-123"));

        Offer reloaded = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OfferStatus.SHIPPED);
    }

    @Test
    void getTracking_neitherSellerNorBuyer_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(buyer, ListingStatus.OPEN);
        Offer offer = persistOffer(listing, seller, OfferStatus.PRINTING);
        OrderTracking tracking = new OrderTracking();
        tracking.setOffer(offer);
        orderTrackingRepository.save(tracking);

        mockMvc.perform(get("/api/offers/{offerId}/tracking", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }
}
