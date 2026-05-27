package com.printplatform.controller;

import com.printplatform.dto.UserProfileDto;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;

    public UserController(ListingRepository listingRepository, OfferRepository offerRepository) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
    }

    @GetMapping("/me")
    public UserProfileDto getProfile(@AuthenticationPrincipal User user) {
        long listingsCount = listingRepository.findByUserId(user.getId()).size();
        long offersCount = offerRepository.findByUserId(user.getId()).size();
        return new UserProfileDto(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                listingsCount,
                offersCount
        );
    }
}
