package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CreateShipmentRequest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Offer;
import com.printplatform.model.OfferStatus;
import com.printplatform.model.Payment;
import com.printplatform.model.PaymentStatus;
import com.printplatform.model.Shipment;
import com.printplatform.model.ShipmentStatus;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.PaymentRepository;
import com.printplatform.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ShipmentControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;

    private Offer persistOffer(User buyer, User seller, OfferStatus status) {
        Listing listing = new Listing();
        listing.setUser(buyer);
        listing.setTitle("Listing " + UUID.randomUUID());
        listing.setRequiredMaterial("PLA");
        listing.setStatus(ListingStatus.AWARDED);
        listing = listingRepository.save(listing);

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

    private Payment persistPayment(Offer offer, User buyer, User seller) {
        Payment payment = new Payment();
        payment.setOffer(offer);
        payment.setBuyer(buyer);
        payment.setSeller(seller);
        payment.setContractorPrice(BigDecimal.valueOf(80));
        payment.setPlatformFeePercent(BigDecimal.valueOf(10));
        payment.setPlatformFee(BigDecimal.valueOf(8));
        payment.setShippingPrice(BigDecimal.valueOf(14.99));
        payment.setParcelSize("B");
        payment.setTotalPrice(BigDecimal.valueOf(102.99));
        payment.setReceiverPaczkomat("WAW01A");
        payment.setStatus(PaymentStatus.HELD);
        payment.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private Shipment persistShipment(Payment payment, Offer offer, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setPayment(payment);
        shipment.setOffer(offer);
        shipment.setTrackingNumber("INPOST-TEST-1");
        shipment.setLabelUrl("/api/shipments/mock-label");
        shipment.setSenderPaczkomat("WAW02B");
        shipment.setReceiverPaczkomat(payment.getReceiverPaczkomat());
        shipment.setParcelSize(payment.getParcelSize());
        shipment.setStatus(status);
        return shipmentRepository.save(shipment);
    }

    @Test
    void createShipment_sellerWithPrintingOffer_returns201() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        persistPayment(offer, buyer, seller);

        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setSenderPaczkomat("WAW02B");

        mockMvc.perform(post("/api/shipments/offer/{offerId}", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LABEL_CREATED"));
    }

    @Test
    void createShipment_nonSeller_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        persistPayment(offer, buyer, seller);

        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setSenderPaczkomat("WAW02B");

        mockMvc.perform(post("/api/shipments/offer/{offerId}", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createShipment_wrongOfferStatus_returns400() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.SELECTED); // not PRINTING
        persistPayment(offer, buyer, seller);

        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setSenderPaczkomat("WAW02B");

        mockMvc.perform(post("/api/shipments/offer/{offerId}", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShipment_offerNotFound_returns404() throws Exception {
        User seller = persistUser();

        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setSenderPaczkomat("WAW02B");

        mockMvc.perform(post("/api/shipments/offer/{offerId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void advanceStatus_labelCreatedToDispatched_returns200AndUpdatesOffer() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        Payment payment = persistPayment(offer, buyer, seller);
        Shipment shipment = persistShipment(payment, offer, ShipmentStatus.LABEL_CREATED);

        mockMvc.perform(put("/api/shipments/{shipmentId}/advance", shipment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        Offer reloaded = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OfferStatus.SHIPPED);
    }

    @Test
    void advanceStatus_sellerCannotConfirmDelivery_returns403AndDoesNotReleasePayment() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.SHIPPED);
        Payment payment = persistPayment(offer, buyer, seller);
        Shipment shipment = persistShipment(payment, offer, ShipmentStatus.READY_TO_PICKUP);

        mockMvc.perform(put("/api/shipments/{shipmentId}/advance", shipment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isForbidden());

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.HELD);
    }

    @Test
    void advanceStatus_buyerConfirmsDelivery_returns200AndReleasesPayment() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.SHIPPED);
        Payment payment = persistPayment(offer, buyer, seller);
        Shipment shipment = persistShipment(payment, offer, ShipmentStatus.READY_TO_PICKUP);

        mockMvc.perform(put("/api/shipments/{shipmentId}/advance", shipment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.RELEASED);
    }

    @Test
    void advanceStatus_neitherSellerNorBuyer_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        Payment payment = persistPayment(offer, buyer, seller);
        Shipment shipment = persistShipment(payment, offer, ShipmentStatus.LABEL_CREATED);

        mockMvc.perform(put("/api/shipments/{shipmentId}/advance", shipment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void advanceStatus_notFound_returns404() throws Exception {
        User seller = persistUser();

        mockMvc.perform(put("/api/shipments/{shipmentId}/advance", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getShipmentForOffer_neitherSellerNorBuyer_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        Payment payment = persistPayment(offer, buyer, seller);
        persistShipment(payment, offer, ShipmentStatus.LABEL_CREATED);

        mockMvc.perform(get("/api/shipments/offer/{offerId}", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getShipmentForOffer_notFound_returns404() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Offer offer = persistOffer(buyer, seller, OfferStatus.PRINTING);
        // no shipment created

        mockMvc.perform(get("/api/shipments/offer/{offerId}", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer)))
                .andExpect(status().isNotFound());
    }
}
