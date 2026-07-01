package com.printplatform.service;

import com.printplatform.model.Listing;
import com.printplatform.model.Offer;
import com.printplatform.model.Payment;
import com.printplatform.model.PaymentStatus;
import com.printplatform.model.User;
import com.printplatform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    private static final BigDecimal FEE_PERCENT = new BigDecimal("10.0");
    private static final BigDecimal PRICE_A = new BigDecimal("12.99");
    private static final BigDecimal PRICE_B = new BigDecimal("14.99");
    private static final BigDecimal PRICE_C = new BigDecimal("19.99");

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, FEE_PERCENT, PRICE_A, PRICE_B, PRICE_C);
    }

    private Offer buildOffer(String estimatorSize, BigDecimal price) {
        Listing listing = new Listing();
        listing.setEstimatorSize(estimatorSize);

        User seller = new User();
        seller.setId(UUID.randomUUID());

        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        offer.setListing(listing);
        offer.setUser(seller);
        offer.setPrice(price);
        return offer;
    }

    @Test
    void getParcelSize_null_returnsB() {
        assertThat(paymentService.getParcelSize(null)).isEqualTo("B");
    }

    @Test
    void getParcelSize_small_returnsA() {
        assertThat(paymentService.getParcelSize("small")).isEqualTo("A");
        assertThat(paymentService.getParcelSize("SMALL")).isEqualTo("A");
    }

    @Test
    void getParcelSize_large_returnsC() {
        assertThat(paymentService.getParcelSize("large")).isEqualTo("C");
    }

    @Test
    void getParcelSize_mediumOrUnknown_returnsB() {
        assertThat(paymentService.getParcelSize("medium")).isEqualTo("B");
        assertThat(paymentService.getParcelSize("unknown")).isEqualTo("B");
    }

    @Test
    void getShippingPrice_returnsConfiguredPricesPerSize() {
        assertThat(paymentService.getShippingPrice("A")).isEqualTo(PRICE_A);
        assertThat(paymentService.getShippingPrice("B")).isEqualTo(PRICE_B);
        assertThat(paymentService.getShippingPrice("C")).isEqualTo(PRICE_C);
        assertThat(paymentService.getShippingPrice("X")).isEqualTo(PRICE_B);
    }

    @Test
    void getFeePercent_returnsConfiguredFeePercent() {
        assertThat(paymentService.getFeePercent()).isEqualTo(FEE_PERCENT);
    }

    @Test
    void createPayment_noExistingPayment_computesFeesAndSavesHeldPayment() {
        Offer offer = buildOffer("medium", new BigDecimal("100.00"));
        User buyer = new User();
        buyer.setId(UUID.randomUUID());

        when(paymentRepository.findByOfferId(offer.getId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.createPayment(offer, buyer, "PACZKOMAT01");

        assertThat(payment.getOffer()).isEqualTo(offer);
        assertThat(payment.getBuyer()).isEqualTo(buyer);
        assertThat(payment.getSeller()).isEqualTo(offer.getUser());
        assertThat(payment.getContractorPrice()).isEqualByComparingTo("100.00");
        assertThat(payment.getPlatformFeePercent()).isEqualTo(FEE_PERCENT);
        assertThat(payment.getPlatformFee()).isEqualByComparingTo("10.00");
        assertThat(payment.getShippingPrice()).isEqualByComparingTo("14.99");
        assertThat(payment.getParcelSize()).isEqualTo("B");
        assertThat(payment.getTotalPrice()).isEqualByComparingTo("124.99");
        assertThat(payment.getReceiverPaczkomat()).isEqualTo("PACZKOMAT01");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.HELD);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void createPayment_smallParcel_usesPriceA() {
        Offer offer = buildOffer("small", new BigDecimal("50.00"));
        User buyer = new User();
        buyer.setId(UUID.randomUUID());

        when(paymentRepository.findByOfferId(offer.getId())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.createPayment(offer, buyer, "PACZKOMAT02");

        assertThat(payment.getParcelSize()).isEqualTo("A");
        assertThat(payment.getShippingPrice()).isEqualByComparingTo("12.99");
    }

    @Test
    void createPayment_alreadyExists_throwsConflict() {
        Offer offer = buildOffer("medium", new BigDecimal("100.00"));
        User buyer = new User();

        when(paymentRepository.findByOfferId(offer.getId()))
                .thenReturn(Optional.of(new Payment()));

        assertThatThrownBy(() -> paymentService.createPayment(offer, buyer, "PACZKOMAT01"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void releasePayment_heldPayment_transitionsToReleased() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.HELD);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.releasePayment(paymentId);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(result.getReleasedAt()).isNotNull();
    }

    @Test
    void releasePayment_notFound_throwsNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.releasePayment(paymentId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void releasePayment_notHeld_throwsBadRequest() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.RELEASED);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.releasePayment(paymentId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getByOfferId_found_returnsPayment() {
        UUID offerId = UUID.randomUUID();
        Payment payment = new Payment();
        when(paymentRepository.findByOfferId(offerId)).thenReturn(Optional.of(payment));

        assertThat(paymentService.getByOfferId(offerId)).isEqualTo(payment);
    }

    @Test
    void getByOfferId_notFound_returnsNull() {
        UUID offerId = UUID.randomUUID();
        when(paymentRepository.findByOfferId(offerId)).thenReturn(Optional.empty());

        assertThat(paymentService.getByOfferId(offerId)).isNull();
    }
}
