package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Offer;
import com.printplatform.model.OfferStatus;
import com.printplatform.model.Payment;
import com.printplatform.model.PaymentStatus;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PaymentControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private Payment persistPayment(User buyer, User seller, PaymentStatus status) {
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
        offer.setStatus(OfferStatus.SELECTED);
        offer = offerRepository.save(offer);

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
        payment.setStatus(status);
        payment.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Test
    void releasePayment_held_returns200AndReleases() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Payment payment = persistPayment(buyer, seller, PaymentStatus.HELD);

        mockMvc.perform(put("/api/payments/{paymentId}/release", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void releasePayment_noAuth_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Payment payment = persistPayment(buyer, seller, PaymentStatus.HELD);

        mockMvc.perform(put("/api/payments/{paymentId}/release", payment.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void releasePayment_notBuyer_returns403() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        User stranger = persistUser();
        Payment payment = persistPayment(buyer, seller, PaymentStatus.HELD);

        mockMvc.perform(put("/api/payments/{paymentId}/release", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/payments/{paymentId}/release", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isForbidden());
    }

    @Test
    void releasePayment_notFound_returns404() throws Exception {
        User someone = persistUser();

        mockMvc.perform(put("/api/payments/{paymentId}/release", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(someone)))
                .andExpect(status().isNotFound());
    }

    @Test
    void releasePayment_alreadyReleased_returns400() throws Exception {
        User buyer = persistUser();
        User seller = persistUser();
        Payment payment = persistPayment(buyer, seller, PaymentStatus.RELEASED);

        mockMvc.perform(put("/api/payments/{paymentId}/release", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(buyer)))
                .andExpect(status().isBadRequest());
    }
}
