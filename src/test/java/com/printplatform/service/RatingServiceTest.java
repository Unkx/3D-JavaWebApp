package com.printplatform.service;

import com.printplatform.dto.RatingDto;
import com.printplatform.dto.UserRatingsDto;
import com.printplatform.model.*;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.RatingRepository;
import com.printplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private OfferRepository offerRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserDisplayNameService userDisplayNameService;

    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository, offerRepository, userRepository, userDisplayNameService);
    }

    private User buildUser() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setRole(Role.USER);
        return u;
    }

    private Offer buildOffer(User seller, User buyer, OfferStatus status) {
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setUser(buyer);
        listing.setTitle("Test listing");

        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        offer.setListing(listing);
        offer.setUser(seller);
        offer.setPrice(BigDecimal.TEN);
        offer.setStatus(status);
        return offer;
    }

    @Test
    void createRating_buyerRatesSeller_savesWithSellerAsRatedUser() {
        User seller = buildUser();
        User buyer = buildUser();
        Offer offer = buildOffer(seller, buyer, OfferStatus.DELIVERED);

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(ratingRepository.existsByOfferIdAndRaterId(offer.getId(), buyer.getId())).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> {
            Rating r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(userRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));
        lenient().when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        lenient().when(userDisplayNameService.resolve(any(User.class))).thenReturn("Jan K.");

        RatingDto dto = ratingService.createRating(buyer, offer.getId(), 5, "Świetna robota!");

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        Rating saved = captor.getValue();

        assertThat(saved.getRaterId()).isEqualTo(buyer.getId());
        assertThat(saved.getRatedUserId()).isEqualTo(seller.getId());
        assertThat(saved.getStars()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("Świetna robota!");
        assertThat(saved.getModerationStatus()).isEqualTo(RatingModerationStatus.VISIBLE);
        assertThat(dto.getStars()).isEqualTo(5);
    }

    @Test
    void createRating_sellerRatesBuyer_savesWithBuyerAsRatedUser() {
        User seller = buildUser();
        User buyer = buildUser();
        Offer offer = buildOffer(seller, buyer, OfferStatus.DELIVERED);

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(ratingRepository.existsByOfferIdAndRaterId(offer.getId(), seller.getId())).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> {
            Rating r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(userRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));
        lenient().when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        lenient().when(userDisplayNameService.resolve(any(User.class))).thenReturn("Jan K.");

        ratingService.createRating(seller, offer.getId(), 4, null);

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRatedUserId()).isEqualTo(buyer.getId());
        assertThat(captor.getValue().getComment()).isNull();
    }

    @Test
    void createRating_offerNotDelivered_throwsBadRequest() {
        User seller = buildUser();
        User buyer = buildUser();
        Offer offer = buildOffer(seller, buyer, OfferStatus.SHIPPED);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> ratingService.createRating(buyer, offer.getId(), 5, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ratingRepository);
    }

    @Test
    void createRating_notAPartyToTheOffer_throwsForbidden() {
        User seller = buildUser();
        User buyer = buildUser();
        User stranger = buildUser();
        Offer offer = buildOffer(seller, buyer, OfferStatus.DELIVERED);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> ratingService.createRating(stranger, offer.getId(), 5, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createRating_alreadyRated_throwsBadRequest() {
        User seller = buildUser();
        User buyer = buildUser();
        Offer offer = buildOffer(seller, buyer, OfferStatus.DELIVERED);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(ratingRepository.existsByOfferIdAndRaterId(offer.getId(), buyer.getId())).thenReturn(true);

        assertThatThrownBy(() -> ratingService.createRating(buyer, offer.getId(), 5, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void createRating_selfOffer_throwsBadRequest() {
        User self = buildUser();
        Offer offer = buildOffer(self, self, OfferStatus.DELIVERED);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> ratingService.createRating(self, offer.getId(), 5, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ratingRepository);
    }

    @Test
    void getUserRatings_averagesOnlyVisibleRatings() {
        UUID ratedUserId = UUID.randomUUID();
        Rating visible1 = new Rating();
        visible1.setId(UUID.randomUUID());
        visible1.setOfferId(UUID.randomUUID());
        visible1.setRaterId(UUID.randomUUID());
        visible1.setRatedUserId(ratedUserId);
        visible1.setStars(5);

        Rating visible2 = new Rating();
        visible2.setId(UUID.randomUUID());
        visible2.setOfferId(UUID.randomUUID());
        visible2.setRaterId(UUID.randomUUID());
        visible2.setRatedUserId(ratedUserId);
        visible2.setStars(3);

        when(ratingRepository.findByRatedUserIdAndModerationStatus(ratedUserId, RatingModerationStatus.VISIBLE))
                .thenReturn(java.util.List.of(visible1, visible2));
        org.springframework.data.domain.Page<Rating> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(visible1, visible2));
        when(ratingRepository.findByRatedUserIdAndModerationStatus(
                eq(ratedUserId), eq(RatingModerationStatus.VISIBLE), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of());
        lenient().when(userDisplayNameService.resolve(any(User.class))).thenReturn("Użytkownik");

        UserRatingsDto result = ratingService.getUserRatings(ratedUserId, 0, 20);

        assertThat(result.getSummary().getAverageStars()).isEqualTo(4.0);
        assertThat(result.getSummary().getCount()).isEqualTo(2);
        assertThat(result.getRatings().getContent()).hasSize(2);
    }

    @Test
    void getUserRatings_noRatings_averageIsNull() {
        UUID ratedUserId = UUID.randomUUID();
        when(ratingRepository.findByRatedUserIdAndModerationStatus(ratedUserId, RatingModerationStatus.VISIBLE))
                .thenReturn(java.util.List.of());
        when(ratingRepository.findByRatedUserIdAndModerationStatus(
                eq(ratedUserId), eq(RatingModerationStatus.VISIBLE), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        UserRatingsDto result = ratingService.getUserRatings(ratedUserId, 0, 20);

        assertThat(result.getSummary().getAverageStars()).isNull();
        assertThat(result.getSummary().getCount()).isZero();
    }

    @Test
    void getUserRatings_resolvesRaterDisplayNameForEachRating() {
        UUID ratedUserId = UUID.randomUUID();
        User rater = buildUser();
        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        rating.setOfferId(UUID.randomUUID());
        rating.setRaterId(rater.getId());
        rating.setRatedUserId(ratedUserId);
        rating.setStars(5);

        when(ratingRepository.findByRatedUserIdAndModerationStatus(ratedUserId, RatingModerationStatus.VISIBLE))
                .thenReturn(java.util.List.of(rating));
        org.springframework.data.domain.Page<Rating> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(rating));
        when(ratingRepository.findByRatedUserIdAndModerationStatus(
                eq(ratedUserId), eq(RatingModerationStatus.VISIBLE), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of(rater));
        when(userDisplayNameService.resolve(rater)).thenReturn("Swift42");

        UserRatingsDto result = ratingService.getUserRatings(ratedUserId, 0, 20);

        assertThat(result.getRatings().getContent().get(0).getRaterDisplayName()).isEqualTo("Swift42");
    }
}
