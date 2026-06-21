# Messaging & Order Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-listing direct messaging between users and post-acceptance order status tracking (PRINTING→SHIPPED→DELIVERED) with carrier/tracking info.

**Architecture:** Two independent feature verticals sharing the same Spring Boot + Angular stack. Messaging uses a Conversation→Message entity hierarchy with poll-based delivery. Order tracking extends the existing Offer entity with new statuses and a separate OrderTracking entity for shipment details. No WebSocket — frontend polls at 10s (conversation) and 30s (navbar badge).

**Tech Stack:** Spring Boot 3 (Jakarta), JPA/Hibernate (auto-DDL), PostgreSQL, Angular 19+ (standalone components, signals, OnPush), JWT auth.

## Global Constraints

- Entities use Lombok `@Data @NoArgsConstructor @AllArgsConstructor`, Jakarta persistence, UUID PKs with `GenerationType.UUID`
- DTOs use manual getters/setters with Jakarta validation annotations
- Controllers use constructor injection, `@AuthenticationPrincipal User`, `ResponseStatusException` for errors
- Repositories extend `JpaRepository<Entity, UUID>` with `findBy*` naming conventions
- Frontend components: standalone, `ChangeDetectionStrategy.OnPush`, signals, `inject()`, `@if`/`@for` control flow
- Frontend services: `@Injectable({ providedIn: 'root' })`, `inject(HttpClient)`, return `Observable<T>`
- Security: new `/api/conversations/**` endpoints require `.authenticated()` — add to `SecurityConfig.java`
- Polish-language UI labels throughout

---

### Task 1: Messaging Backend — Entities, Repository, Controller

**Files:**
- Create: `src/main/java/com/printplatform/model/Conversation.java`
- Create: `src/main/java/com/printplatform/model/Message.java`
- Create: `src/main/java/com/printplatform/repository/ConversationRepository.java`
- Create: `src/main/java/com/printplatform/repository/MessageRepository.java`
- Create: `src/main/java/com/printplatform/dto/CreateConversationRequest.java`
- Create: `src/main/java/com/printplatform/dto/SendMessageRequest.java`
- Create: `src/main/java/com/printplatform/dto/ConversationDto.java`
- Create: `src/main/java/com/printplatform/controller/ConversationController.java`
- Modify: `src/main/java/com/printplatform/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `User` entity, `Listing` entity, `ListingRepository`, `UserRepository`
- Produces: REST endpoints `POST /api/conversations`, `GET /api/conversations`, `GET /api/conversations/{id}/messages`, `POST /api/conversations/{id}/messages`, `PUT /api/conversations/{id}/read`, `GET /api/conversations/unread-count`

- [ ] **Step 1: Create Conversation entity**

Create `src/main/java/com/printplatform/model/Conversation.java`:

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"listing_id", "participant2_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne
    @JoinColumn(name = "participant1_id", nullable = false)
    private User participant1;

    @ManyToOne
    @JoinColumn(name = "participant2_id", nullable = false)
    private User participant2;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Create Message entity**

Create `src/main/java/com/printplatform/model/Message.java`:

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

Note: column named `is_read` to avoid conflict with SQL reserved word `read`.

- [ ] **Step 3: Create repositories**

Create `src/main/java/com/printplatform/repository/ConversationRepository.java`:

```java
package com.printplatform.repository;

import com.printplatform.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByListingIdAndParticipant2Id(UUID listingId, UUID participant2Id);

    @Query("SELECT c FROM Conversation c WHERE c.participant1.id = :userId OR c.participant2.id = :userId ORDER BY c.createdAt DESC")
    List<Conversation> findByParticipantId(UUID userId);
}
```

Create `src/main/java/com/printplatform/repository/MessageRepository.java`:

```java
package com.printplatform.repository;

import com.printplatform.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.read = false")
    long countUnreadInConversation(UUID conversationId, UUID userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender.id <> :userId AND m.read = false AND (m.conversation.participant1.id = :userId OR m.conversation.participant2.id = :userId)")
    long countTotalUnread(UUID userId);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.read = false")
    void markAllRead(UUID conversationId, UUID userId);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC LIMIT 1")
    Message findLastByConversationId(UUID conversationId);
}
```

- [ ] **Step 4: Create DTOs**

Create `src/main/java/com/printplatform/dto/CreateConversationRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CreateConversationRequest {
    @NotNull(message = "Identyfikator zlecenia jest wymagany")
    private UUID listingId;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
}
```

Create `src/main/java/com/printplatform/dto/SendMessageRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {
    @NotBlank(message = "Treść wiadomości jest wymagana")
    @Size(max = 2000, message = "Wiadomość może mieć maksymalnie 2000 znaków")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

Create `src/main/java/com/printplatform/dto/ConversationDto.java`:

```java
package com.printplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConversationDto {
    private UUID id;
    private UUID listingId;
    private String listingTitle;
    private String otherParticipantEmail;
    private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    private long unreadCount;

    public ConversationDto() {}

    public ConversationDto(UUID id, UUID listingId, String listingTitle,
                           String otherParticipantEmail, String lastMessageContent,
                           LocalDateTime lastMessageAt, long unreadCount) {
        this.id = id;
        this.listingId = listingId;
        this.listingTitle = listingTitle;
        this.otherParticipantEmail = otherParticipantEmail;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }
    public String getOtherParticipantEmail() { return otherParticipantEmail; }
    public void setOtherParticipantEmail(String otherParticipantEmail) { this.otherParticipantEmail = otherParticipantEmail; }
    public String getLastMessageContent() { return lastMessageContent; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
}
```

- [ ] **Step 5: Create ConversationController**

Create `src/main/java/com/printplatform/controller/ConversationController.java`:

```java
package com.printplatform.controller;

import com.printplatform.dto.ConversationDto;
import com.printplatform.dto.CreateConversationRequest;
import com.printplatform.dto.SendMessageRequest;
import com.printplatform.model.*;
import com.printplatform.repository.ConversationRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.MessageRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ListingRepository listingRepository;

    public ConversationController(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  ListingRepository listingRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.listingRepository = listingRepository;
    }

    @PostMapping
    public Conversation createOrGet(@Valid @RequestBody CreateConversationRequest request,
                                    @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        if (listing.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie możesz rozpocząć rozmowy z samym sobą");
        }

        return conversationRepository.findByListingIdAndParticipant2Id(listing.getId(), user.getId())
                .orElseGet(() -> {
                    Conversation conv = new Conversation();
                    conv.setListing(listing);
                    conv.setParticipant1(listing.getUser());
                    conv.setParticipant2(user);
                    return conversationRepository.save(conv);
                });
    }

    @GetMapping
    public List<ConversationDto> getMyConversations(@AuthenticationPrincipal User user) {
        List<Conversation> conversations = conversationRepository.findByParticipantId(user.getId());
        return conversations.stream().map(conv -> {
            String otherEmail = conv.getParticipant1().getId().equals(user.getId())
                    ? conv.getParticipant2().getEmail()
                    : conv.getParticipant1().getEmail();
            Message lastMsg = messageRepository.findLastByConversationId(conv.getId());
            long unread = messageRepository.countUnreadInConversation(conv.getId(), user.getId());
            return new ConversationDto(
                    conv.getId(),
                    conv.getListing().getId(),
                    conv.getListing().getTitle(),
                    otherEmail,
                    lastMsg != null ? lastMsg.getContent() : null,
                    lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt(),
                    unread
            );
        }).sorted(Comparator.comparing(ConversationDto::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
          .collect(Collectors.toList());
    }

    @GetMapping("/{id}/messages")
    public List<Message> getMessages(@PathVariable UUID id,
                                     @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message sendMessage(@PathVariable UUID id,
                               @Valid @RequestBody SendMessageRequest request,
                               @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        Message msg = new Message();
        msg.setConversation(conv);
        msg.setSender(user);
        msg.setContent(request.getContent());
        return messageRepository.save(msg);
    }

    @PutMapping("/{id}/read")
    @Transactional
    public void markRead(@PathVariable UUID id,
                         @AuthenticationPrincipal User user) {
        Conversation conv = getConversationIfParticipant(id, user);
        messageRepository.markAllRead(conv.getId(), user.getId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", messageRepository.countTotalUnread(user.getId()));
    }

    private Conversation getConversationIfParticipant(UUID conversationId, User user) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rozmowa nie istnieje"));
        if (!conv.getParticipant1().getId().equals(user.getId())
                && !conv.getParticipant2().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak dostępu do tej rozmowy");
        }
        return conv;
    }
}
```

- [ ] **Step 6: Update SecurityConfig to permit GET on conversations for authenticated users**

In `src/main/java/com/printplatform/security/SecurityConfig.java`, the existing `.anyRequest().authenticated()` already covers `/api/conversations/**`. No change needed — verify this is the case.

- [ ] **Step 7: Handle JSON serialization — prevent infinite recursion**

The `Message` entity has `conversation` → `listing` → `user` references that can cause infinite JSON serialization. Add `@JsonIgnoreProperties` to prevent this.

Update `Conversation.java` — add to imports and annotations:

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
```

Add to the `listing`, `participant1`, `participant2` fields:

```java
@ManyToOne
@JoinColumn(name = "listing_id", nullable = false)
@JsonIgnoreProperties({"user", "stlFileData", "description"})
private Listing listing;

@ManyToOne
@JoinColumn(name = "participant1_id", nullable = false)
@JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
private User participant1;

@ManyToOne
@JoinColumn(name = "participant2_id", nullable = false)
@JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
private User participant2;
```

Update `Message.java` — add to the `conversation` and `sender` fields:

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@ManyToOne
@JoinColumn(name = "conversation_id", nullable = false)
@JsonIgnoreProperties({"participant1", "participant2"})
private Conversation conversation;

@ManyToOne
@JoinColumn(name = "sender_id", nullable = false)
@JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
private User sender;
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/printplatform/model/Conversation.java \
        src/main/java/com/printplatform/model/Message.java \
        src/main/java/com/printplatform/repository/ConversationRepository.java \
        src/main/java/com/printplatform/repository/MessageRepository.java \
        src/main/java/com/printplatform/dto/CreateConversationRequest.java \
        src/main/java/com/printplatform/dto/SendMessageRequest.java \
        src/main/java/com/printplatform/dto/ConversationDto.java \
        src/main/java/com/printplatform/controller/ConversationController.java
git commit -m "feat: add messaging backend — entities, repos, controller"
```

---

### Task 2: Order Tracking Backend — Entities, DTO, Controller Extension

**Files:**
- Create: `src/main/java/com/printplatform/model/OrderTracking.java`
- Create: `src/main/java/com/printplatform/repository/OrderTrackingRepository.java`
- Create: `src/main/java/com/printplatform/dto/UpdateOfferStatusRequest.java`
- Create: `src/main/java/com/printplatform/dto/UpdateTrackingRequest.java`
- Modify: `src/main/java/com/printplatform/model/OfferStatus.java` — add PRINTING, SHIPPED, DELIVERED
- Modify: `src/main/java/com/printplatform/controller/OfferController.java` — add status + tracking endpoints

**Interfaces:**
- Consumes: `Offer` entity, `OfferRepository`, `OfferStatus` enum
- Produces: REST endpoints `PUT /api/offers/{id}/status`, `PUT /api/offers/{id}/tracking`, `GET /api/offers/{id}/tracking`

- [ ] **Step 1: Extend OfferStatus enum**

Replace contents of `src/main/java/com/printplatform/model/OfferStatus.java`:

```java
package com.printplatform.model;

public enum OfferStatus {
    PENDING,
    SELECTED,
    REJECTED,
    PAID,
    PRINTING,
    SHIPPED,
    DELIVERED
}
```

- [ ] **Step 2: Create OrderTracking entity**

Create `src/main/java/com/printplatform/model/OrderTracking.java`:

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    private Offer offer;

    @Column(length = 100)
    private String carrierName;

    @Column(length = 100)
    private String trackingNumber;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 3: Create OrderTrackingRepository**

Create `src/main/java/com/printplatform/repository/OrderTrackingRepository.java`:

```java
package com.printplatform.repository;

import com.printplatform.model.OrderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrderTrackingRepository extends JpaRepository<OrderTracking, UUID> {
    Optional<OrderTracking> findByOfferId(UUID offerId);
}
```

- [ ] **Step 4: Create DTOs**

Create `src/main/java/com/printplatform/dto/UpdateOfferStatusRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateOfferStatusRequest {
    @NotNull(message = "Status jest wymagany")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

Create `src/main/java/com/printplatform/dto/UpdateTrackingRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateTrackingRequest {
    @NotBlank(message = "Nazwa przewoźnika jest wymagana")
    @Size(max = 100, message = "Nazwa przewoźnika jest zbyt długa")
    private String carrierName;

    @NotBlank(message = "Numer przesyłki jest wymagany")
    @Size(max = 100, message = "Numer przesyłki jest zbyt długi")
    private String trackingNumber;

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
}
```

- [ ] **Step 5: Add order tracking endpoints to OfferController**

Add these fields and methods to `src/main/java/com/printplatform/controller/OfferController.java`:

Add to constructor and field:

```java
private final OrderTrackingRepository orderTrackingRepository;

public OfferController(OfferRepository offerRepository,
                       ListingRepository listingRepository,
                       OrderTrackingRepository orderTrackingRepository) {
    this.offerRepository = offerRepository;
    this.listingRepository = listingRepository;
    this.orderTrackingRepository = orderTrackingRepository;
}
```

Add these imports at the top:

```java
import com.printplatform.dto.UpdateOfferStatusRequest;
import com.printplatform.dto.UpdateTrackingRequest;
import com.printplatform.model.OrderTracking;
import com.printplatform.repository.OrderTrackingRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
```

Add these new endpoint methods:

```java
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
```

- [ ] **Step 6: Add `@JsonIgnoreProperties` to OrderTracking**

In `OrderTracking.java`, add to the offer field to prevent serialization loops:

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@OneToOne
@JoinColumn(name = "offer_id", nullable = false, unique = true)
@JsonIgnoreProperties({"listing", "user"})
private Offer offer;
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/printplatform/model/OfferStatus.java \
        src/main/java/com/printplatform/model/OrderTracking.java \
        src/main/java/com/printplatform/repository/OrderTrackingRepository.java \
        src/main/java/com/printplatform/dto/UpdateOfferStatusRequest.java \
        src/main/java/com/printplatform/dto/UpdateTrackingRequest.java \
        src/main/java/com/printplatform/controller/OfferController.java
git commit -m "feat: add order tracking backend — entity, repo, status transitions, tracking endpoints"
```

---

### Task 3: Messaging Frontend — Service + Messaging Page

**Files:**
- Create: `frontend/src/app/services/conversation.service.ts`
- Create: `frontend/src/app/features/messages/messages.component.ts`
- Create: `frontend/src/app/features/messages/messages.component.html`
- Create: `frontend/src/app/features/messages/messages.component.css`
- Modify: `frontend/src/app/app.routes.ts` — add `/wiadomosci` route
- Modify: `frontend/src/app/app.component.html` — add navbar link with badge
- Modify: `frontend/src/app/app.component.ts` — add unread polling
- Modify: `frontend/src/app/app.component.css` — badge styles
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.ts` — wire `openMessage()` to real navigation

**Interfaces:**
- Consumes: `GET/POST /api/conversations`, `GET/POST /api/conversations/{id}/messages`, `PUT /api/conversations/{id}/read`, `GET /api/conversations/unread-count`
- Produces: `ConversationService` (singleton), `MessagesComponent` (standalone), navbar unread badge

- [ ] **Step 1: Create ConversationService**

Create `frontend/src/app/services/conversation.service.ts`:

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ConversationSummary {
  id: string;
  listingId: string;
  listingTitle: string;
  otherParticipantEmail: string;
  lastMessageContent: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface ChatMessage {
  id: string;
  conversation: { id: string };
  sender: { id: string; email: string };
  content: string;
  read: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private http = inject(HttpClient);
  private apiUrl = '/api/conversations';

  getMyConversations(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(this.apiUrl);
  }

  createOrGet(listingId: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(this.apiUrl, { listingId });
  }

  getMessages(conversationId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/${conversationId}/messages`);
  }

  sendMessage(conversationId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiUrl}/${conversationId}/messages`, { content });
  }

  markRead(conversationId: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${conversationId}/read`, {});
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`);
  }
}
```

- [ ] **Step 2: Create MessagesComponent TypeScript**

Create `frontend/src/app/features/messages/messages.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, signal, inject, OnInit, OnDestroy, ElementRef, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { ConversationService, ConversationSummary, ChatMessage } from '../../services/conversation.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-messages',
  imports: [RouterLink, FormsModule, SlicePipe],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessagesComponent implements OnInit, OnDestroy {
  private conversationService = inject(ConversationService);
  private authService = inject(AuthService);

  conversations = signal<ConversationSummary[]>([]);
  loading = signal(true);
  selectedId = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  messagesLoading = signal(false);
  newMessage = signal('');
  sending = signal(false);

  private pollInterval: ReturnType<typeof setInterval> | null = null;
  readonly messageList = viewChild<ElementRef>('messageList');

  currentUserId(): string | null {
    return this.authService.currentUser()?.userId ?? null;
  }

  ngOnInit(): void {
    this.loadConversations();
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  private loadConversations(): void {
    this.loading.set(true);
    this.conversationService.getMyConversations().subscribe({
      next: data => { this.conversations.set(data); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  selectConversation(id: string): void {
    this.selectedId.set(id);
    this.loadMessages(id);
    this.conversationService.markRead(id).subscribe(() => {
      this.conversations.update(list =>
        list.map(c => c.id === id ? { ...c, unreadCount: 0 } : c)
      );
    });
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.pollInterval = setInterval(() => this.loadMessages(id), 10000);
  }

  private loadMessages(conversationId: string): void {
    const isInitial = this.messages().length === 0;
    if (isInitial) this.messagesLoading.set(true);
    this.conversationService.getMessages(conversationId).subscribe({
      next: data => {
        this.messages.set(data);
        this.messagesLoading.set(false);
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => this.messagesLoading.set(false)
    });
  }

  sendMessage(): void {
    const content = this.newMessage().trim();
    const convId = this.selectedId();
    if (!content || !convId) return;
    this.sending.set(true);
    this.conversationService.sendMessage(convId, content).subscribe({
      next: msg => {
        this.messages.update(list => [...list, msg]);
        this.newMessage.set('');
        this.sending.set(false);
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => this.sending.set(false)
    });
  }

  private scrollToBottom(): void {
    const el = this.messageList()?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  isOwn(msg: ChatMessage): boolean {
    return msg.sender.id === this.currentUserId();
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleString('pl-PL', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
  }
}
```

- [ ] **Step 3: Create MessagesComponent template**

Create `frontend/src/app/features/messages/messages.component.html`:

```html
<div class="page">
  <h1 class="page__title">Wiadomości</h1>

  <div class="chat-layout">
    <!-- Conversation list -->
    <aside class="conv-list" aria-label="Lista rozmów">
      @if (loading()) {
        <div class="state-box" aria-busy="true">
          <div class="spinner" aria-hidden="true"></div>
        </div>
      } @else if (conversations().length === 0) {
        <div class="state-box state-box--empty">
          <p>Brak rozmów</p>
        </div>
      } @else {
        <ul class="conv-items">
          @for (conv of conversations(); track conv.id) {
            <li
              class="conv-item"
              [class.conv-item--active]="selectedId() === conv.id"
              [class.conv-item--unread]="conv.unreadCount > 0"
              (click)="selectConversation(conv.id)"
              (keyup.enter)="selectConversation(conv.id)"
              tabindex="0"
              role="button"
              [attr.aria-current]="selectedId() === conv.id ? 'true' : null"
            >
              <div class="conv-item__top">
                <span class="conv-item__title">{{ conv.listingTitle }}</span>
                @if (conv.unreadCount > 0) {
                  <span class="conv-item__badge">{{ conv.unreadCount }}</span>
                }
              </div>
              <span class="conv-item__email">{{ conv.otherParticipantEmail }}</span>
              @if (conv.lastMessageContent) {
                <span class="conv-item__preview">{{ conv.lastMessageContent | slice:0:60 }}</span>
              }
              @if (conv.lastMessageAt) {
                <time class="conv-item__time">{{ formatTime(conv.lastMessageAt) }}</time>
              }
            </li>
          }
        </ul>
      }
    </aside>

    <!-- Conversation view -->
    <section class="chat-view" aria-label="Rozmowa">
      @if (!selectedId()) {
        <div class="chat-view__empty">
          <p>Wybierz rozmowę z listy</p>
        </div>
      } @else {
        @if (messagesLoading()) {
          <div class="state-box" aria-busy="true">
            <div class="spinner" aria-hidden="true"></div>
          </div>
        } @else {
          <div class="chat-view__messages" #messageList>
            @for (msg of messages(); track msg.id) {
              <div class="bubble" [class.bubble--own]="isOwn(msg)" [class.bubble--other]="!isOwn(msg)">
                <span class="bubble__sender">{{ msg.sender.email }}</span>
                <p class="bubble__content">{{ msg.content }}</p>
                <time class="bubble__time">{{ formatTime(msg.createdAt) }}</time>
              </div>
            }
            @if (messages().length === 0) {
              <p class="chat-view__no-messages">Brak wiadomości. Napisz pierwszą!</p>
            }
          </div>

          <form class="chat-input" (ngSubmit)="sendMessage()" novalidate>
            <input
              type="text"
              class="chat-input__field"
              placeholder="Napisz wiadomość..."
              [ngModel]="newMessage()"
              (ngModelChange)="newMessage.set($event)"
              name="message"
              maxlength="2000"
              autocomplete="off"
            />
            <button
              type="submit"
              class="btn btn--primary chat-input__send"
              [disabled]="sending() || !newMessage().trim()"
            >
              @if (sending()) { Wysyłanie... } @else { Wyślij }
            </button>
          </form>
        }
      }
    </section>
  </div>
</div>
```

- [ ] **Step 4: Create MessagesComponent styles**

Create `frontend/src/app/features/messages/messages.component.css`:

```css
.page { max-width: 1100px; margin: 0 auto; }
.page__title {
  font-family: var(--font-display);
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 1.5rem;
  letter-spacing: -0.02em;
}

.chat-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 1rem;
  min-height: 500px;
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
}

/* Conversation list */
.conv-list {
  border-right: 1px solid var(--border);
  overflow-y: auto;
  max-height: 600px;
}
.conv-items { list-style: none; margin: 0; padding: 0; }
.conv-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.875rem 1rem;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  transition: background 0.15s;
}
.conv-item:hover { background: var(--surface-2); }
.conv-item--active { background: var(--accent-light); border-left: 3px solid var(--accent); }
.conv-item--unread .conv-item__title { font-weight: 700; }
.conv-item__top { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
.conv-item__title { font-size: 0.9rem; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-item__badge {
  background: var(--accent);
  color: #fff;
  font-size: 0.6875rem;
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  min-width: 1.25rem;
  text-align: center;
  flex-shrink: 0;
}
.conv-item__email { font-size: 0.8125rem; color: var(--text-muted); }
.conv-item__preview { font-size: 0.8125rem; color: var(--text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-item__time { font-size: 0.75rem; color: var(--text-muted); }

/* Chat view */
.chat-view { display: flex; flex-direction: column; max-height: 600px; }
.chat-view__empty { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--text-muted); }
.chat-view__messages { flex: 1; overflow-y: auto; padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem; }
.chat-view__no-messages { text-align: center; color: var(--text-muted); margin: auto; }

/* Bubbles */
.bubble { max-width: 75%; padding: 0.625rem 0.875rem; border-radius: 12px; display: flex; flex-direction: column; gap: 0.15rem; }
.bubble--own { align-self: flex-end; background: var(--accent); color: #fff; border-bottom-right-radius: 4px; }
.bubble--other { align-self: flex-start; background: var(--surface-2); color: var(--text-primary); border: 1px solid var(--border); border-bottom-left-radius: 4px; }
.bubble__sender { font-size: 0.6875rem; opacity: 0.7; }
.bubble--own .bubble__sender { color: rgba(255,255,255,0.7); }
.bubble__content { margin: 0; font-size: 0.9rem; line-height: 1.45; word-break: break-word; }
.bubble__time { font-size: 0.6875rem; opacity: 0.6; align-self: flex-end; }

/* Input */
.chat-input {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--border);
  background: var(--surface);
}
.chat-input__field {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 8px;
  font-family: inherit;
  font-size: 0.9rem;
  background: var(--bg);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.15s;
}
.chat-input__field:focus { border-color: var(--accent); }
.chat-input__send { flex-shrink: 0; }

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 3rem 1.5rem;
  text-align: center;
  color: var(--text-secondary);
}
.spinner {
  width: 28px; height: 28px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.btn { display: inline-flex; align-items: center; gap: 0.375rem; padding: 0.4rem 0.875rem; border-radius: 7px; font-size: 0.875rem; font-weight: 600; font-family: inherit; cursor: pointer; border: none; transition: background 0.15s; white-space: nowrap; }
.btn--primary { background: var(--accent); color: #fff; }
.btn--primary:hover { background: var(--accent-hover); }
.btn--primary:disabled { opacity: 0.55; cursor: not-allowed; }

@media (max-width: 700px) {
  .chat-layout { grid-template-columns: 1fr; }
  .conv-list { max-height: 250px; border-right: none; border-bottom: 1px solid var(--border); }
  .chat-view { min-height: 350px; }
}
```

- [ ] **Step 5: Add route for messaging page**

In `frontend/src/app/app.routes.ts`, add before the wildcard route:

```typescript
{
  path: 'wiadomosci',
  canActivate: [authGuard],
  loadComponent: () => import('./features/messages/messages.component').then(m => m.MessagesComponent)
},
```

- [ ] **Step 6: Add navbar link with unread badge**

In `frontend/src/app/app.component.ts`, add:

Import at top:

```typescript
import { ConversationService } from './services/conversation.service';
```

Add field and polling:

```typescript
private conversationService = inject(ConversationService);
unreadCount = signal(0);
private unreadInterval: ReturnType<typeof setInterval> | null = null;

constructor() {
  effect(() => {
    this.doc.body.style.overflow = this.menuOpen() ? 'hidden' : '';
  });
  effect(() => {
    if (this.auth.isLoggedIn()) {
      this.pollUnread();
      this.unreadInterval = setInterval(() => this.pollUnread(), 30000);
    } else {
      this.unreadCount.set(0);
      if (this.unreadInterval) { clearInterval(this.unreadInterval); this.unreadInterval = null; }
    }
  });
}

private pollUnread(): void {
  this.conversationService.getUnreadCount().subscribe({
    next: data => this.unreadCount.set(data.count),
    error: () => {}
  });
}
```

In `frontend/src/app/app.component.html`, add after the "Moje zlecenia" link inside `@if (auth.isLoggedIn())`:

```html
<a routerLink="/wiadomosci" routerLinkActive="active" ariaCurrentWhenActive="page" class="nav-messages" (click)="closeMenu()">
  Wiadomości
  @if (unreadCount() > 0) {
    <span class="unread-badge">{{ unreadCount() }}</span>
  }
</a>
```

In `frontend/src/app/app.component.css`, add badge styles:

```css
.nav-messages { position: relative; }
.unread-badge {
  background: var(--accent);
  color: #fff;
  font-size: 0.625rem;
  font-weight: 700;
  padding: 0.1rem 0.35rem;
  border-radius: 999px;
  min-width: 1rem;
  text-align: center;
  margin-left: 0.25rem;
}
```

- [ ] **Step 7: Wire listing detail "Napisz wiadomość" button to real navigation**

In `frontend/src/app/features/listing-detail/listing-detail.component.ts`, replace the placeholder `openMessage()`:

Add imports:

```typescript
import { ConversationService } from '../../services/conversation.service';
```

Add service injection:

```typescript
private readonly conversationService = inject(ConversationService);
```

Replace the `openMessage` method:

```typescript
openMessage(offer: Offer): void {
  const listingId = this.listing()?.id;
  if (!listingId) return;
  this.conversationService.createOrGet(listingId).subscribe({
    next: conv => this.router.navigate(['/wiadomosci'], { queryParams: { conv: conv.id } }),
    error: () => alert('Nie udało się otworzyć rozmowy.')
  });
}
```

Then in `MessagesComponent.ngOnInit()`, add query param handling:

Add import:

```typescript
import { ActivatedRoute } from '@angular/router';
```

Add injection:

```typescript
private route = inject(ActivatedRoute);
```

Update `ngOnInit`:

```typescript
ngOnInit(): void {
  this.loadConversations();
  const convId = this.route.snapshot.queryParamMap.get('conv');
  if (convId) {
    setTimeout(() => this.selectConversation(convId), 500);
  }
}
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/services/conversation.service.ts \
        frontend/src/app/features/messages/messages.component.ts \
        frontend/src/app/features/messages/messages.component.html \
        frontend/src/app/features/messages/messages.component.css \
        frontend/src/app/app.routes.ts \
        frontend/src/app/app.component.ts \
        frontend/src/app/app.component.html \
        frontend/src/app/app.component.css \
        frontend/src/app/features/listing-detail/listing-detail.component.ts
git commit -m "feat: add messaging frontend — conversation service, messages page, navbar badge, listing integration"
```

---

### Task 4: Order Tracking Frontend — Service Extension + My Orders UI

**Files:**
- Modify: `frontend/src/app/services/offer.service.ts` — add status update + tracking methods
- Modify: `frontend/src/app/features/my-orders/my-orders.component.ts` — add order tracking logic
- Modify: `frontend/src/app/features/my-orders/my-orders.component.html` — add tracking timeline UI
- Modify: `frontend/src/app/features/my-orders/my-orders.component.css` — add timeline styles

**Interfaces:**
- Consumes: `PUT /api/offers/{id}/status`, `PUT /api/offers/{id}/tracking`, `GET /api/offers/{id}/tracking`, `GET /api/offers/my`
- Produces: Order tracking timeline UI on My Orders page

- [ ] **Step 1: Extend OfferService with tracking methods**

Add to `frontend/src/app/services/offer.service.ts`:

Add interface:

```typescript
export interface OrderTracking {
  id: string;
  carrierName: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
}
```

Add methods to `OfferService`:

```typescript
getMyOffers(): Observable<Offer[]> {
  return this.http.get<Offer[]>(`${this.apiUrl}/my`);
}

updateOfferStatus(offerId: string, status: string): Observable<Offer> {
  return this.http.put<Offer>(`${this.apiUrl}/${offerId}/status`, { status });
}

updateTracking(offerId: string, carrierName: string, trackingNumber: string): Observable<OrderTracking> {
  return this.http.put<OrderTracking>(`${this.apiUrl}/${offerId}/tracking`, { carrierName, trackingNumber });
}

getTracking(offerId: string): Observable<OrderTracking> {
  return this.http.get<OrderTracking>(`${this.apiUrl}/${offerId}/tracking`);
}
```

- [ ] **Step 2: Update MyOrdersComponent to load seller offers and tracking**

Replace `frontend/src/app/features/my-orders/my-orders.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ListingService, Listing } from '../../services/listing.service';
import { OfferService, Offer, OrderTracking } from '../../services/offer.service';
import { AuthService } from '../../services/auth.service';

interface ListingWithOffers extends Listing {
  offersCount?: number;
}

@Component({
  selector: 'app-my-orders',
  imports: [RouterLink, SlicePipe, FormsModule],
  templateUrl: './my-orders.component.html',
  styleUrl: './my-orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyOrdersComponent implements OnInit {
  private listingService = inject(ListingService);
  private offerService = inject(OfferService);
  private authService = inject(AuthService);
  private http = inject(HttpClient);

  listings = signal<ListingWithOffers[]>([]);
  myOffers = signal<Offer[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  deletingId = signal<string | null>(null);
  closingId = signal<string | null>(null);

  // Order tracking
  activeTab = signal<'listings' | 'offers'>('listings');
  updatingStatusId = signal<string | null>(null);
  trackingData = signal<Record<string, OrderTracking>>({});
  shippingFormId = signal<string | null>(null);
  carrierName = signal('');
  trackingNumber = signal('');
  sendingTracking = signal(false);

  readonly carriers = ['DPD', 'InPost', 'Poczta Polska', 'Kurier', 'Inne'];

  ngOnInit(): void {
    this.load();
    this.loadMyOffers();
  }

  private load(): void {
    this.loading.set(true);
    this.listingService.getMyListings().subscribe({
      next: data => { this.listings.set(data); this.loading.set(false); },
      error: () => { this.error.set('Nie udało się załadować zleceń.'); this.loading.set(false); }
    });
  }

  private loadMyOffers(): void {
    this.offerService.getMyOffers().subscribe({
      next: data => {
        this.myOffers.set(data);
        data.filter(o => ['PRINTING', 'SHIPPED', 'DELIVERED'].includes(o.status ?? ''))
            .forEach(o => this.loadTracking(o.id!));
      },
      error: () => {}
    });
  }

  private loadTracking(offerId: string): void {
    this.offerService.getTracking(offerId).subscribe({
      next: t => this.trackingData.update(d => ({ ...d, [offerId]: t })),
      error: () => {}
    });
  }

  updateStatus(offerId: string, status: string): void {
    this.updatingStatusId.set(offerId);
    this.offerService.updateOfferStatus(offerId, status).subscribe({
      next: updated => {
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: updated.status } : o));
        this.updatingStatusId.set(null);
        if (status === 'PRINTING') this.loadTracking(offerId);
      },
      error: () => this.updatingStatusId.set(null)
    });
  }

  openShippingForm(offerId: string): void {
    this.shippingFormId.set(offerId);
    this.carrierName.set('DPD');
    this.trackingNumber.set('');
  }

  submitTracking(offerId: string): void {
    const carrier = this.carrierName().trim();
    const number = this.trackingNumber().trim();
    if (!carrier || !number) return;
    this.sendingTracking.set(true);
    this.offerService.updateTracking(offerId, carrier, number).subscribe({
      next: t => {
        this.trackingData.update(d => ({ ...d, [offerId]: t }));
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: 'SHIPPED' } : o));
        this.shippingFormId.set(null);
        this.sendingTracking.set(false);
      },
      error: () => this.sendingTracking.set(false)
    });
  }

  confirmDelivery(offerId: string): void {
    this.updatingStatusId.set(offerId);
    this.offerService.updateOfferStatus(offerId, 'DELIVERED').subscribe({
      next: updated => {
        this.myOffers.update(list => list.map(o => o.id === offerId ? { ...o, status: updated.status } : o));
        this.updatingStatusId.set(null);
        this.loadTracking(offerId);
      },
      error: () => this.updatingStatusId.set(null)
    });
  }

  closeListing(id: string): void {
    this.closingId.set(id);
    this.http.put<Listing>(`/api/listings/${id}/close`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? { ...l, status: updated.status } : l));
        this.closingId.set(null);
      },
      error: () => this.closingId.set(null)
    });
  }

  deleteListing(id: string): void {
    if (!confirm('Czy na pewno chcesz usunąć to zlecenie?')) return;
    this.deletingId.set(id);
    this.http.delete(`/api/listings/${id}`).subscribe({
      next: () => { this.listings.update(list => list.filter(l => l.id !== id)); this.deletingId.set(null); },
      error: () => this.deletingId.set(null)
    });
  }

  statusLabel(status: string | undefined): string {
    const map: Record<string, string> = {
      OPEN: 'Otwarte', CLOSED: 'Zamknięte', AWARDED: 'Przyznane',
      PENDING: 'Oczekuje', SELECTED: 'Wybrana', REJECTED: 'Odrzucona', PAID: 'Opłacona',
      PRINTING: 'Drukowanie', SHIPPED: 'Wysłano', DELIVERED: 'Dostarczono'
    };
    return map[status ?? ''] ?? (status ?? '');
  }

  offerStatusStep(status: string | undefined): number {
    const steps: Record<string, number> = { SELECTED: 0, PRINTING: 1, SHIPPED: 2, DELIVERED: 3 };
    return steps[status ?? ''] ?? -1;
  }
}
```

- [ ] **Step 3: Update MyOrders template with tabs and tracking timeline**

Replace `frontend/src/app/features/my-orders/my-orders.component.html`:

```html
<div class="page">
  <header class="page__header">
    <div>
      <h1 class="page__title">Moje zlecenia</h1>
    </div>
    <a routerLink="/dodaj-zlecenie" class="btn btn--primary">+ Dodaj zlecenie</a>
  </header>

  <!-- Tabs -->
  <div class="tabs">
    <button class="tab" [class.tab--active]="activeTab() === 'listings'" (click)="activeTab.set('listings')">
      Moje zlecenia
      @if (!loading()) { <span class="tab__count">{{ listings().length }}</span> }
    </button>
    <button class="tab" [class.tab--active]="activeTab() === 'offers'" (click)="activeTab.set('offers')">
      Moje oferty
      <span class="tab__count">{{ myOffers().length }}</span>
    </button>
  </div>

  <!-- Listings tab (existing) -->
  @if (activeTab() === 'listings') {
    @if (loading()) {
      <div class="state-box" aria-busy="true">
        <div class="spinner" aria-hidden="true"></div>
        <p>Ładowanie zleceń...</p>
      </div>
    } @else if (error()) {
      <div class="state-box state-box--error" role="alert">⚠️ {{ error() }}</div>
    } @else if (listings().length === 0) {
      <div class="state-box state-box--empty">
        <span class="state-box__icon" aria-hidden="true">📭</span>
        <p>Nie masz jeszcze żadnych zleceń.</p>
        <a routerLink="/dodaj-zlecenie" class="btn btn--primary">Dodaj pierwsze zlecenie</a>
      </div>
    } @else {
      <ul class="order-list" aria-label="Lista moich zleceń">
        @for (listing of listings(); track listing.id) {
          <li class="order-card">
            <div class="order-card__top">
              <div class="order-card__meta">
                <span class="status-badge status-badge--{{ (listing.status ?? 'open').toLowerCase() }}">
                  {{ statusLabel(listing.status) }}
                </span>
                @if (listing.createdAt) {
                  <time class="order-card__date" [attr.datetime]="listing.createdAt">
                    {{ listing.createdAt | slice:0:10 }}
                  </time>
                }
              </div>
              <div class="order-card__actions">
                <a [routerLink]="['/zlecenia', listing.id]" class="btn btn--ghost" aria-label="Szczegóły zlecenia">
                  Szczegóły →
                </a>
                @if (listing.status === 'OPEN') {
                  <button
                    class="btn btn--outline-warn"
                    (click)="closeListing(listing.id!)"
                    [disabled]="closingId() === listing.id"
                    aria-label="Zamknij zlecenie"
                  >
                    @if (closingId() === listing.id) { Zamykanie... } @else { Zamknij }
                  </button>
                }
                @if (listing.status !== 'AWARDED') {
                  <button
                    class="btn btn--danger"
                    (click)="deleteListing(listing.id!)"
                    [disabled]="deletingId() === listing.id"
                    aria-label="Usuń zlecenie"
                  >
                    @if (deletingId() === listing.id) { Usuwanie... } @else { Usuń }
                  </button>
                }
              </div>
            </div>

            <h2 class="order-card__title">
              <a [routerLink]="['/zlecenia', listing.id]">{{ listing.title }}</a>
            </h2>

            <p class="order-card__desc">{{ listing.description }}</p>

            <div class="order-card__tags">
              <span class="tag">🧱 {{ listing.requiredMaterial }}</span>
              @if (listing.maxBudget) {
                <span class="tag tag--budget">💰 do {{ listing.maxBudget }} zł</span>
              }
              @if (listing.stlFileUrl) {
                <span class="tag">📎 Plik STL</span>
              }
            </div>
          </li>
        }
      </ul>
    }
  }

  <!-- Offers tab (seller view with tracking) -->
  @if (activeTab() === 'offers') {
    @if (myOffers().length === 0) {
      <div class="state-box state-box--empty">
        <span class="state-box__icon" aria-hidden="true">📭</span>
        <p>Nie złożyłeś jeszcze żadnych ofert.</p>
      </div>
    } @else {
      <ul class="order-list" aria-label="Lista moich ofert">
        @for (offer of myOffers(); track offer.id) {
          <li class="order-card">
            <div class="order-card__top">
              <div class="order-card__meta">
                <span class="status-badge status-badge--{{ (offer.status ?? 'pending').toLowerCase() }}">
                  {{ statusLabel(offer.status) }}
                </span>
                <span class="order-card__price">{{ offer.price }} zł</span>
              </div>
            </div>

            <div class="order-card__details">
              <span>⏱ {{ offer.printingTimeHours }} h</span>
              <span>🧵 {{ offer.filamentGrams }} g</span>
              @if (offer.printerModel) {
                <span>🖨️ {{ offer.printerModel }}</span>
              }
            </div>

            <!-- Order tracking timeline -->
            @if (offerStatusStep(offer.status) >= 0) {
              <div class="timeline">
                <div class="timeline__step" [class.timeline__step--done]="offerStatusStep(offer.status) >= 1" [class.timeline__step--active]="offerStatusStep(offer.status) === 0">
                  <span class="timeline__dot">@if (offerStatusStep(offer.status) >= 1) { ✓ } @else { 1 }</span>
                  <span class="timeline__label">Wybrana</span>
                </div>
                <div class="timeline__line" [class.timeline__line--done]="offerStatusStep(offer.status) >= 1"></div>
                <div class="timeline__step" [class.timeline__step--done]="offerStatusStep(offer.status) >= 2" [class.timeline__step--active]="offerStatusStep(offer.status) === 1">
                  <span class="timeline__dot">@if (offerStatusStep(offer.status) >= 2) { ✓ } @else { 2 }</span>
                  <span class="timeline__label">Drukowanie</span>
                </div>
                <div class="timeline__line" [class.timeline__line--done]="offerStatusStep(offer.status) >= 2"></div>
                <div class="timeline__step" [class.timeline__step--done]="offerStatusStep(offer.status) >= 3" [class.timeline__step--active]="offerStatusStep(offer.status) === 2">
                  <span class="timeline__dot">@if (offerStatusStep(offer.status) >= 3) { ✓ } @else { 3 }</span>
                  <span class="timeline__label">Wysłano</span>
                </div>
                <div class="timeline__line" [class.timeline__line--done]="offerStatusStep(offer.status) >= 3"></div>
                <div class="timeline__step" [class.timeline__step--done]="offerStatusStep(offer.status) >= 3 && offer.status === 'DELIVERED'" [class.timeline__step--active]="offerStatusStep(offer.status) === 3 && offer.status !== 'DELIVERED'">
                  <span class="timeline__dot">@if (offer.status === 'DELIVERED') { ✓ } @else { 4 }</span>
                  <span class="timeline__label">Dostarczono</span>
                </div>
              </div>

              <!-- Tracking info -->
              @if (trackingData()[offer.id!]) {
                <div class="tracking-info">
                  @if (trackingData()[offer.id!].carrierName) {
                    <span>📦 {{ trackingData()[offer.id!].carrierName }}: {{ trackingData()[offer.id!].trackingNumber }}</span>
                  }
                </div>
              }

              <!-- Action buttons -->
              <div class="order-card__tracking-actions">
                @if (offer.status === 'SELECTED') {
                  <button class="btn btn--primary"
                          (click)="updateStatus(offer.id!, 'PRINTING')"
                          [disabled]="updatingStatusId() === offer.id">
                    @if (updatingStatusId() === offer.id) { Aktualizacja... } @else { 🖨️ Rozpocznij druk }
                  </button>
                }
                @if (offer.status === 'PRINTING') {
                  @if (shippingFormId() !== offer.id) {
                    <button class="btn btn--primary" (click)="openShippingForm(offer.id!)">
                      📦 Oznacz jako wysłane
                    </button>
                  } @else {
                    <div class="shipping-form">
                      <select class="shipping-form__select"
                              [ngModel]="carrierName()"
                              (ngModelChange)="carrierName.set($event)"
                              name="carrier">
                        @for (c of carriers; track c) {
                          <option [value]="c">{{ c }}</option>
                        }
                      </select>
                      <input type="text"
                             class="shipping-form__input"
                             placeholder="Numer przesyłki"
                             [ngModel]="trackingNumber()"
                             (ngModelChange)="trackingNumber.set($event)"
                             name="trackingNumber" />
                      <button class="btn btn--primary"
                              (click)="submitTracking(offer.id!)"
                              [disabled]="sendingTracking() || !trackingNumber().trim()">
                        @if (sendingTracking()) { Wysyłanie... } @else { Wyślij }
                      </button>
                      <button class="btn btn--ghost" (click)="shippingFormId.set(null)">Anuluj</button>
                    </div>
                  }
                }
                @if (offer.status === 'SHIPPED') {
                  <button class="btn btn--primary"
                          (click)="updateStatus(offer.id!, 'DELIVERED')"
                          [disabled]="updatingStatusId() === offer.id">
                    @if (updatingStatusId() === offer.id) { Aktualizacja... } @else { ✓ Dostarczono }
                  </button>
                }
              </div>
            }
          </li>
        }
      </ul>
    }
  }
</div>
```

- [ ] **Step 4: Add tracking timeline and tab styles**

Append to `frontend/src/app/features/my-orders/my-orders.component.css`:

```css
/* Tabs */
.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 1.5rem;
  border-bottom: 2px solid var(--border);
}
.tab {
  padding: 0.625rem 1.25rem;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.15s, border-color 0.15s;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.tab:hover { color: var(--text-primary); }
.tab--active { color: var(--accent); border-bottom-color: var(--accent); }
.tab__count {
  background: var(--tag-bg);
  color: var(--tag-text);
  font-size: 0.75rem;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
}

/* Offer details */
.order-card__details { display: flex; flex-wrap: wrap; gap: 0.75rem; font-size: 0.875rem; color: var(--text-secondary); }
.order-card__price { font-weight: 700; color: var(--text-primary); font-size: 1rem; }

/* Timeline */
.timeline {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 1rem 0;
}
.timeline__step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
}
.timeline__dot {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  background: var(--surface-2);
  border: 2px solid var(--border);
  color: var(--text-muted);
  transition: all 0.2s;
}
.timeline__step--active .timeline__dot {
  background: var(--accent-light);
  border-color: var(--accent);
  color: var(--accent);
}
.timeline__step--done .timeline__dot {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.timeline__label { font-size: 0.6875rem; font-weight: 600; color: var(--text-muted); white-space: nowrap; }
.timeline__step--active .timeline__label { color: var(--accent); }
.timeline__step--done .timeline__label { color: var(--text-primary); }
.timeline__line { flex: 1; height: 2px; background: var(--border); min-width: 1.5rem; }
.timeline__line--done { background: var(--accent); }

/* Tracking info */
.tracking-info {
  font-size: 0.875rem;
  color: var(--text-secondary);
  padding: 0.5rem 0;
}

/* Tracking actions */
.order-card__tracking-actions {
  padding-top: 0.5rem;
}

/* Shipping form */
.shipping-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.shipping-form__select,
.shipping-form__input {
  padding: 0.4rem 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 7px;
  font-family: inherit;
  font-size: 0.875rem;
  background: var(--bg);
  color: var(--text-primary);
}
.shipping-form__input { flex: 1; min-width: 150px; }
.shipping-form__select { min-width: 120px; }

/* Status badges for new statuses */
.status-badge--printing  { background: #fef3c7; color: #92400e; }
.status-badge--shipped   { background: #dbeafe; color: #1e40af; }
.status-badge--delivered  { background: var(--success-bg); color: var(--success-text); }
.status-badge--selected  { background: var(--accent-light); color: var(--accent-dark); }
.status-badge--pending   { background: var(--tag-bg); color: var(--tag-text); }
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/services/offer.service.ts \
        frontend/src/app/features/my-orders/my-orders.component.ts \
        frontend/src/app/features/my-orders/my-orders.component.html \
        frontend/src/app/features/my-orders/my-orders.component.css
git commit -m "feat: add order tracking frontend — status timeline, shipping form, seller/buyer flow"
```

---

### Task 5: Update Listing Detail Status Labels + Final Integration

**Files:**
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.ts` — add new status labels
- Modify: `frontend/src/app/features/listing-detail/listing-detail.component.css` — add status badge colors for new statuses

**Interfaces:**
- Consumes: All prior tasks
- Produces: Consistent status display across listing detail and my orders pages

- [ ] **Step 1: Add new status labels to listing-detail component**

In `frontend/src/app/features/listing-detail/listing-detail.component.ts`, update `statusLabel()`:

```typescript
statusLabel(status: string | undefined): string {
  const map: Record<string, string> = {
    OPEN: 'Otwarte', CLOSED: 'Zamknięte', AWARDED: 'Przyznane',
    PENDING: 'Oczekuje', SELECTED: 'Wybrana', REJECTED: 'Odrzucona', PAID: 'Opłacona',
    PRINTING: 'Drukowanie', SHIPPED: 'Wysłano', DELIVERED: 'Dostarczono'
  };
  return map[status ?? ''] ?? status ?? '';
}
```

- [ ] **Step 2: Add new status badge styles to listing-detail CSS**

Append to `frontend/src/app/features/listing-detail/listing-detail.component.css`:

```css
.status-badge--printing  { background: #fef3c7; color: #92400e; }
.status-badge--shipped   { background: #dbeafe; color: #1e40af; }
.status-badge--delivered  { background: var(--success-bg); color: var(--success-text); }
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/listing-detail/listing-detail.component.ts \
        frontend/src/app/features/listing-detail/listing-detail.component.css
git commit -m "feat: add status labels and badge styles for order tracking statuses"
```

---

## Summary

| Task | What | Files |
|------|------|-------|
| 1 | Messaging backend | 8 new files (entities, repos, DTOs, controller) |
| 2 | Order tracking backend | 4 new files + 2 modified (entity, repo, DTOs, controller extension) |
| 3 | Messaging frontend | 4 new files + 4 modified (service, page, navbar, listing integration) |
| 4 | Order tracking frontend | 4 modified files (service extension, my-orders page overhaul) |
| 5 | Status labels integration | 2 modified files (listing-detail consistency) |
