package com.printplatform.repository;

import com.printplatform.model.Rating;
import com.printplatform.model.RatingModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    boolean existsByOfferIdAndRaterId(UUID offerId, UUID raterId);
    List<Rating> findByOfferId(UUID offerId);
    Page<Rating> findByRatedUserIdAndModerationStatus(UUID ratedUserId, RatingModerationStatus moderationStatus, Pageable pageable);
    List<Rating> findByRatedUserIdAndModerationStatus(UUID ratedUserId, RatingModerationStatus moderationStatus);
    Page<Rating> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
