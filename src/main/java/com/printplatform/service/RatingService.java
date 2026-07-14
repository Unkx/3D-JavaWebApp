package com.printplatform.service;

import com.printplatform.dto.RatingDto;
import com.printplatform.model.Offer;
import com.printplatform.model.Rating;
import com.printplatform.model.User;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.RatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OfferRepository offerRepository;

    public RatingService(RatingRepository ratingRepository, OfferRepository offerRepository) {
        this.ratingRepository = ratingRepository;
        this.offerRepository = offerRepository;
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
        return new RatingDto(ratingRepository.save(rating));
    }
}
