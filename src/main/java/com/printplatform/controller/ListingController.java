package com.printplatform.controller;

import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listingRepository;

    public ListingController(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @GetMapping
    public List<Listing> getOpenListings() {
        return listingRepository.findByStatus(ListingStatus.OPEN);
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
    public Listing createListing(@RequestBody Listing listing,
                                 @AuthenticationPrincipal User user) {
        listing.setUser(user);
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    @PutMapping("/{id}/close")
    public Listing closeListing(@PathVariable UUID id,
                                @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        if (!listing.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        listing.setStatus(ListingStatus.CLOSED);
        return listingRepository.save(listing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteListing(@PathVariable UUID id,
                              @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        if (!listing.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        listingRepository.delete(listing);
    }
}
