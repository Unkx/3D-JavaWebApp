package com.printplatform.repository;

import com.printplatform.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOfferId(UUID offerId);
    Optional<Shipment> findByPaymentId(UUID paymentId);
}
