package com.printplatform.controller;

import com.printplatform.dto.CreateOfferRequest;
import com.printplatform.model.*;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;

    public OfferController(OfferRepository offerRepository, ListingRepository listingRepository) {
        this.offerRepository = offerRepository;
        this.listingRepository = listingRepository;
    }

    @GetMapping("/listing/{listingId}")
    public List<Offer> getOffersForListing(@PathVariable UUID listingId) {
        return offerRepository.findByListingId(listingId);
    }

    @GetMapping("/my")
    public List<Offer> getMyOffers(@AuthenticationPrincipal User user) {
        return offerRepository.findByUserId(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Offer createOffer(@Valid @RequestBody CreateOfferRequest request,
                             @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        if (listing.getStatus() != null && listing.getStatus() != ListingStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zlecenie nie przyjmuje już ofert");
        }
        if (listing.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie możesz złożyć oferty na własne zlecenie");
        }

        Offer offer = new Offer();
        offer.setListing(listing);
        offer.setUser(user);
        offer.setPrice(request.getPrice());
        offer.setPrintingTimeHours(request.getPrintingTimeHours());
        offer.setFilamentGrams(request.getFilamentGrams());
        offer.setPrinterModel(request.getPrinterModel());
        offer.setStatus(OfferStatus.PENDING);
        return offerRepository.save(offer);
    }

    @PutMapping("/{offerId}/select")
    public Offer selectOffer(@PathVariable UUID offerId,
                             @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        Listing listing = offer.getListing();
        if (!listing.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko właściciel zlecenia może wybrać ofertę");
        }

        offer.setStatus(OfferStatus.SELECTED);
        listing.setStatus(ListingStatus.AWARDED);
        listingRepository.save(listing);
        return offerRepository.save(offer);
    }
}
