package com.printplatform.controller;

import com.printplatform.dto.CreateShipmentRequest;
import com.printplatform.model.*;
import com.printplatform.repository.OfferRepository;
import com.printplatform.service.PaymentService;
import com.printplatform.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final PaymentService paymentService;
    private final OfferRepository offerRepository;

    public ShipmentController(ShipmentService shipmentService,
                              PaymentService paymentService,
                              OfferRepository offerRepository) {
        this.shipmentService = shipmentService;
        this.paymentService = paymentService;
        this.offerRepository = offerRepository;
    }

    @PostMapping("/offer/{offerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Shipment createShipment(@PathVariable UUID offerId,
                                   @Valid @RequestBody CreateShipmentRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        if (!offer.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko sprzedawca może utworzyć przesyłkę");
        }
        if (offer.getStatus() != OfferStatus.PRINTING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Przesyłkę można utworzyć tylko w statusie PRINTING");
        }

        Payment payment = paymentService.getByOfferId(offerId);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak płatności dla tej oferty");
        }

        return shipmentService.createShipment(payment, offer, request.getSenderPaczkomat());
    }

    @PutMapping("/{shipmentId}/advance")
    public Shipment advanceStatus(@PathVariable UUID shipmentId,
                                  @AuthenticationPrincipal User currentUser) {
        Shipment shipment = shipmentService.advanceStatus(shipmentId);

        if (shipment.getStatus() == ShipmentStatus.DISPATCHED) {
            Offer offer = shipment.getOffer();
            offer.setStatus(OfferStatus.SHIPPED);
            offerRepository.save(offer);
        }

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            Offer offer = shipment.getOffer();
            offer.setStatus(OfferStatus.DELIVERED);
            offerRepository.save(offer);
            paymentService.releasePayment(shipment.getPayment().getId());
        }

        return shipment;
    }

    @GetMapping("/offer/{offerId}")
    public Shipment getShipmentForOffer(@PathVariable UUID offerId,
                                        @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
        boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());
        if (!isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
        }

        Shipment shipment = shipmentService.getByOfferId(offerId);
        if (shipment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak przesyłki");
        }
        return shipment;
    }
}
