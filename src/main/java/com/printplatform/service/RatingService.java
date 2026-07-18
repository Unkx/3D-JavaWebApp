package com.printplatform.service;

import com.printplatform.dto.PageResponse;
import com.printplatform.dto.RatingDto;
import com.printplatform.dto.RatingSummaryDto;
import com.printplatform.dto.UserRatingsDto;
import com.printplatform.model.Offer;
import com.printplatform.model.Rating;
import com.printplatform.model.RatingModerationStatus;
import com.printplatform.model.User;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.RatingRepository;
import com.printplatform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final UserDisplayNameService userDisplayNameService;

    public RatingService(RatingRepository ratingRepository, OfferRepository offerRepository,
                         UserRepository userRepository, UserDisplayNameService userDisplayNameService) {
        this.ratingRepository = ratingRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.userDisplayNameService = userDisplayNameService;
    }

    /** Creates a one-time rating from `rater` about the other party on a DELIVERED offer. */
    public RatingDto createRating(User rater, UUID offerId, int stars, String comment) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        UUID sellerId = offer.getUser().getId();
        UUID buyerId = offer.getListing().getUser().getId();

        if (sellerId.equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie można ocenić własnego zlecenia");
        }

        boolean isSeller = sellerId.equals(rater.getId());
        boolean isBuyer = buyerId.equals(rater.getId());
        if (!isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
        }

        if (offer.getStatus() != com.printplatform.model.OfferStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ocenić można tylko dostarczone zamówienia");
        }

        if (ratingRepository.existsByOfferIdAndRaterId(offerId, rater.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Już oceniłeś to zamówienie");
        }

        UUID ratedUserId = isSeller ? buyerId : sellerId;

        Rating rating = new Rating();
        rating.setOfferId(offerId);
        rating.setRaterId(rater.getId());
        rating.setRatedUserId(ratedUserId);
        rating.setStars(stars);
        rating.setComment(comment);
        Rating saved = ratingRepository.save(rating);
        return new RatingDto(saved, resolveDisplayName(saved.getRaterId()));
    }

    /** Both ratings for an offer, if they exist — visible only to a party to that offer. */
    public java.util.List<RatingDto> getRatingsForOffer(User currentUser, UUID offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
        boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());
        if (!isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
        }

        return toDtos(ratingRepository.findByOfferId(offerId));
    }

    /** Visible-only average + paged list of ratings a user has received (public). */
    public UserRatingsDto getUserRatings(UUID userId, int page, int size) {
        int safeSize = Math.clamp(size, 1, 50);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        java.util.List<Rating> allVisible =
                ratingRepository.findByRatedUserIdAndModerationStatus(userId, RatingModerationStatus.VISIBLE);
        Double average = allVisible.isEmpty()
                ? null
                : allVisible.stream().mapToInt(Rating::getStars).average().orElseThrow();
        RatingSummaryDto summary = new RatingSummaryDto(average, allVisible.size());

        Page<Rating> pageResult =
                ratingRepository.findByRatedUserIdAndModerationStatus(userId, RatingModerationStatus.VISIBLE, pageable);
        Map<UUID, User> raterCache = buildRaterCache(pageResult.getContent());
        PageResponse<RatingDto> ratingsPage =
                new PageResponse<>(pageResult.map(r -> new RatingDto(r, resolveDisplayName(r, raterCache))));

        return new UserRatingsDto(summary, ratingsPage);
    }

    private List<RatingDto> toDtos(List<Rating> ratings) {
        Map<UUID, User> raterCache = buildRaterCache(ratings);
        return ratings.stream()
                .map(r -> new RatingDto(r, resolveDisplayName(r, raterCache)))
                .toList();
    }

    private Map<UUID, User> buildRaterCache(List<Rating> ratings) {
        List<UUID> raterIds = ratings.stream().map(Rating::getRaterId).distinct().toList();
        Map<UUID, User> cache = new HashMap<>();
        userRepository.findAllById(raterIds).forEach(u -> cache.put(u.getId(), u));
        return cache;
    }

    private String resolveDisplayName(Rating rating, Map<UUID, User> raterCache) {
        User rater = raterCache.get(rating.getRaterId());
        return rater != null ? userDisplayNameService.resolve(rater) : "Użytkownik";
    }

    private String resolveDisplayName(UUID raterId) {
        return userRepository.findById(raterId)
                .map(userDisplayNameService::resolve)
                .orElse("Użytkownik");
    }
}
