package com.printplatform.repository;

import com.printplatform.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOfferId(UUID offerId);
    List<Payment> findByCreatedAtAfter(LocalDateTime since);
}
