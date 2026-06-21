package com.printplatform.repository;

import com.printplatform.model.OrderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrderTrackingRepository extends JpaRepository<OrderTracking, UUID> {
    Optional<OrderTracking> findByOfferId(UUID offerId);
}
