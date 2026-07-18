package com.printplatform.controller;

import com.printplatform.dto.UpdatePrivacyRequest;
import com.printplatform.dto.UpdateProfileRequest;
import com.printplatform.dto.UpdateShippingRequest;
import com.printplatform.dto.UserProfileDto;
import com.printplatform.dto.UserPublicProfileDto;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.UserRepository;
import com.printplatform.service.UserDisplayNameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final UserDisplayNameService userDisplayNameService;

    public UserController(ListingRepository listingRepository, OfferRepository offerRepository,
                          UserRepository userRepository, UserDisplayNameService userDisplayNameService) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.userDisplayNameService = userDisplayNameService;
    }

    @GetMapping("/me")
    public UserProfileDto getProfile(@AuthenticationPrincipal User user) {
        return toDto(user);
    }

    @PutMapping("/me")
    public UserProfileDto updateProfile(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody UpdateProfileRequest req) {
        user.setFirstName(trimOrNull(req.getFirstName()));
        user.setLastName(trimOrNull(req.getLastName()));
        user.setPhone(trimOrNull(req.getPhone()));
        user.setGender(trimOrNull(req.getGender()));
        user.setBio(trimOrNull(req.getBio()));
        user.setDateOfBirth(parseDateOfBirth(req.getDateOfBirth()));
        userRepository.save(user);
        return toDto(user);
    }

    @PutMapping("/me/shipping")
    public UserProfileDto updateShipping(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody UpdateShippingRequest req) {
        user.setStreet(trimOrNull(req.getStreet()));
        user.setHouseNumber(trimOrNull(req.getHouseNumber()));
        user.setCity(trimOrNull(req.getCity()));
        user.setPostalCode(trimOrNull(req.getPostalCode()));
        userRepository.save(user);
        return toDto(user);
    }

    @GetMapping("/{id}/public-profile")
    public UserPublicProfileDto getPublicProfile(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> !u.isSuspended())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        return toPublicProfileDto(user);
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> !u.isSuspended())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        if (user.getAvatarData() == null || user.getAvatarData().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak awatara");
        }
        String contentType = user.getAvatarContentType() != null ? user.getAvatarContentType() : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("X-Content-Type-Options", "nosniff")
                .body(user.getAvatarData());
    }

    @PostMapping("/me/avatar")
    public UserPublicProfileDto uploadAvatar(@AuthenticationPrincipal User user,
                                             @RequestParam("file") MultipartFile file) {
        validateAvatarFile(file);
        try {
            user.setAvatarData(file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się przesłać pliku.");
        }
        user.setAvatarContentType(file.getContentType());
        user.setAvatarUrl(null);
        user.setAvatarImportSkipped(true);
        userRepository.save(user);
        return toPublicProfileDto(user);
    }

    @DeleteMapping("/me/avatar")
    public UserPublicProfileDto deleteAvatar(@AuthenticationPrincipal User user) {
        user.setAvatarData(null);
        user.setAvatarContentType(null);
        user.setAvatarUrl(null);
        user.setAvatarImportSkipped(true);
        userRepository.save(user);
        return toPublicProfileDto(user);
    }

    @PostMapping("/me/avatar/import-google")
    public UserPublicProfileDto importGoogleAvatar(@AuthenticationPrincipal User user) {
        if (user.getGoogleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Konto nie jest połączone z Google");
        }
        if (user.getGoogleAvatarUrl() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak zdjęcia profilowego Google do zaimportowania");
        }
        user.setAvatarData(null);
        user.setAvatarContentType(null);
        user.setAvatarUrl(user.getGoogleAvatarUrl());
        user.setAvatarImportSkipped(false);
        userRepository.save(user);
        return toPublicProfileDto(user);
    }

    @PutMapping("/me/privacy")
    public UserPublicProfileDto updatePrivacy(@AuthenticationPrincipal User user,
                                              @RequestBody UpdatePrivacyRequest request) {
        user.setShowCity(request.isShowCity());
        user.setShowRealName(request.isShowRealName());
        userRepository.save(user);
        return toPublicProfileDto(user);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxAvatarSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("Plik jest zbyt duży (maksymalnie 5MB).");
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plik jest pusty");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Plik jest zbyt duży. Maksymalny rozmiar: %dMB", MAX_AVATAR_SIZE / (1024L * 1024)));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolone są tylko pliki JPEG, PNG lub WEBP");
        }
    }

    private UserPublicProfileDto toPublicProfileDto(User user) {
        long activeListingsCount = listingRepository.findByUserId(user.getId()).stream()
                .filter(l -> l.getStatus() == com.printplatform.model.ListingStatus.OPEN
                        && l.getModerationStatus() == com.printplatform.model.ListingModerationStatus.VISIBLE)
                .count();
        return new UserPublicProfileDto(
                user.getId().toString(),
                userDisplayNameService.resolve(user),
                user.isShowCity() ? user.getCity() : null,
                user.isEmailVerified(),
                user.getGoogleId() != null,
                user.getFacebookId() != null,
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getAvatarData() != null && user.getAvatarData().length > 0,
                user.getAvatarUrl(),
                activeListingsCount
        );
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

    private static LocalDate parseDateOfBirth(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateOfBirth);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy format daty urodzenia");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
