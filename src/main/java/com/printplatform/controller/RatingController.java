package com.printplatform.controller;

import com.printplatform.dto.CreateRatingRequest;
import com.printplatform.dto.RatingDto;
import com.printplatform.model.User;
import com.printplatform.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// No class-level @RequestMapping: this controller will grow a /api/users/{userId}/ratings
// endpoint in Task 3 that doesn't share the /api/offers/{offerId}/ratings prefix — Spring
// concatenates a class-level @RequestMapping with every method path (even one starting with
// "/"), it does not treat a leading slash as an absolute override, so a shared prefix would
// silently break that endpoint. Full paths on every method avoids that trap.
@RestController
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/api/offers/{offerId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingDto createRating(@PathVariable UUID offerId,
                                  @Valid @RequestBody CreateRatingRequest request,
                                  @AuthenticationPrincipal User rater) {
        return ratingService.createRating(rater, offerId, request.getStars(), request.getComment());
    }

    @GetMapping("/api/offers/{offerId}/ratings")
    public List<RatingDto> getRatingsForOffer(@PathVariable UUID offerId,
                                              @AuthenticationPrincipal User currentUser) {
        return ratingService.getRatingsForOffer(currentUser, offerId);
    }
}
