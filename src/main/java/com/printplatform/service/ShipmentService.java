package com.printplatform.service;

import com.printplatform.model.*;
import com.printplatform.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment createShipment(Payment payment, Offer offer, String senderPaczkomat) {
        if (shipmentRepository.findByOfferId(offer.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Przesyłka już istnieje dla tej oferty");
        }

        String trackingNumber = "INPOST-MOCK-" + ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        Shipment shipment = new Shipment();
        shipment.setPayment(payment);
        shipment.setOffer(offer);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setLabelUrl("/api/shipments/mock-label");
        shipment.setSenderPaczkomat(senderPaczkomat);
        shipment.setReceiverPaczkomat(payment.getReceiverPaczkomat());
        shipment.setParcelSize(payment.getParcelSize());
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        Shipment saved = shipmentRepository.save(shipment);
        saved.setLabelUrl("/api/shipments/" + saved.getId() + "/label");
        return shipmentRepository.save(saved);
    }

    public Shipment getById(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Przesyłka nie istnieje"));
    }

    public Shipment advanceStatus(UUID shipmentId) {
        Shipment shipment = getById(shipmentId);

        Map<ShipmentStatus, ShipmentStatus> transitions = Map.of(
                ShipmentStatus.LABEL_CREATED, ShipmentStatus.DISPATCHED,
                ShipmentStatus.DISPATCHED, ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.IN_TRANSIT, ShipmentStatus.READY_TO_PICKUP,
                ShipmentStatus.READY_TO_PICKUP, ShipmentStatus.DELIVERED
        );

        ShipmentStatus next = transitions.get(shipment.getStatus());
        if (next == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nie można zmienić statusu przesyłki z: " + shipment.getStatus());
        }

        shipment.setStatus(next);
        return shipmentRepository.save(shipment);
    }

    public Shipment getByOfferId(UUID offerId) {
        return shipmentRepository.findByOfferId(offerId).orElse(null);
    }
}
