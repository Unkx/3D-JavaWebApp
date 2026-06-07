package com.printplatform.repository;

import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    List<Listing> findByStatus(ListingStatus status);
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);
    List<Listing> findByUserId(UUID userId);
}