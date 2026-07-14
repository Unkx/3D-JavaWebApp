package com.printplatform.repository;

import com.printplatform.model.Listing;
import com.printplatform.model.ListingModerationStatus;
import com.printplatform.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    List<Listing> findByStatus(ListingStatus status);
    Page<Listing> findByStatusAndModerationStatus(ListingStatus status, ListingModerationStatus moderationStatus, Pageable pageable);
    List<Listing> findByUserId(UUID userId);

    @Query("SELECT l FROM Listing l WHERE l.status = :status AND l.moderationStatus = :moderationStatus AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(l.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Listing> searchByStatusAndModerationStatus(@Param("status") ListingStatus status,
                                                     @Param("moderationStatus") ListingModerationStatus moderationStatus,
                                                     @Param("q") String q, Pageable pageable);
}