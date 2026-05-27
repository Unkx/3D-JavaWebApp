package com.printplatform.repository;

import com.printplatform.model.Offer;
import com.printplatform.model.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {
    List<Offer> findByListingId(UUID listingId);
    List<Offer> findByListingIdAndStatus(UUID listingId, OfferStatus status);
    List<Offer> findByUserId(UUID userId);
}