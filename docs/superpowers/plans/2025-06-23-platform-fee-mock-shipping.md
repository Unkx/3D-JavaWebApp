# Platform Fee + Mock InPost Shipping — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 10% platform fee markup and mock InPost paczkomat shipping to the offer acceptance flow, so buyers see a full price breakdown (contractor price + fee + shipping) and the system generates mock InPost tracking.

**Architecture:** New `Payment` entity captures fee breakdown at acceptance time. New `Shipment` entity replaces manual carrier input with auto-generated InPost mock data. `selectOffer` endpoint extended to accept paczkomat choice and create both records atomically. Frontend gets a checkout step with paczkomat dropdown + price summary before finalizing.

**Tech Stack:** Spring Boot 3.2.5 / Java 21 / JPA / PostgreSQL / Angular 21

## Global Constraints

- `ddl-auto=update` — Hibernate creates tables automatically, no migrations needed
- Follow existing patterns: Lombok `@Data` on entities, manual getters on DTOs, controller-level logic (no service layer existed before — we add services for new logic)
- Polish UI labels (matching existing app language)
- Platform fee default: 10%
- Shipping prices: size A = 12.99, B = 14.99, C = 19.99 PLN
- Parcel size auto-derived from `listing.estimatorSize`: small→A, medium→B, large→C, null→B
- Mock paczkomatы: 6 hardcoded entries

---

### Task 1: Backend — Enums, Entities, Repositories

**Files:**
- Create: `src/main/java/com/printplatform/model/PaymentStatus.java`
- Create: `src/main/java/com/printplatform/model/Payment.java`
- Create: `src/main/java/com/printplatform/model/ShipmentStatus.java`
- Create: `src/main/java/com/printplatform/model/Shipment.java`
- Create: `src/main/java/com/printplatform/repository/PaymentRepository.java`
- Create: `src/main/java/com/printplatform/repository/ShipmentRepository.java`

**Produces:**
- `PaymentStatus` enum: `PENDING, HELD, RELEASED, REFUNDED`
- `ShipmentStatus` enum: `LABEL_CREATED, DISPATCHED, IN_TRANSIT, READY_TO_PICKUP, DELIVERED`
- `Payment` entity with fields: id (UUID), offer (OneToOne), buyer (ManyToOne User), seller (ManyToOne User), contractorPrice, platformFeePercent, platformFee, shippingPrice, parcelSize, totalPrice, receiverPaczkomat, status (PaymentStatus), paidAt, releasedAt, createdAt
- `Shipment` entity with fields: id (UUID), payment (ManyToOne), offer (OneToOne), trackingNumber, labelUrl, senderPaczkomat, receiverPaczkomat, parcelSize, status (ShipmentStatus), createdAt
- `PaymentRepository` with `findByOfferId(UUID)` → `Optional<Payment>`
- `ShipmentRepository` with `findByOfferId(UUID)` → `Optional<Shipment>`, `findByPaymentId(UUID)` → `Optional<Shipment>`

- [ ] **Step 1:** Create `PaymentStatus.java` enum

```java
package com.printplatform.model;

public enum PaymentStatus {
    PENDING, HELD, RELEASED, REFUNDED
}
```

- [ ] **Step 2:** Create `Payment.java` entity

```java
package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"listing", "user"})
    private Offer offer;

    @ManyToOne
    @JoinColumn(name = "buyer_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User buyer;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User seller;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal contractorPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercent;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingPrice;

    @Column(length = 1)
    private String parcelSize;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 20)
    private String receiverPaczkomat;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private LocalDateTime paidAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 3:** Create `ShipmentStatus.java` enum

```java
package com.printplatform.model;

public enum ShipmentStatus {
    LABEL_CREATED, DISPATCHED, IN_TRANSIT, READY_TO_PICKUP, DELIVERED
}
```

- [ ] **Step 4:** Create `Shipment.java` entity

```java
package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "payment_id", nullable = false)
    @JsonIgnoreProperties({"offer", "buyer", "seller"})
    private Payment payment;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"listing", "user"})
    private Offer offer;

    @Column(length = 50)
    private String trackingNumber;

    private String labelUrl;

    @Column(length = 20)
    private String senderPaczkomat;

    @Column(length = 20)
    private String receiverPaczkomat;

    @Column(length = 1)
    private String parcelSize;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status = ShipmentStatus.LABEL_CREATED;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 5:** Create `PaymentRepository.java`

```java
package com.printplatform.repository;

import com.printplatform.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOfferId(UUID offerId);
}
```

- [ ] **Step 6:** Create `ShipmentRepository.java`

```java
package com.printplatform.repository;

import com.printplatform.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOfferId(UUID offerId);
    Optional<Shipment> findByPaymentId(UUID paymentId);
}
```

- [ ] **Step 7:** Commit

```bash
git add src/main/java/com/printplatform/model/PaymentStatus.java \
        src/main/java/com/printplatform/model/Payment.java \
        src/main/java/com/printplatform/model/ShipmentStatus.java \
        src/main/java/com/printplatform/model/Shipment.java \
        src/main/java/com/printplatform/repository/PaymentRepository.java \
        src/main/java/com/printplatform/repository/ShipmentRepository.java
git commit -m "feat: add Payment and Shipment entities for platform fee + mock InPost"
```

---

### Task 2: Backend — Services + Config

**Files:**
- Create: `src/main/java/com/printplatform/service/PaymentService.java`
- Create: `src/main/java/com/printplatform/service/ShipmentService.java`
- Modify: `src/main/resources/application.properties`

**Consumes:** Payment, Shipment, PaymentRepository, ShipmentRepository, OfferRepository
**Produces:**
- `PaymentService.createPayment(Offer, User buyer, String receiverPaczkomat)` → Payment (status HELD, mock instant)
- `PaymentService.releasePayment(UUID paymentId)` → Payment
- `PaymentService.getShippingPrice(String estimatorSize)` → BigDecimal
- `PaymentService.getParcelSize(String estimatorSize)` → String
- `ShipmentService.createShipment(Payment, Offer, String senderPaczkomat)` → Shipment
- `ShipmentService.advanceStatus(UUID shipmentId)` → Shipment

- [ ] **Step 1:** Add config to `application.properties`

Append to end of file:
```properties
# Platform fee (percentage added on top of contractor price)
platform.fee.percent=${PLATFORM_FEE_PERCENT:10.0}

# Mock InPost shipping prices by parcel size
shipping.price.a=${SHIPPING_PRICE_A:12.99}
shipping.price.b=${SHIPPING_PRICE_B:14.99}
shipping.price.c=${SHIPPING_PRICE_C:19.99}
```

- [ ] **Step 2:** Create `PaymentService.java`

```java
package com.printplatform.service;

import com.printplatform.model.*;
import com.printplatform.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BigDecimal feePercent;
    private final BigDecimal priceA;
    private final BigDecimal priceB;
    private final BigDecimal priceC;

    public PaymentService(PaymentRepository paymentRepository,
                          @Value("${platform.fee.percent:10.0}") BigDecimal feePercent,
                          @Value("${shipping.price.a:12.99}") BigDecimal priceA,
                          @Value("${shipping.price.b:14.99}") BigDecimal priceB,
                          @Value("${shipping.price.c:19.99}") BigDecimal priceC) {
        this.paymentRepository = paymentRepository;
        this.feePercent = feePercent;
        this.priceA = priceA;
        this.priceB = priceB;
        this.priceC = priceC;
    }

    public String getParcelSize(String estimatorSize) {
        if (estimatorSize == null) return "B";
        return switch (estimatorSize.toLowerCase()) {
            case "small" -> "A";
            case "large" -> "C";
            default -> "B";
        };
    }

    public BigDecimal getShippingPrice(String parcelSize) {
        return switch (parcelSize) {
            case "A" -> priceA;
            case "C" -> priceC;
            default -> priceB;
        };
    }

    public Payment createPayment(Offer offer, User buyer, String receiverPaczkomat) {
        if (paymentRepository.findByOfferId(offer.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Płatność już istnieje dla tej oferty");
        }

        String parcelSize = getParcelSize(offer.getListing().getEstimatorSize());
        BigDecimal contractorPrice = offer.getPrice();
        BigDecimal fee = contractorPrice.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal shipping = getShippingPrice(parcelSize);
        BigDecimal total = contractorPrice.add(fee).add(shipping);

        Payment payment = new Payment();
        payment.setOffer(offer);
        payment.setBuyer(buyer);
        payment.setSeller(offer.getUser());
        payment.setContractorPrice(contractorPrice);
        payment.setPlatformFeePercent(feePercent);
        payment.setPlatformFee(fee);
        payment.setShippingPrice(shipping);
        payment.setParcelSize(parcelSize);
        payment.setTotalPrice(total);
        payment.setReceiverPaczkomat(receiverPaczkomat);
        // Mock: instant payment
        payment.setStatus(PaymentStatus.HELD);
        payment.setPaidAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    public Payment releasePayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Płatność nie istnieje"));
        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Płatność nie jest w stanie HELD");
        }
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    public Payment getByOfferId(UUID offerId) {
        return paymentRepository.findByOfferId(offerId).orElse(null);
    }
}
```

- [ ] **Step 3:** Create `ShipmentService.java`

```java
package com.printplatform.service;

import com.printplatform.model.*;
import com.printplatform.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment createShipment(Payment payment, Offer offer, String senderPaczkomat) {
        if (shipmentRepository.findByOfferId(offer.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Przesyłka już istnieje dla tej oferty");
        }

        String trackingNumber = "INPOST-MOCK-" + ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        Shipment shipment = new Shipment();
        shipment.setPayment(payment);
        shipment.setOffer(offer);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setLabelUrl("/api/shipments/" + "mock-label"); // placeholder, updated after save
        shipment.setSenderPaczkomat(senderPaczkomat);
        shipment.setReceiverPaczkomat(payment.getReceiverPaczkomat());
        shipment.setParcelSize(payment.getParcelSize());
        shipment.setStatus(ShipmentStatus.LABEL_CREATED);

        Shipment saved = shipmentRepository.save(shipment);
        saved.setLabelUrl("/api/shipments/" + saved.getId() + "/label");
        return shipmentRepository.save(saved);
    }

    public Shipment advanceStatus(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Przesyłka nie istnieje"));

        Map<ShipmentStatus, ShipmentStatus> transitions = Map.of(
                ShipmentStatus.LABEL_CREATED, ShipmentStatus.DISPATCHED,
                ShipmentStatus.DISPATCHED, ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.IN_TRANSIT, ShipmentStatus.READY_TO_PICKUP,
                ShipmentStatus.READY_TO_PICKUP, ShipmentStatus.DELIVERED
        );

        ShipmentStatus next = transitions.get(shipment.getStatus());
        if (next == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nie można zmienić statusu przesyłki z: " + shipment.getStatus());
        }

        shipment.setStatus(next);
        return shipmentRepository.save(shipment);
    }

    public Shipment getByOfferId(UUID offerId) {
        return shipmentRepository.findByOfferId(offerId).orElse(null);
    }
}
```

- [ ] **Step 4:** Commit

```bash
git add src/main/java/com/printplatform/service/PaymentService.java \
        src/main/java/com/printplatform/service/ShipmentService.java \
        src/main/resources/application.properties
git commit -m "feat: add PaymentService and ShipmentService with mock InPost logic"
```

---

### Task 3: Backend — DTOs + Modify OfferController + New Controllers

**Files:**
- Create: `src/main/java/com/printplatform/dto/SelectOfferRequest.java`
- Create: `src/main/java/com/printplatform/dto/CreateShipmentRequest.java`
- Create: `src/main/java/com/printplatform/dto/FeeBreakdownResponse.java`
- Create: `src/main/java/com/printplatform/controller/PaymentController.java`
- Create: `src/main/java/com/printplatform/controller/ShipmentController.java`
- Modify: `src/main/java/com/printplatform/controller/OfferController.java` — selectOffer takes body, creates payment, rejects other offers
- Modify: `src/main/java/com/printplatform/security/SecurityConfig.java` — permit GET on fee-breakdown

**Consumes:** PaymentService, ShipmentService, all entities/repos from Task 1-2
**Produces:**
- `PUT /api/offers/{offerId}/select` — now takes `{ receiverPaczkomat: "WAW001" }` body, creates Payment, returns offer
- `GET /api/offers/{offerId}/payment` — returns Payment for an offer
- `GET /api/offers/fee-breakdown?price=100&estimatorSize=medium` — preview breakdown (no auth needed)
- `POST /api/shipments/offer/{offerId}` — creates Shipment (seller only, offer must be PRINTING)
- `PUT /api/shipments/{id}/advance` — advance shipment status (mock InPost progression)
- `GET /api/shipments/offer/{offerId}` — get shipment for offer

- [ ] **Step 1:** Create `SelectOfferRequest.java`

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class SelectOfferRequest {
    @NotBlank(message = "Paczkomat odbioru jest wymagany")
    private String receiverPaczkomat;

    public String getReceiverPaczkomat() { return receiverPaczkomat; }
    public void setReceiverPaczkomat(String receiverPaczkomat) { this.receiverPaczkomat = receiverPaczkomat; }
}
```

- [ ] **Step 2:** Create `CreateShipmentRequest.java`

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateShipmentRequest {
    @NotBlank(message = "Paczkomat nadania jest wymagany")
    private String senderPaczkomat;

    public String getSenderPaczkomat() { return senderPaczkomat; }
    public void setSenderPaczkomat(String senderPaczkomat) { this.senderPaczkomat = senderPaczkomat; }
}
```

- [ ] **Step 3:** Create `FeeBreakdownResponse.java`

```java
package com.printplatform.dto;

import java.math.BigDecimal;

public class FeeBreakdownResponse {
    private BigDecimal contractorPrice;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFee;
    private BigDecimal shippingPrice;
    private String parcelSize;
    private BigDecimal totalPrice;

    public FeeBreakdownResponse(BigDecimal contractorPrice, BigDecimal platformFeePercent,
                                BigDecimal platformFee, BigDecimal shippingPrice,
                                String parcelSize, BigDecimal totalPrice) {
        this.contractorPrice = contractorPrice;
        this.platformFeePercent = platformFeePercent;
        this.platformFee = platformFee;
        this.shippingPrice = shippingPrice;
        this.parcelSize = parcelSize;
        this.totalPrice = totalPrice;
    }

    public BigDecimal getContractorPrice() { return contractorPrice; }
    public BigDecimal getPlatformFeePercent() { return platformFeePercent; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getShippingPrice() { return shippingPrice; }
    public String getParcelSize() { return parcelSize; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}
```

- [ ] **Step 4:** Modify `OfferController.java` — update selectOffer to accept body and create payment

Replace the `selectOffer` method (lines 73-88) with:

```java
@PutMapping("/{offerId}/select")
public Offer selectOffer(@PathVariable UUID offerId,
                         @Valid @RequestBody SelectOfferRequest request,
                         @AuthenticationPrincipal User currentUser) {
    Offer offer = offerRepository.findById(offerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

    Listing listing = offer.getListing();
    if (!listing.getUser().getId().equals(currentUser.getId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko właściciel zlecenia może wybrać ofertę");
    }
    if (offer.getStatus() != OfferStatus.PENDING) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Oferta nie jest w stanie oczekiwania");
    }

    // Create payment (mock: instantly HELD)
    paymentService.createPayment(offer, currentUser, request.getReceiverPaczkomat());

    // Accept this offer
    offer.setStatus(OfferStatus.SELECTED);
    listing.setStatus(ListingStatus.AWARDED);
    listingRepository.save(listing);

    // Reject other pending offers
    offerRepository.findByListingIdAndStatus(listing.getId(), OfferStatus.PENDING)
            .stream()
            .filter(o -> !o.getId().equals(offerId))
            .forEach(o -> {
                o.setStatus(OfferStatus.REJECTED);
                offerRepository.save(o);
            });

    return offerRepository.save(offer);
}
```

Also add `PaymentService` and `SelectOfferRequest` import + inject PaymentService in constructor:

```java
private final PaymentService paymentService;

public OfferController(OfferRepository offerRepository,
                       ListingRepository listingRepository,
                       OrderTrackingRepository orderTrackingRepository,
                       PaymentService paymentService) {
    this.offerRepository = offerRepository;
    this.listingRepository = listingRepository;
    this.orderTrackingRepository = orderTrackingRepository;
    this.paymentService = paymentService;
}
```

Add a new endpoint to get payment for an offer:

```java
@GetMapping("/{offerId}/payment")
public Payment getPayment(@PathVariable UUID offerId,
                          @AuthenticationPrincipal User currentUser) {
    Offer offer = offerRepository.findById(offerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

    boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
    boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());
    if (!isSeller && !isBuyer) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
    }

    Payment payment = paymentService.getByOfferId(offerId);
    if (payment == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak płatności");
    }
    return payment;
}
```

Add a public fee preview endpoint:

```java
@GetMapping("/fee-breakdown")
public FeeBreakdownResponse feeBreakdown(@RequestParam BigDecimal price,
                                          @RequestParam(required = false) String estimatorSize) {
    String parcelSize = paymentService.getParcelSize(estimatorSize);
    BigDecimal shipping = paymentService.getShippingPrice(parcelSize);
    BigDecimal feePercent = new BigDecimal("10.0");
    BigDecimal fee = price.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    BigDecimal total = price.add(fee).add(shipping);
    return new FeeBreakdownResponse(price, feePercent, fee, shipping, parcelSize, total);
}
```

- [ ] **Step 5:** Create `PaymentController.java`

```java
package com.printplatform.controller;

import com.printplatform.model.Payment;
import com.printplatform.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.printplatform.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PutMapping("/{paymentId}/release")
    public Payment releasePayment(@PathVariable UUID paymentId,
                                  @AuthenticationPrincipal User currentUser) {
        return paymentService.releasePayment(paymentId);
    }
}
```

- [ ] **Step 6:** Create `ShipmentController.java`

```java
package com.printplatform.controller;

import com.printplatform.dto.CreateShipmentRequest;
import com.printplatform.model.*;
import com.printplatform.repository.OfferRepository;
import com.printplatform.service.PaymentService;
import com.printplatform.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final PaymentService paymentService;
    private final OfferRepository offerRepository;

    public ShipmentController(ShipmentService shipmentService,
                              PaymentService paymentService,
                              OfferRepository offerRepository) {
        this.shipmentService = shipmentService;
        this.paymentService = paymentService;
        this.offerRepository = offerRepository;
    }

    @PostMapping("/offer/{offerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Shipment createShipment(@PathVariable UUID offerId,
                                   @Valid @RequestBody CreateShipmentRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        if (!offer.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko sprzedawca może utworzyć przesyłkę");
        }
        if (offer.getStatus() != OfferStatus.PRINTING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Przesyłkę można utworzyć tylko w statusie PRINTING");
        }

        Payment payment = paymentService.getByOfferId(offerId);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak płatności dla tej oferty");
        }

        return shipmentService.createShipment(payment, offer, request.getSenderPaczkomat());
    }

    @PutMapping("/{shipmentId}/advance")
    public Shipment advanceStatus(@PathVariable UUID shipmentId,
                                  @AuthenticationPrincipal User currentUser) {
        Shipment shipment = shipmentService.advanceStatus(shipmentId);

        // When shipment reaches DISPATCHED, auto-advance offer to SHIPPED
        if (shipment.getStatus() == ShipmentStatus.DISPATCHED) {
            Offer offer = shipment.getOffer();
            offer.setStatus(OfferStatus.SHIPPED);
            offerRepository.save(offer);
        }

        // When shipment reaches DELIVERED, auto-advance offer to DELIVERED and release payment
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            Offer offer = shipment.getOffer();
            offer.setStatus(OfferStatus.DELIVERED);
            offerRepository.save(offer);
            paymentService.releasePayment(shipment.getPayment().getId());
        }

        return shipment;
    }

    @GetMapping("/offer/{offerId}")
    public Shipment getShipmentForOffer(@PathVariable UUID offerId,
                                        @AuthenticationPrincipal User currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta nie istnieje"));

        boolean isSeller = offer.getUser().getId().equals(currentUser.getId());
        boolean isBuyer = offer.getListing().getUser().getId().equals(currentUser.getId());
        if (!isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu");
        }

        Shipment shipment = shipmentService.getByOfferId(offerId);
        if (shipment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak przesyłki");
        }
        return shipment;
    }
}
```

- [ ] **Step 7:** Modify `SecurityConfig.java` — add public fee-breakdown endpoint

Add to the `authorizeHttpRequests` chain, after the existing GET listings line:

```java
.requestMatchers(HttpMethod.GET, "/api/offers/fee-breakdown").permitAll()
```

- [ ] **Step 8:** Commit

```bash
git add src/main/java/com/printplatform/dto/SelectOfferRequest.java \
        src/main/java/com/printplatform/dto/CreateShipmentRequest.java \
        src/main/java/com/printplatform/dto/FeeBreakdownResponse.java \
        src/main/java/com/printplatform/controller/OfferController.java \
        src/main/java/com/printplatform/controller/PaymentController.java \
        src/main/java/com/printplatform/controller/ShipmentController.java \
        src/main/java/com/printplatform/security/SecurityConfig.java
git commit -m "feat: wire up selectOffer with payment creation, add shipment + payment controllers"
```

---

### Task 4: Frontend — Services + Types

**Files:**
- Create: `frontend/src/app/services/payment.service.ts`
- Modify: `frontend/src/app/services/offer.service.ts` — add Payment, Shipment, FeeBreakdown interfaces; add selectOffer body param; add new API methods

**Produces:**
- `Payment` interface
- `Shipment` interface
- `FeeBreakdown` interface
- `OfferService.selectOffer(offerId, receiverPaczkomat)` — updated
- `OfferService.getPayment(offerId)` → `Observable<Payment>`
- `OfferService.getFeeBreakdown(price, estimatorSize?)` → `Observable<FeeBreakdown>`
- `PaymentService.getShipment(offerId)` → `Observable<Shipment>`
- `PaymentService.createShipment(offerId, senderPaczkomat)` → `Observable<Shipment>`
- `PaymentService.advanceShipment(shipmentId)` → `Observable<Shipment>`

- [ ] **Step 1:** Update `offer.service.ts` — add interfaces and methods

Add interfaces after existing `OrderTracking` interface:

```typescript
export interface Payment {
  id: string;
  contractorPrice: number;
  platformFeePercent: number;
  platformFee: number;
  shippingPrice: number;
  parcelSize: string;
  totalPrice: number;
  receiverPaczkomat: string;
  status: string;
  paidAt: string | null;
  releasedAt: string | null;
}

export interface Shipment {
  id: string;
  trackingNumber: string;
  labelUrl: string;
  senderPaczkomat: string;
  receiverPaczkomat: string;
  parcelSize: string;
  status: string;
  createdAt: string;
}

export interface FeeBreakdown {
  contractorPrice: number;
  platformFeePercent: number;
  platformFee: number;
  shippingPrice: number;
  parcelSize: string;
  totalPrice: number;
}
```

Update `selectOffer` method to send body:

```typescript
selectOffer(offerId: string, receiverPaczkomat: string): Observable<Offer> {
  return this.http.put<Offer>(`${this.apiUrl}/${offerId}/select`, { receiverPaczkomat });
}
```

Add new methods:

```typescript
getPayment(offerId: string): Observable<Payment> {
  return this.http.get<Payment>(`${this.apiUrl}/${offerId}/payment`);
}

getFeeBreakdown(price: number, estimatorSize?: string): Observable<FeeBreakdown> {
  let url = `${this.apiUrl}/fee-breakdown?price=${price}`;
  if (estimatorSize) url += `&estimatorSize=${estimatorSize}`;
  return this.http.get<FeeBreakdown>(url);
}

getShipment(offerId: string): Observable<Shipment> {
  return this.http.get<Shipment>(`/api/shipments/offer/${offerId}`);
}

createShipment(offerId: string, senderPaczkomat: string): Observable<Shipment> {
  return this.http.post<Shipment>(`/api/shipments/offer/${offerId}`, { senderPaczkomat });
}

advanceShipment(shipmentId: string): Observable<Shipment> {
  return this.http.put<Shipment>(`/api/shipments/${shipmentId}/advance`, {});
}
```

- [ ] **Step 2:** Commit

```bash
git add frontend/src/app/services/offer.service.ts
git commit -m "feat: add Payment, Shipment, FeeBreakdown types and API methods to OfferService"
```

---

### Task 5: Frontend — Listing Detail Checkout Flow

**Files:**
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.ts`
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.html`
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.css`

**Consumes:** Updated `OfferService` with `selectOffer(id, paczkomat)`, `getFeeBreakdown()`, `Payment` type

Changes needed:
1. Each offer card shows price breakdown (contractor + fee + shipping = total)
2. "Akceptuj ofertę" button replaced with "Zapłać X PLN" that opens checkout panel
3. Checkout panel: paczkomat dropdown + price summary + confirm button
4. After payment: offer shows payment status badge + breakdown

- [ ] **Step 1:** Update `listing-detail.component.ts`

Add to the component class:

```typescript
// Checkout state
checkoutOfferId = signal<string | null>(null);
checkoutPaczkomat = signal('WAW001');
checkoutPaying = signal(false);

readonly paczkomatOptions = [
  { id: 'WAW001', label: 'WAW001 — Warszawa, ul. Marszałkowska 10' },
  { id: 'WAW045', label: 'WAW045 — Warszawa, ul. Puławska 120' },
  { id: 'KRK012', label: 'KRK012 — Kraków, ul. Floriańska 5' },
  { id: 'WRO008', label: 'WRO008 — Wrocław, ul. Świdnicka 25' },
  { id: 'GDA003', label: 'GDA003 — Gdańsk, ul. Długa 15' },
  { id: 'POZ019', label: 'POZ019 — Poznań, ul. Półwiejska 42' },
];

feeBreakdown = computed(() => {
  const listing = this.listing();
  return (offer: Offer) => {
    const price = offer.price;
    const feePercent = 10;
    const fee = Math.round(price * feePercent) / 100;
    const size = listing?.estimatorSize ?? 'medium';
    const shipping = size === 'small' ? 12.99 : size === 'large' ? 19.99 : 14.99;
    const total = +(price + fee + shipping).toFixed(2);
    return { contractorPrice: price, fee, shipping, total };
  };
});

openCheckout(offer: Offer): void {
  this.checkoutOfferId.set(offer.id ?? null);
  this.checkoutPaczkomat.set('WAW001');
}

cancelCheckout(): void {
  this.checkoutOfferId.set(null);
}

confirmCheckout(offer: Offer): void {
  if (!offer.id) return;
  this.checkoutPaying.set(true);
  this.offerService.selectOffer(offer.id, this.checkoutPaczkomat()).subscribe({
    next: () => {
      this.checkoutPaying.set(false);
      this.checkoutOfferId.set(null);
      const listingId = this.listing()?.id;
      if (listingId) {
        this.loadOffers(listingId);
        this.loadListing(listingId);
      }
    },
    error: () => {
      this.checkoutPaying.set(false);
      this.acceptError.set('Nie udało się dokonać płatności.');
    }
  });
}
```

Remove the old `acceptOffer` method (it's replaced by `openCheckout` + `confirmCheckout`).

- [ ] **Step 2:** Update `listing-detail.component.html` — offer cards section

Replace the offer-card actions block (the `@if (currentUser()...` block inside the offer list) with:

```html
@if (currentUser() && (isOwner() || currentUser()?.userId === offer.user?.id)) {
  <!-- Price breakdown (always visible for accepted+ offers) -->
  @if (isOwner() && offer.status === 'PENDING' && listing()!.status === 'OPEN') {
    @let bd = feeBreakdown()(offer);
    <div class="offer-card__breakdown">
      <div class="breakdown__row">
        <span>Cena wykonawcy</span>
        <span>{{ bd.contractorPrice | number:'1.2-2' }} zł</span>
      </div>
      <div class="breakdown__row breakdown__row--muted">
        <span>Opłata serwisowa (10%)</span>
        <span>+ {{ bd.fee | number:'1.2-2' }} zł</span>
      </div>
      <div class="breakdown__row breakdown__row--muted">
        <span>Wysyłka InPost</span>
        <span>+ {{ bd.shipping | number:'1.2-2' }} zł</span>
      </div>
      <div class="breakdown__row breakdown__row--total">
        <span>Do zapłaty</span>
        <span>{{ bd.total | number:'1.2-2' }} zł</span>
      </div>
    </div>
  }

  <div class="offer-card__actions">
    @if (isOwner() && offer.status === 'PENDING' && listing()!.status === 'OPEN') {
      @if (checkoutOfferId() === offer.id) {
        <!-- Checkout panel -->
        <div class="checkout-panel">
          <label class="checkout-panel__label" for="paczkomat-select">
            📦 Paczkomat odbioru
          </label>
          <select id="paczkomat-select" class="checkout-panel__select"
                  [ngModel]="checkoutPaczkomat()"
                  (ngModelChange)="checkoutPaczkomat.set($event)">
            @for (p of paczkomatOptions; track p.id) {
              <option [value]="p.id">{{ p.label }}</option>
            }
          </select>
          <div class="checkout-panel__actions">
            <button class="btn btn--accept"
                    (click)="confirmCheckout(offer)"
                    [disabled]="checkoutPaying()">
              @if (checkoutPaying()) {
                <span class="btn-spinner" aria-hidden="true"></span> Płacenie...
              } @else {
                🛡️ Zapłać {{ feeBreakdown()(offer).total | number:'1.2-2' }} zł
              }
            </button>
            <button class="btn btn--outline" (click)="cancelCheckout()">Anuluj</button>
          </div>
        </div>
      } @else {
        <button class="btn btn--accept" (click)="openCheckout(offer)">
          💳 Zapłać {{ feeBreakdown()(offer).total | number:'1.2-2' }} zł
        </button>
      }
    }
    <button class="btn btn--outline" (click)="openMessage(offer)">
      ✉ Napisz wiadomość
    </button>
  </div>
}
```

Also add `FormsModule` to the component imports array (for `ngModel` on the select), and `DecimalPipe` (for the `number` pipe).

- [ ] **Step 3:** Update `listing-detail.component.css` — add checkout and breakdown styles

```css
/* Price breakdown */
.offer-card__breakdown {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.75rem 1rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 0.8125rem;
}

.breakdown__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.breakdown__row--muted {
  color: var(--text-muted);
}

.breakdown__row--total {
  padding-top: 0.375rem;
  margin-top: 0.25rem;
  border-top: 1px solid var(--border);
  font-weight: 700;
  font-size: 0.9375rem;
  color: var(--text-primary);
}

/* Checkout panel */
.checkout-panel {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  padding: 0.875rem 1rem;
  background: var(--accent-light);
  border: 1px solid var(--accent-border);
  border-radius: 8px;
  width: 100%;
  animation: fadeUp 0.25s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.checkout-panel__label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
}

.checkout-panel__select {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 8px;
  font-size: 0.875rem;
  font-family: inherit;
  background: var(--input-bg);
  color: var(--text-primary);
  width: 100%;
  cursor: pointer;
}

.checkout-panel__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
```

- [ ] **Step 4:** Commit

```bash
git add frontend/src/app/features/listing-detail/listing-detail.component.ts \
        frontend/src/app/features/listing-detail/listing-detail.component.html \
        frontend/src/app/features/listing-detail/listing-detail.component.css
git commit -m "feat: add checkout flow with fee breakdown + paczkomat selection to listing detail"
```

---

### Task 6: Frontend — My Orders: InPost Shipment Flow

**Files:**
- Modify: `frontend/src/app/features/my-orders/my-orders.component.ts`
- Modify: `frontend/src/app/features/my-orders/my-orders.component.html`
- Modify: `frontend/src/app/features/my-orders/my-orders.component.css`

**Consumes:** `OfferService` with `createShipment()`, `advanceShipment()`, `getShipment()`, `getPayment()`, `Shipment`, `Payment`

Changes for seller's "offers" tab:
1. Replace manual carrier+tracking form with auto InPost shipment creation
2. When PRINTING: show "Nadaj paczkę" button with paczkomat selector → creates shipment
3. After shipment created: show tracking number, label link, status
4. "Nadano w paczkomacie" button → advances shipment status (DISPATCHED → SHIPPED)
5. Show shipment timeline instead of old tracking display
6. Show payment info (how much seller earns)

- [ ] **Step 1:** Update `my-orders.component.ts`

Add imports and new signals:

```typescript
import { OfferService, Offer, OrderTracking, Payment, Shipment } from '../../services/offer.service';
```

Add to class:

```typescript
// Shipment + Payment data
shipmentData = signal<Record<string, Shipment>>({});
paymentData = signal<Record<string, Payment>>({});
creatingShipmentId = signal<string | null>(null);
advancingShipmentId = signal<string | null>(null);
shipmentPaczkomat = signal('WAW001');

readonly paczkomatOptions = [
  { id: 'WAW001', label: 'WAW001 — Warszawa, ul. Marszałkowska 10' },
  { id: 'WAW045', label: 'WAW045 — Warszawa, ul. Puławska 120' },
  { id: 'KRK012', label: 'KRK012 — Kraków, ul. Floriańska 5' },
  { id: 'WRO008', label: 'WRO008 — Wrocław, ul. Świdnicka 25' },
  { id: 'GDA003', label: 'GDA003 — Gdańsk, ul. Długa 15' },
  { id: 'POZ019', label: 'POZ019 — Poznań, ul. Półwiejska 42' },
];
```

Update `loadMyOffers` to also load shipment/payment data:

```typescript
private loadMyOffers(): void {
  this.offerService.getMyOffers().subscribe({
    next: data => {
      this.myOffers.set(data);
      data.filter(o => ['SELECTED', 'PRINTING', 'SHIPPED', 'DELIVERED'].includes(o.status ?? ''))
          .forEach(o => {
            this.loadTracking(o.id!);
            this.loadShipment(o.id!);
            this.loadPayment(o.id!);
          });
    },
    error: () => {}
  });
}

private loadShipment(offerId: string): void {
  this.offerService.getShipment(offerId).subscribe({
    next: s => this.shipmentData.update(d => ({ ...d, [offerId]: s })),
    error: () => {}
  });
}

private loadPayment(offerId: string): void {
  this.offerService.getPayment(offerId).subscribe({
    next: p => this.paymentData.update(d => ({ ...d, [offerId]: p })),
    error: () => {}
  });
}
```

Add shipment creation and advance methods:

```typescript
createShipment(offerId: string): void {
  this.creatingShipmentId.set(offerId);
  this.offerService.createShipment(offerId, this.shipmentPaczkomat()).subscribe({
    next: s => {
      this.shipmentData.update(d => ({ ...d, [offerId]: s }));
      this.creatingShipmentId.set(null);
    },
    error: () => this.creatingShipmentId.set(null)
  });
}

advanceShipment(offerId: string): void {
  const shipment = this.shipmentData()[offerId];
  if (!shipment) return;
  this.advancingShipmentId.set(offerId);
  this.offerService.advanceShipment(shipment.id).subscribe({
    next: s => {
      this.shipmentData.update(d => ({ ...d, [offerId]: s }));
      this.advancingShipmentId.set(null);
      // Refresh offers to get updated statuses
      this.loadMyOffers();
    },
    error: () => this.advancingShipmentId.set(null)
  });
}

shipmentStatusLabel(status: string): string {
  const map: Record<string, string> = {
    LABEL_CREATED: 'Etykieta utworzona',
    DISPATCHED: 'Nadano',
    IN_TRANSIT: 'W drodze',
    READY_TO_PICKUP: 'Czeka w paczkomacie',
    DELIVERED: 'Odebrano'
  };
  return map[status] ?? status;
}
```

- [ ] **Step 2:** Update `my-orders.component.html` — offers tab

In the offers tab section, after the existing tracking/actions block, replace the `order-card__tracking-actions` div content for the PRINTING status with the new InPost shipment flow. Also add payment breakdown display.

Add after the timeline section, inside the `@if (offerStatusStep(offer.status) >= 0)` block:

```html
<!-- Payment info -->
@if (paymentData()[offer.id!]) {
  @let pay = paymentData()[offer.id!];
  <div class="payment-summary">
    <span class="payment-summary__label">💰 Twoje wynagrodzenie:</span>
    <span class="payment-summary__value">{{ pay.contractorPrice }} zł</span>
    <span class="payment-summary__status">
      @if (pay.status === 'HELD') { 🔒 Środki zabezpieczone }
      @else if (pay.status === 'RELEASED') { ✅ Wypłacono }
    </span>
  </div>
}

<!-- InPost shipment info -->
@if (shipmentData()[offer.id!]) {
  @let ship = shipmentData()[offer.id!];
  <div class="shipment-info">
    <div class="shipment-info__row">
      <span>📦 Tracking:</span>
      <code>{{ ship.trackingNumber }}</code>
    </div>
    <div class="shipment-info__row">
      <span>📍 Paczkomat nadania:</span>
      <span>{{ ship.senderPaczkomat }}</span>
    </div>
    <div class="shipment-info__row">
      <span>📍 Paczkomat odbioru:</span>
      <span>{{ ship.receiverPaczkomat }}</span>
    </div>
    <div class="shipment-info__row">
      <span>Status:</span>
      <span class="status-badge status-badge--{{ ship.status.toLowerCase() }}">
        {{ shipmentStatusLabel(ship.status) }}
      </span>
    </div>
  </div>
}
```

Replace the PRINTING action block (where old shipping form was) with:

```html
@if (offer.status === 'PRINTING') {
  @if (!shipmentData()[offer.id!]) {
    <!-- Create InPost shipment -->
    <div class="shipment-form">
      <label class="shipment-form__label">📦 Nadaj paczkę InPost</label>
      <select class="shipment-form__select"
              [ngModel]="shipmentPaczkomat()"
              (ngModelChange)="shipmentPaczkomat.set($event)">
        @for (p of paczkomatOptions; track p.id) {
          <option [value]="p.id">{{ p.label }}</option>
        }
      </select>
      <button class="btn btn--primary"
              (click)="createShipment(offer.id!)"
              [disabled]="creatingShipmentId() === offer.id">
        @if (creatingShipmentId() === offer.id) { Tworzenie... } @else { 📄 Utwórz przesyłkę }
      </button>
    </div>
  } @else if (shipmentData()[offer.id!].status === 'LABEL_CREATED') {
    <button class="btn btn--primary"
            (click)="advanceShipment(offer.id!)"
            [disabled]="advancingShipmentId() === offer.id">
      @if (advancingShipmentId() === offer.id) { Aktualizacja... } @else { ✅ Nadano w paczkomacie }
    </button>
  }
}
@if (offer.status === 'SHIPPED' && shipmentData()[offer.id!]) {
  @let ship = shipmentData()[offer.id!];
  @if (ship.status !== 'DELIVERED') {
    <button class="btn btn--primary"
            (click)="advanceShipment(offer.id!)"
            [disabled]="advancingShipmentId() === offer.id">
      @if (advancingShipmentId() === offer.id) { Aktualizacja... }
      @else if (ship.status === 'DISPATCHED') { 📦 W drodze }
      @else if (ship.status === 'IN_TRANSIT') { 📬 Czeka w paczkomacie }
      @else if (ship.status === 'READY_TO_PICKUP') { ✅ Odebrano }
    </button>
  }
}
```

- [ ] **Step 3:** Update `my-orders.component.css` — add shipment/payment styles

```css
/* Payment summary */
.payment-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.625rem 0.875rem;
  background: var(--success-bg);
  border: 1px solid var(--success-border);
  border-radius: 8px;
  font-size: 0.8125rem;
}
.payment-summary__label { font-weight: 600; color: var(--success-text); }
.payment-summary__value { font-weight: 700; color: var(--text-primary); font-size: 1rem; }
.payment-summary__status { color: var(--success-text); margin-left: auto; }

/* Shipment info */
.shipment-info {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.75rem 1rem;
  background: var(--accent-light);
  border: 1px solid var(--accent-border);
  border-radius: 8px;
  font-size: 0.8125rem;
}
.shipment-info__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.shipment-info__row code {
  font-family: monospace;
  font-weight: 600;
  color: var(--accent-dark);
}

/* Shipment form */
.shipment-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.shipment-form__label {
  font-size: 0.875rem;
  font-weight: 600;
}
.shipment-form__select {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 8px;
  font-family: inherit;
  font-size: 0.875rem;
  background: var(--input-bg);
  color: var(--text-primary);
}

.status-badge--label_created { background: var(--tag-bg); color: var(--tag-text); }
.status-badge--dispatched { background: #dbeafe; color: #1e40af; }
.status-badge--in_transit { background: #fef3c7; color: #92400e; }
.status-badge--ready_to_pickup { background: var(--success-bg); color: var(--success-text); }
```

- [ ] **Step 4:** Commit

```bash
git add frontend/src/app/features/my-orders/my-orders.component.ts \
        frontend/src/app/features/my-orders/my-orders.component.html \
        frontend/src/app/features/my-orders/my-orders.component.css
git commit -m "feat: replace manual shipping with InPost mock flow in my-orders"
```

---
