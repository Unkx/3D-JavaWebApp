package com.printplatform.controller;

import com.printplatform.dto.UpdateProfileRequest;
import com.printplatform.dto.UpdateShippingRequest;
import com.printplatform.dto.UserProfileDto;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;

    public UserController(ListingRepository listingRepository, OfferRepository offerRepository,
                          UserRepository userRepository) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserProfileDto getProfile(@AuthenticationPrincipal User user) {
        return toDto(user);
    }

    @PutMapping("/me")
    public UserProfileDto updateProfile(@AuthenticationPrincipal User user,
                                        @RequestBody UpdateProfileRequest req) {
        user.setFirstName(trimOrNull(req.getFirstName()));
        user.setLastName(trimOrNull(req.getLastName()));
        user.setPhone(trimOrNull(req.getPhone()));
        user.setGender(trimOrNull(req.getGender()));
        user.setBio(trimOrNull(req.getBio()));
        user.setDateOfBirth(req.getDateOfBirth() != null && !req.getDateOfBirth().isBlank()
                ? LocalDate.parse(req.getDateOfBirth()) : null);
        userRepository.save(user);
        return toDto(user);
    }

    @PutMapping("/me/shipping")
    public UserProfileDto updateShipping(@AuthenticationPrincipal User user,
                                         @RequestBody UpdateShippingRequest req) {
        user.setStreet(trimOrNull(req.getStreet()));
        user.setHouseNumber(trimOrNull(req.getHouseNumber()));
        user.setCity(trimOrNull(req.getCity()));
        user.setPostalCode(trimOrNull(req.getPostalCode()));
        userRepository.save(user);
        return toDto(user);
    }

    private UserProfileDto toDto(User user) {
        long listingsCount = listingRepository.findByUserId(user.getId()).size();
        long offersCount   = offerRepository.findByUserId(user.getId()).size();
        return new UserProfileDto(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                listingsCount,
                offersCount,
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getGender(),
                user.getBio(),
                user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null,
                user.getStreet(),
                user.getHouseNumber(),
                user.getCity(),
                user.getPostalCode()
        );
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
