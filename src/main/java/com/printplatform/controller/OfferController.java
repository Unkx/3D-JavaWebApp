package com.printplatform.controller;

import com.printplatform.dto.CreateOfferRequest;
import com.printplatform.dto.UpdateOfferStatusRequest;
import com.printplatform.dto.UpdateTrackingRequest;
import com.printplatform.model.*;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.OrderTrackingRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final OrderTrackingRepository orderTrackingRepository;

    public OfferController(OfferRepository offerRepository,
                           ListingRepository listingRepository,
                           OrderTrackingRepository orderTrackingRepository) {
        this.offerRepository = offerRepository;
        this.listingRepository = listingRepository;
        this.orderTrackingRepository = orderTrackingRepository;
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

    @PutMapping("/{offerId}/status")
    public Offer updateOfferStatus(@PathVariable UUID offerId,
                                   @Valid @RequestBody UpdateOfferStatusRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        OfferStatus newStatus;
        try {
            newStatus = OfferStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy status");
        }

        boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
        boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());

        // Buyer can only confirm DELIVERED
        if (isBuyer && newStatus == OfferStatus.DELIVERED && offer.getStatus() == OfferStatus.SHIPPED) {
            offer.setStatus(OfferStatus.DELIVERED);
            OrderTracking tracking = orderTrackingRepository.findByOfferId(offerId).orElse(null);
            if (tracking != null) {
                tracking.setDeliveredAt(LocalDateTime.now());
                orderTrackingRepository.save(tracking);
            }
            return offerRepository.save(offer);
        }

        if (!isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko sprzedawca może zmienić status");
        }

        // Validate transitions
        Map<OfferStatus, Set<OfferStatus>> validTransitions = Map.of(
                OfferStatus.SELECTED, Set.of(OfferStatus.PRINTING),
                OfferStatus.PRINTING, Set.of(OfferStatus.SHIPPED),
                OfferStatus.SHIPPED, Set.of(OfferStatus.DELIVERED)
        );

        Set<OfferStatus> allowed = validTransitions.getOrDefault(offer.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nieprawidłowa zmiana statusu: " + offer.getStatus() + " → " + newStatus);
        }

        offer.setStatus(newStatus);

        // Create OrderTracking when entering PRINTING
        if (newStatus == OfferStatus.PRINTING) {
            OrderTracking tracking = new OrderTracking();
            tracking.setOffer(offer);
            orderTrackingRepository.save(tracking);
        }

        if (newStatus == OfferStatus.DELIVERED) {
            OrderTracking tracking = orderTrackingRepository.findByOfferId(offerId).orElse(null);
            if (tracking != null) {
                tracking.setDeliveredAt(LocalDateTime.now());
                orderTrackingRepository.save(tracking);
            }
        }

        return offerRepository.save(offer);
    }

    @PutMapping("/{offerId}/tracking")
    public OrderTracking updateTracking(@PathVariable UUID offerId,
                                        @Valid @RequestBody UpdateTrackingRequest request,
                                        @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        if (!offer.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko sprzedawca może dodać tracking");
        }

        if (offer.getStatus() != OfferStatus.PRINTING && offer.getStatus() != OfferStatus.SHIPPED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking można dodać tylko po rozpoczęciu druku");
        }

        OrderTracking tracking = orderTrackingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak danych śledzenia"));

        tracking.setCarrierName(request.getCarrierName());
        tracking.setTrackingNumber(request.getTrackingNumber());
        tracking.setShippedAt(LocalDateTime.now());

        // Auto-advance to SHIPPED when tracking is added
        offer.setStatus(OfferStatus.SHIPPED);
        offerRepository.save(offer);

        return orderTrackingRepository.save(tracking);
    }

    @GetMapping("/{offerId}/tracking")
    public OrderTracking getTracking(@PathVariable UUID offerId,
                                     @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
        boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());
        if (!isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
        }

        return orderTrackingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak danych śledzenia"));
    }
}
