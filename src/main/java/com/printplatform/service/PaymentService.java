package com.printplatform.service;

import com.printplatform.model.*;
import com.printplatform.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BigDecimal feePercent;
    private final BigDecimal priceA;
    private final BigDecimal priceB;
    private final BigDecimal priceC;

    public PaymentService(PaymentRepository paymentRepository,
                          @Value("${platform.fee.percent:10.0}") BigDecimal feePercent,
                          @Value("${shipping.price.a:12.99}") BigDecimal priceA,
                          @Value("${shipping.price.b:14.99}") BigDecimal priceB,
                          @Value("${shipping.price.c:19.99}") BigDecimal priceC) {
        this.paymentRepository = paymentRepository;
        this.feePercent = feePercent;
        this.priceA = priceA;
        this.priceB = priceB;
        this.priceC = priceC;
    }

    public String getParcelSize(String estimatorSize) {
        if (estimatorSize == null) return "B";
        return switch (estimatorSize.toLowerCase()) {
            case "small" -> "A";
            case "large" -> "C";
            default -> "B";
        };
    }

    public BigDecimal getShippingPrice(String parcelSize) {
        return switch (parcelSize) {
            case "A" -> priceA;
            case "C" -> priceC;
            default -> priceB;
        };
    }

    public BigDecimal getFeePercent() {
        return feePercent;
    }

    public Payment createPayment(Offer offer, User buyer, String receiverPaczkomat) {
        if (paymentRepository.findByOfferId(offer.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Płatność już istnieje dla tej oferty");
        }

        String parcelSize = getParcelSize(offer.getListing().getEstimatorSize());
        BigDecimal contractorPrice = offer.getPrice();
        BigDecimal fee = contractorPrice.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal shipping = getShippingPrice(parcelSize);
        BigDecimal total = contractorPrice.add(fee).add(shipping);

        Payment payment = new Payment();
        payment.setOffer(offer);
        payment.setBuyer(buyer);
        payment.setSeller(offer.getUser());
        payment.setContractorPrice(contractorPrice);
        payment.setPlatformFeePercent(feePercent);
        payment.setPlatformFee(fee);
        payment.setShippingPrice(shipping);
        payment.setParcelSize(parcelSize);
        payment.setTotalPrice(total);
        payment.setReceiverPaczkomat(receiverPaczkomat);
        payment.setStatus(PaymentStatus.HELD);
        payment.setPaidAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    public Payment releasePayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Płatność nie istnieje"));
        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Płatność nie jest w stanie HELD");
        }
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    public Payment getByOfferId(UUID offerId) {
        return paymentRepository.findByOfferId(offerId).orElse(null);
    }
}
