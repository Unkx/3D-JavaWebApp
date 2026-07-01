package com.printplatform.service;

import com.printplatform.model.Offer;
import com.printplatform.model.Payment;
import com.printplatform.model.Shipment;
import com.printplatform.model.ShipmentStatus;
import com.printplatform.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    private ShipmentService shipmentService;

    @BeforeEach
    void setUp() {
        shipmentService = new ShipmentService(shipmentRepository);
    }

    private Payment buildPayment(String receiverPaczkomat, String parcelSize) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setReceiverPaczkomat(receiverPaczkomat);
        payment.setParcelSize(parcelSize);
        return payment;
    }

    private Offer buildOffer() {
        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        return offer;
    }

    @Test
    void createShipment_noExisting_createsLabelWithMockTrackingNumberAndFinalLabelUrl() {
        Offer offer = buildOffer();
        Payment payment = buildPayment("RECEIVER-01", "B");

        when(shipmentRepository.findByOfferId(offer.getId())).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        Shipment shipment = shipmentService.createShipment(payment, offer, "SENDER-01");

        assertThat(shipment.getTrackingNumber()).startsWith("INPOST-MOCK-");
        assertThat(shipment.getLabelUrl()).isEqualTo("/api/shipments/" + shipment.getId() + "/label");
        assertThat(shipment.getSenderPaczkomat()).isEqualTo("SENDER-01");
        assertThat(shipment.getReceiverPaczkomat()).isEqualTo("RECEIVER-01");
        assertThat(shipment.getParcelSize()).isEqualTo("B");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.LABEL_CREATED);
        assertThat(shipment.getPayment()).isEqualTo(payment);
        assertThat(shipment.getOffer()).isEqualTo(offer);

        verify(shipmentRepository, times(2)).save(any(Shipment.class));
    }

    @Test
    void createShipment_alreadyExists_throwsConflict() {
        Offer offer = buildOffer();
        Payment payment = buildPayment("RECEIVER-01", "B");

        when(shipmentRepository.findByOfferId(offer.getId())).thenReturn(Optional.of(new Shipment()));

        assertThatThrownBy(() -> shipmentService.createShipment(payment, offer, "SENDER-01"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void advanceStatus_labelCreated_movesToDispatched() {
        assertTransition(ShipmentStatus.LABEL_CREATED, ShipmentStatus.DISPATCHED);
    }

    @Test
    void advanceStatus_dispatched_movesToInTransit() {
        assertTransition(ShipmentStatus.DISPATCHED, ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void advanceStatus_inTransit_movesToReadyToPickup() {
        assertTransition(ShipmentStatus.IN_TRANSIT, ShipmentStatus.READY_TO_PICKUP);
    }

    @Test
    void advanceStatus_readyToPickup_movesToDelivered() {
        assertTransition(ShipmentStatus.READY_TO_PICKUP, ShipmentStatus.DELIVERED);
    }

    private void assertTransition(ShipmentStatus from, ShipmentStatus to) {
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setStatus(from);

        when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        Shipment result = shipmentService.advanceStatus(shipmentId);

        assertThat(result.getStatus()).isEqualTo(to);
    }

    @Test
    void advanceStatus_delivered_isTerminalAndThrowsBadRequest() {
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setStatus(ShipmentStatus.DELIVERED);

        when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.advanceStatus(shipmentId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void advanceStatus_notFound_throwsNotFound() {
        UUID shipmentId = UUID.randomUUID();
        when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.advanceStatus(shipmentId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getByOfferId_found_returnsShipment() {
        UUID offerId = UUID.randomUUID();
        Shipment shipment = new Shipment();
        when(shipmentRepository.findByOfferId(offerId)).thenReturn(Optional.of(shipment));

        assertThat(shipmentService.getByOfferId(offerId)).isEqualTo(shipment);
    }

    @Test
    void getByOfferId_notFound_returnsNull() {
        UUID offerId = UUID.randomUUID();
        when(shipmentRepository.findByOfferId(offerId)).thenReturn(Optional.empty());

        assertThat(shipmentService.getByOfferId(offerId)).isNull();
    }
}
