package com.printplatform.controller;

import com.printplatform.dto.CreateListingRequest;
import com.printplatform.dto.PageResponse;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.StlFileRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final StlFileRepository stlFileRepository;

    public ListingController(ListingRepository listingRepository,
                             OfferRepository offerRepository,
                             StlFileRepository stlFileRepository) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
        this.stlFileRepository = stlFileRepository;
    }

    @GetMapping
    public PageResponse<Listing> getOpenListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.clamp(size, 1, 50);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PageResponse<>(listingRepository.findByStatus(ListingStatus.OPEN, pageable));
    }

    @GetMapping("/{id}")
    public Listing getListing(@PathVariable UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
    }

    @GetMapping("/my")
    public List<Listing> getMyListings(@AuthenticationPrincipal User user) {
        return listingRepository.findByUserId(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Listing createListing(@Valid @RequestBody CreateListingRequest request,
                                 @AuthenticationPrincipal User user) {
        Listing listing = new Listing();
        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setRequiredMaterial(request.getRequiredMaterial());
        listing.setMaxBudget(request.getMaxBudget());
        listing.setUser(user);
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    @PutMapping("/{id}/close")
    public Listing closeListing(@PathVariable UUID id,
                                @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        listing.setStatus(ListingStatus.CLOSED);
        return listingRepository.save(listing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteListing(@PathVariable UUID id,
                              @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        // Remove dependent rows first (listing_id FKs are non-nullable).
        offerRepository.deleteAll(offerRepository.findByListingId(id));
        stlFileRepository.deleteByListingId(id);
        listingRepository.delete(listing);
    }

    /** A listing may be modified/removed by its owner or by any administrator. */
    private void requireOwnerOrAdmin(Listing listing, User user) {
        boolean isOwner = listing.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
    }

    @GetMapping("/{id}/stl")
    public ResponseEntity<byte[]> downloadStl(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        // Priority 1: Serve from uploaded BLOB if present
        if (listing.getStlFileData() != null && listing.getStlFileData().length > 0) {
            String filename = listing.getStlFileName() != null ? listing.getStlFileName() : "model.stl";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(listing.getStlFileData());
        }

        // Priority 2: Fall back to external URL if no uploaded file
        if (listing.getStlFileUrl() == null || listing.getStlFileUrl().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak pliku STL");
        }

        // A Thingiverse "thing:" link is a web PAGE, not a downloadable STL —
        // proxying it only ever returns HTML. Reject it with a clear message.
        String url = listing.getStlFileUrl();
        if (url.contains("thingiverse.com")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Link Thingiverse to strona, nie plik STL. Prześlij plik .stl, aby zobaczyć podgląd 3D.");
        }

        byte[] fileContent;
        try {
            RestTemplate restTemplate = new RestTemplate();
            fileContent = restTemplate.getForObject(url, byte[].class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się pobrać pliku STL: " + e.getMessage());
        }

        // Guard: never serve an HTML page (login/error page) as if it were an STL.
        if (fileContent == null || looksLikeHtml(fileContent)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Adres URL nie wskazuje na plik STL. Prześlij plik .stl, aby zobaczyć podgląd 3D.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"model.stl\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(fileContent);
    }

    private boolean looksLikeHtml(byte[] data) {
        int n = Math.min(data.length, 64);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = (char) (data[i] & 0xFF);
            if (!Character.isWhitespace(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        String head = sb.toString();
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml");
    }
}
