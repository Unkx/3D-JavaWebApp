# Admin Traffic & Moderation Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give admins traffic analytics (page views + API request stats), a revenue overview, user suspension, listing moderation (hide without delete), and an audit log of admin actions.

**Architecture:** Two new lightweight `OncePerRequestFilter`s log raw events (API requests, page views) directly to Postgres on the request thread — no queue, no async, matching this app's current traffic scale. `AdminService` gains read/write methods for suspension, moderation, and revenue (aggregated in Java over existing `Payment` rows, not SQL date-grouping, so behavior is identical between H2 test and Postgres prod). A new `AdminAuditService` is the single write path for the accountability log, called from `AdminService` and from the existing admin-path `deleteListing`. The Angular admin panel — currently one scrolling grid of `<section class="card">` blocks, not tabs — gets new cards for Traffic, Revenue, and Audit Log, plus inline suspend/hide buttons on the existing Users/Listings tables.

**Tech Stack:** Spring Boot (Java 21+), Spring Data JPA / Hibernate (`ddl-auto=update`, no manual migrations), PostgreSQL prod / H2 (`MODE=PostgreSQL`) test, JUnit 5 + Mockito + AssertJ + MockMvc, Angular (standalone components, signals, `inject()`), Vitest + `HttpTestingController`.

## Global Constraints

- Hibernate `ddl-auto=update`: new entities/columns need no manual migration, but any new non-nullable primitive column needs `@ColumnDefault(...)` (see `User.emailVerified` for why — a plain `ALTER TABLE ADD COLUMN` with no default breaks reading existing rows into a primitive).
- Backend package convention: `model` (entities/enums), `dto` (request/response shapes, manual getters/setters, no Lombok), `repository` (Spring Data interfaces), `service` (business logic), `controller` (`@RestController`, thin), `security` (filters/JWT).
- New entities that aren't `User` use Lombok `@Data @NoArgsConstructor @AllArgsConstructor` (matches `Payment`, `Listing` — `User` is the one hand-rolled exception, don't imitate it for new entities).
- All admin endpoints live under `/api/admin/**`, already gated by `SecurityConfig` (`hasRole("ADMIN")`) — no per-endpoint auth code needed.
- Controller tests extend `AbstractControllerTest` (real Spring context + H2, `persistUser(Role)` + `bearerToken(user)` for auth) and are annotated `@Transactional` so each test rolls back.
- Frontend: standalone components, `OnPush`, signals for state, `inject()` not constructor DI (per `frontend/.claude/CLAUDE.md`), native `@if`/`@for` control flow, no `ngClass`/`ngStyle`.
- Aggregation queries (traffic, revenue) fetch rows in range and group in Java, not SQL `DATE()`/`GROUP BY` — avoids H2-vs-Postgres date-function differences given current data volumes.
- Out of scope (per approved spec `docs/superpowers/specs/2026-07-14-admin-traffic-tools-design.md`): retention/pruning jobs for the new log tables, geo-IP/device detection, IP-banning, hiding a listing from its own direct `/api/listings/{id}` detail view (only the public feed/list endpoint is filtered), and audit-logging admin-code redemption (no matching `AdminActionType` in the approved data model — redemption is a self-promotion action, not an admin acting on another target).

---

### Task 1: Audit log write path — `AdminAction` entity + `AdminAuditService`

**Files:**
- Create: `src/main/java/com/printplatform/model/AdminActionType.java`
- Create: `src/main/java/com/printplatform/model/AdminAction.java`
- Create: `src/main/java/com/printplatform/repository/AdminActionRepository.java`
- Create: `src/main/java/com/printplatform/service/AdminAuditService.java`
- Test: `src/test/java/com/printplatform/service/AdminAuditServiceTest.java`

**Interfaces:**
- Produces: `AdminAuditService.log(User admin, AdminActionType actionType, String targetType, UUID targetId, String details)` — used by Tasks 3, 4, 5.
- Produces: `AdminActionRepository.findAllByOrderByCreatedAtDesc(Pageable)` — used by Task 6.

- [ ] **Step 1: Write the failing test**

```java
package com.printplatform.service;

import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AdminActionRepository adminActionRepository;

    private AdminAuditService adminAuditService;

    @BeforeEach
    void setUp() {
        adminAuditService = new AdminAuditService(adminActionRepository);
    }

    @Test
    void log_savesActionWithAdminAndTargetDetails() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);
        UUID targetId = UUID.randomUUID();

        when(adminActionRepository.save(org.mockito.ArgumentMatchers.any(AdminAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adminAuditService.log(admin, AdminActionType.HIDE_LISTING, "Listing", targetId, "spam report");

        ArgumentCaptor<AdminAction> captor = ArgumentCaptor.forClass(AdminAction.class);
        verify(adminActionRepository).save(captor.capture());
        AdminAction saved = captor.getValue();

        assertThat(saved.getAdminId()).isEqualTo(admin.getId());
        assertThat(saved.getAdminEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getActionType()).isEqualTo(AdminActionType.HIDE_LISTING);
        assertThat(saved.getTargetType()).isEqualTo("Listing");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getDetails()).isEqualTo("spam report");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminAuditServiceTest test`
Expected: FAIL — compile error, `AdminAuditService`/`AdminAction`/`AdminActionType`/`AdminActionRepository` don't exist yet.

- [ ] **Step 3: Write the enum**

```java
package com.printplatform.model;

public enum AdminActionType {
    DELETE_LISTING,
    BAN_USER,
    UNBAN_USER,
    HIDE_LISTING,
    UNHIDE_LISTING
}
```

- [ ] **Step 4: Write the entity**

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_action")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID adminId;

    @Column(nullable = false)
    private String adminEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminActionType actionType;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(length = 500)
    private String details;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 5: Write the repository**

```java
package com.printplatform.repository;

import com.printplatform.model.AdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {
    Page<AdminAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 6: Write the service**

```java
package com.printplatform.service;

import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.User;
import com.printplatform.repository.AdminActionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Single write path for the admin accountability log — every admin moderation action goes through here. */
@Service
public class AdminAuditService {

    private final AdminActionRepository adminActionRepository;

    public AdminAuditService(AdminActionRepository adminActionRepository) {
        this.adminActionRepository = adminActionRepository;
    }

    public void log(User admin, AdminActionType actionType, String targetType, UUID targetId, String details) {
        AdminAction action = new AdminAction();
        action.setAdminId(admin.getId());
        action.setAdminEmail(admin.getEmail());
        action.setActionType(actionType);
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setDetails(details);
        adminActionRepository.save(action);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminAuditServiceTest test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/printplatform/model/AdminActionType.java \
        src/main/java/com/printplatform/model/AdminAction.java \
        src/main/java/com/printplatform/repository/AdminActionRepository.java \
        src/main/java/com/printplatform/service/AdminAuditService.java \
        src/test/java/com/printplatform/service/AdminAuditServiceTest.java
git commit -m "feat: add admin action audit log write path"
```

---

### Task 2: User suspension — model field + login rejection + live-token enforcement

**Files:**
- Modify: `src/main/java/com/printplatform/model/User.java`
- Modify: `src/main/java/com/printplatform/service/AuthService.java:146-149` (the `toResponse` method)
- Modify: `src/main/java/com/printplatform/security/JwtAuthFilter.java:39-49`
- Test: `src/test/java/com/printplatform/service/AuthServiceTest.java`
- Test: `src/test/java/com/printplatform/controller/AuthControllerTest.java`

**Interfaces:**
- Produces: `User.isSuspended()` / `User.setSuspended(boolean)` — used by Tasks 4 (AdminService), frontend DTOs.
- Consumes: nothing new from earlier tasks.

- [ ] **Step 1: Write the failing test (login rejects a suspended user)**

Add to `AuthServiceTest.java`:

```java
@Test
void login_suspendedUser_throwsForbidden() {
    User user = buildUser("suspended@example.com", "encoded", Role.USER);
    user.setSuspended(true);
    LoginRequest request = new LoginRequest();
    request.setEmail("suspended@example.com");
    request.setPassword("Secret123");

    when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Secret123", "encoded")).thenReturn(true);

    assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));

    verify(jwtService, never()).generateToken(any(User.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthServiceTest#login_suspendedUser_throwsForbidden test`
Expected: FAIL — `User.setSuspended` doesn't exist yet (compile error).

- [ ] **Step 3: Add the `suspended` field to `User`**

In `src/main/java/com/printplatform/model/User.java`, add next to the existing `emailVerified` field (same pattern — see the comment already there explaining why `@ColumnDefault` is required for a primitive boolean under `ddl-auto=update`):

```java
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean suspended = false;
```

And the getter/setter alongside the other accessors:

```java
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
```

Also update the `isEnabled()` override (currently hardcoded `true`) so any future Spring-Security-native auth path respects it too:

```java
    @Override public boolean isEnabled()               { return !suspended; }
```

- [ ] **Step 4: Reject suspended users in `AuthService.toResponse`**

In `src/main/java/com/printplatform/service/AuthService.java`, change:

```java
    private AuthResponse toResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }
```

to:

```java
    private AuthResponse toResponse(User user) {
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "To konto zostało zawieszone.");
        }
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }
```

`toResponse` is the single exit point for email, Facebook, and Google login, so this covers all three with one change.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthServiceTest test`
Expected: PASS (all `AuthServiceTest` tests, including the new one)

- [ ] **Step 6: Write the failing test (an already-issued JWT stops working the moment the account is suspended)**

Add to `AuthControllerTest.java`:

```java
@Test
void protectedEndpoint_suspendedUserWithLiveToken_returns403() throws Exception {
    User user = persistUser();
    String token = bearerToken(user);

    user.setSuspended(true);
    userRepository.save(user);

    mockMvc.perform(get("/api/listings/my")
                    .header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden());
}
```

(Uses `/api/listings/my`, an existing `authenticated()`-gated GET endpoint, as the probe — no new endpoint needed for this test.)

- [ ] **Step 7: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthControllerTest#protectedEndpoint_suspendedUserWithLiveToken_returns403 test`
Expected: FAIL — the token still authenticates, so the request currently returns 200, not 403.

- [ ] **Step 8: Enforce suspension per-request in `JwtAuthFilter`**

In `src/main/java/com/printplatform/security/JwtAuthFilter.java`, change:

```java
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null && jwtService.isTokenValid(jwt, user)) {
```

to:

```java
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null && jwtService.isTokenValid(jwt, user) && !user.isSuspended()) {
```

A suspended user's request simply falls through unauthenticated (same effect as an expired token) — Spring Security's existing `anyRequest().authenticated()` rule then rejects it, so no custom error response is needed here.

- [ ] **Step 9: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/printplatform/model/User.java \
        src/main/java/com/printplatform/service/AuthService.java \
        src/main/java/com/printplatform/security/JwtAuthFilter.java \
        src/test/java/com/printplatform/service/AuthServiceTest.java \
        src/test/java/com/printplatform/controller/AuthControllerTest.java
git commit -m "feat: reject login and revoke live sessions for suspended users"
```

---

### Task 3: Listing moderation — `moderationStatus` field + public feed filter

**Files:**
- Create: `src/main/java/com/printplatform/model/ListingModerationStatus.java`
- Modify: `src/main/java/com/printplatform/model/Listing.java`
- Modify: `src/main/java/com/printplatform/repository/ListingRepository.java`
- Modify: `src/main/java/com/printplatform/controller/ListingController.java:76-112` (`getOpenListings`)
- Test: `src/test/java/com/printplatform/controller/ListingControllerTest.java`

**Interfaces:**
- Produces: `Listing.getModerationStatus()` / `setModerationStatus(ListingModerationStatus)` (Lombok-generated, field default `VISIBLE`) — used by Task 4.
- Produces: `ListingRepository.findByStatusAndModerationStatus(...)`, `searchByStatusAndModerationStatus(...)` — used only by `ListingController`.

- [ ] **Step 1: Write the failing test**

Add to `ListingControllerTest.java`:

```java
@Test
void getOpenListings_excludesHiddenListings() throws Exception {
    User owner = persistUser();
    Listing visible = new Listing();
    visible.setUser(owner);
    visible.setTitle("Visible listing");
    visible.setStatus(ListingStatus.OPEN);
    listingRepository.save(visible);

    Listing hidden = new Listing();
    hidden.setUser(owner);
    hidden.setTitle("Hidden listing");
    hidden.setStatus(ListingStatus.OPEN);
    hidden.setModerationStatus(ListingModerationStatus.HIDDEN);
    listingRepository.save(hidden);

    mockMvc.perform(get("/api/listings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].title", not(hasItem("Hidden listing"))))
            .andExpect(jsonPath("$.content[*].title", hasItem("Visible listing")));
}
```

Add the needed imports at the top of the test file if not already present: `com.printplatform.model.ListingModerationStatus`, and static imports `org.hamcrest.Matchers.hasItem` / `org.hamcrest.Matchers.not`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListingControllerTest#getOpenListings_excludesHiddenListings test`
Expected: FAIL — `ListingModerationStatus` / `setModerationStatus` don't exist yet (compile error).

- [ ] **Step 3: Write the enum**

```java
package com.printplatform.model;

public enum ListingModerationStatus {
    VISIBLE,
    HIDDEN
}
```

- [ ] **Step 4: Add the field to `Listing`**

In `src/main/java/com/printplatform/model/Listing.java`, add next to the existing `status` field:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingModerationStatus moderationStatus = ListingModerationStatus.VISIBLE;
```

`Listing` already uses Lombok `@Data`, so the getter/setter are generated automatically — no manual accessors needed.

- [ ] **Step 5: Update `ListingRepository`**

In `src/main/java/com/printplatform/repository/ListingRepository.java`, replace the paged `findByStatus` and `searchByStatus` methods (the only two call sites, both in `getOpenListings`) with moderation-aware versions:

```java
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    List<Listing> findByStatus(ListingStatus status);
    Page<Listing> findByStatusAndModerationStatus(ListingStatus status, ListingModerationStatus moderationStatus, Pageable pageable);
    List<Listing> findByUserId(UUID userId);

    @Query("SELECT l FROM Listing l WHERE l.status = :status AND l.moderationStatus = :moderationStatus AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(l.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Listing> searchByStatusAndModerationStatus(@Param("status") ListingStatus status,
                                                     @Param("moderationStatus") ListingModerationStatus moderationStatus,
                                                     @Param("q") String q, Pageable pageable);
}
```

(Add the `import com.printplatform.model.ListingModerationStatus;` line. The unparameterized `findByStatus(ListingStatus)` at the top has no current callers — leave it as-is, unrelated to this change.)

- [ ] **Step 6: Update `ListingController.getOpenListings`**

In `src/main/java/com/printplatform/controller/ListingController.java`, change:

```java
        var resultPage = search.isBlank()
                ? listingRepository.findByStatus(ListingStatus.OPEN, pageable)
                : listingRepository.searchByStatus(ListingStatus.OPEN, search.strip(), pageable);
```

to:

```java
        var resultPage = search.isBlank()
                ? listingRepository.findByStatusAndModerationStatus(ListingStatus.OPEN, ListingModerationStatus.VISIBLE, pageable)
                : listingRepository.searchByStatusAndModerationStatus(ListingStatus.OPEN, ListingModerationStatus.VISIBLE, search.strip(), pageable);
```

Add `import com.printplatform.model.ListingModerationStatus;` to the file's imports.

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -Dtest=ListingControllerTest test`
Expected: PASS (all tests, including the pre-existing `getOpenListings_returnsPageOfOpenListings`, since listings default to `VISIBLE`)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/printplatform/model/ListingModerationStatus.java \
        src/main/java/com/printplatform/model/Listing.java \
        src/main/java/com/printplatform/repository/ListingRepository.java \
        src/main/java/com/printplatform/controller/ListingController.java \
        src/test/java/com/printplatform/controller/ListingControllerTest.java
git commit -m "feat: add listing moderation status, filter hidden listings from public feed"
```

---

### Task 4: `AdminService` suspend/unsuspend + hide/unhide + `AdminController` endpoints

**Files:**
- Modify: `src/main/java/com/printplatform/dto/UserSummaryDto.java`
- Modify: `src/main/java/com/printplatform/dto/AdminListingDto.java`
- Modify: `src/main/java/com/printplatform/service/AdminService.java`
- Modify: `src/main/java/com/printplatform/controller/AdminController.java`
- Test: `src/test/java/com/printplatform/service/AdminServiceTest.java`
- Test: `src/test/java/com/printplatform/controller/AdminControllerTest.java`

**Interfaces:**
- Consumes: `AdminAuditService.log(...)` (Task 1), `User.isSuspended/setSuspended` (Task 2), `Listing.getModerationStatus/setModerationStatus` (Task 3).
- Produces: `AdminService.suspendUser(User admin, UUID userId)`, `unsuspendUser(...)`, `hideListing(User admin, UUID listingId)`, `unhideListing(...)` — all return the updated DTO.

- [ ] **Step 1: Write the failing tests**

Add to `AdminServiceTest.java` (and update the constructor call in `setUp()` — see Step 3):

```java
@Test
void suspendUser_marksSuspendedAndLogsAudit() {
    User admin = buildUser(Role.ADMIN);
    admin.setEmail("admin@example.com");
    User target = buildUser(Role.USER);

    when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UserSummaryDto result = adminService.suspendUser(admin, target.getId());

    assertThat(target.isSuspended()).isTrue();
    assertThat(result.getId()).isEqualTo(target.getId().toString());
    verify(adminAuditService).log(admin, AdminActionType.BAN_USER, "User", target.getId(), null);
}

@Test
void unsuspendUser_clearsSuspendedAndLogsAudit() {
    User admin = buildUser(Role.ADMIN);
    User target = buildUser(Role.USER);
    target.setSuspended(true);

    when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    adminService.unsuspendUser(admin, target.getId());

    assertThat(target.isSuspended()).isFalse();
    verify(adminAuditService).log(admin, AdminActionType.UNBAN_USER, "User", target.getId(), null);
}

@Test
void suspendUser_unknownUser_throwsNotFound() {
    User admin = buildUser(Role.ADMIN);
    UUID missingId = UUID.randomUUID();
    when(userRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminService.suspendUser(admin, missingId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
}

@Test
void hideListing_marksHiddenAndLogsAudit() {
    User admin = buildUser(Role.ADMIN);
    Listing listing = new Listing();
    listing.setId(UUID.randomUUID());
    listing.setTitle("Suspicious listing");
    listing.setUser(buildUser(Role.USER));

    when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
    when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

    adminService.hideListing(admin, listing.getId());

    assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.HIDDEN);
    verify(adminAuditService).log(admin, AdminActionType.HIDE_LISTING, "Listing", listing.getId(), null);
}

@Test
void unhideListing_marksVisibleAndLogsAudit() {
    User admin = buildUser(Role.ADMIN);
    Listing listing = new Listing();
    listing.setId(UUID.randomUUID());
    listing.setTitle("Reviewed listing");
    listing.setUser(buildUser(Role.USER));
    listing.setModerationStatus(ListingModerationStatus.HIDDEN);

    when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
    when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

    adminService.unhideListing(admin, listing.getId());

    assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.VISIBLE);
    verify(adminAuditService).log(admin, AdminActionType.UNHIDE_LISTING, "Listing", listing.getId(), null);
}
```

Add these imports to the test file: `com.printplatform.model.AdminActionType`, `com.printplatform.model.ListingModerationStatus`, `com.printplatform.service.AdminAuditService` (same package, no import needed), and add:

```java
    @Mock
    private AdminAuditService adminAuditService;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminServiceTest test`
Expected: FAIL — compile error, `AdminService` has no `suspendUser`/`unsuspendUser`/`hideListing`/`unhideListing` methods and no matching constructor.

- [ ] **Step 3: Update `AdminServiceTest.setUp()` for the new constructor dependency**

```java
    @BeforeEach
    void setUp() {
        adminService = new AdminService(codeRepository, userRepository, listingRepository, jwtService, adminAuditService);
    }
```

- [ ] **Step 4: Add `suspended`/`moderationStatus` to the admin DTOs**

In `src/main/java/com/printplatform/dto/UserSummaryDto.java`, add the field, constructor line, and getter:

```java
    private boolean suspended;
    // ...
    this.suspended = u.isSuspended();
    // ...
    public boolean isSuspended() { return suspended; }
```

In `src/main/java/com/printplatform/dto/AdminListingDto.java`, add the field, constructor line, and getter:

```java
    private String moderationStatus;
    // ...
    this.moderationStatus = l.getModerationStatus().name();
    // ...
    public String getModerationStatus() { return moderationStatus; }
```

- [ ] **Step 5: Add the new dependency and methods to `AdminService`**

In `src/main/java/com/printplatform/service/AdminService.java`, update the constructor:

```java
    private final AdminCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final JwtService jwtService;
    private final AdminAuditService adminAuditService;

    public AdminService(AdminCodeRepository codeRepository,
                        UserRepository userRepository,
                        ListingRepository listingRepository,
                        JwtService jwtService,
                        AdminAuditService adminAuditService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.jwtService = jwtService;
        this.adminAuditService = adminAuditService;
    }
```

Add imports: `com.printplatform.model.AdminActionType`, `com.printplatform.model.Listing`, `com.printplatform.model.ListingModerationStatus`, `com.printplatform.dto.AdminListingDto` (if not already imported — it is).

Add the four methods:

```java
    /** Suspends a user's account: blocks future logins and revokes any already-issued JWT (admin only). */
    public UserSummaryDto suspendUser(User admin, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        user.setSuspended(true);
        userRepository.save(user);
        adminAuditService.log(admin, AdminActionType.BAN_USER, "User", user.getId(), null);
        return new UserSummaryDto(user);
    }

    /** Lifts a suspension (admin only). */
    public UserSummaryDto unsuspendUser(User admin, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        user.setSuspended(false);
        userRepository.save(user);
        adminAuditService.log(admin, AdminActionType.UNBAN_USER, "User", user.getId(), null);
        return new UserSummaryDto(user);
    }

    /** Hides a listing from the public feed without deleting it (admin only). */
    public AdminListingDto hideListing(User admin, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        listing.setModerationStatus(ListingModerationStatus.HIDDEN);
        listingRepository.save(listing);
        adminAuditService.log(admin, AdminActionType.HIDE_LISTING, "Listing", listing.getId(), null);
        return new AdminListingDto(listing);
    }

    /** Restores a previously hidden listing to the public feed (admin only). */
    public AdminListingDto unhideListing(User admin, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        listing.setModerationStatus(ListingModerationStatus.VISIBLE);
        listingRepository.save(listing);
        adminAuditService.log(admin, AdminActionType.UNHIDE_LISTING, "Listing", listing.getId(), null);
        return new AdminListingDto(listing);
    }
```

- [ ] **Step 6: Add the endpoints to `AdminController`**

In `src/main/java/com/printplatform/controller/AdminController.java`, add:

```java
    /** Suspend a user's account (admin only). */
    @PutMapping("/users/{id}/suspend")
    public UserSummaryDto suspendUser(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.suspendUser(admin, id);
    }

    /** Lift a user's suspension (admin only). */
    @PutMapping("/users/{id}/unsuspend")
    public UserSummaryDto unsuspendUser(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.unsuspendUser(admin, id);
    }

    /** Hide a listing from the public feed without deleting it (admin only). */
    @PutMapping("/listings/{id}/hide")
    public AdminListingDto hideListing(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.hideListing(admin, id);
    }

    /** Restore a hidden listing to the public feed (admin only). */
    @PutMapping("/listings/{id}/unhide")
    public AdminListingDto unhideListing(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.unhideListing(admin, id);
    }
```

`AdminController.java` currently has no `java.util.UUID` import (only `java.util.List`) — add `import java.util.UUID;` alongside it.

- [ ] **Step 7: Write the controller integration tests**

Add to `AdminControllerTest.java`:

```java
@Test
void suspendUser_admin_returns200AndMarksSuspended() throws Exception {
    User admin = persistUser(Role.ADMIN);
    User target = persistUser();

    mockMvc.perform(put("/api/admin/users/" + target.getId() + "/suspend")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suspended").value(true));
}

@Test
void suspendUser_nonAdmin_returns403() throws Exception {
    User user = persistUser();
    User target = persistUser();

    mockMvc.perform(put("/api/admin/users/" + target.getId() + "/suspend")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
            .andExpect(status().isForbidden());
}

@Test
void hideListing_admin_returns200AndMarksHidden() throws Exception {
    User admin = persistUser(Role.ADMIN);
    User owner = persistUser();
    Listing listing = new Listing();
    listing.setUser(owner);
    listing.setTitle("Test listing");
    Listing saved = listingRepository.save(listing);

    mockMvc.perform(put("/api/admin/listings/" + saved.getId() + "/hide")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.moderationStatus").value("HIDDEN"));
}
```

Add `@Autowired private ListingRepository listingRepository;` and the `import com.printplatform.model.Listing;` / `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;` to `AdminControllerTest.java` if not already present.

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn -q -Dtest=AdminServiceTest,AdminControllerTest test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/printplatform/dto/UserSummaryDto.java \
        src/main/java/com/printplatform/dto/AdminListingDto.java \
        src/main/java/com/printplatform/service/AdminService.java \
        src/main/java/com/printplatform/controller/AdminController.java \
        src/test/java/com/printplatform/service/AdminServiceTest.java \
        src/test/java/com/printplatform/controller/AdminControllerTest.java
git commit -m "feat: add admin endpoints to suspend users and hide listings"
```

---

### Task 5: Retrofit audit logging into the existing admin-path listing delete

**Files:**
- Modify: `src/main/java/com/printplatform/controller/ListingController.java:166-178` (`deleteListing`)
- Test: `src/test/java/com/printplatform/controller/ListingControllerTest.java`

**Interfaces:**
- Consumes: `AdminAuditService.log(...)` (Task 1).

- [ ] **Step 1: Write the failing test**

Add to `ListingControllerTest.java`:

```java
@Test
void deleteListing_byAdminNotOwner_writesAuditRow() throws Exception {
    User owner = persistUser();
    User admin = persistUser(Role.ADMIN);
    Listing listing = new Listing();
    listing.setUser(owner);
    listing.setTitle("To be removed");
    Listing saved = listingRepository.save(listing);

    mockMvc.perform(delete("/api/listings/" + saved.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isNoContent());

    List<AdminAction> actions = adminActionRepository.findAll();
    assertThat(actions).hasSize(1);
    assertThat(actions.get(0).getActionType()).isEqualTo(AdminActionType.DELETE_LISTING);
    assertThat(actions.get(0).getTargetId()).isEqualTo(saved.getId());
    assertThat(actions.get(0).getAdminEmail()).isEqualTo(admin.getEmail());
}

@Test
void deleteListing_byOwner_writesNoAuditRow() throws Exception {
    User owner = persistUser();
    Listing listing = new Listing();
    listing.setUser(owner);
    listing.setTitle("My own listing");
    Listing saved = listingRepository.save(listing);

    mockMvc.perform(delete("/api/listings/" + saved.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
            .andExpect(status().isNoContent());

    assertThat(adminActionRepository.findAll()).isEmpty();
}
```

Add `@Autowired private AdminActionRepository adminActionRepository;` and imports `com.printplatform.model.AdminAction`, `com.printplatform.model.AdminActionType`, `com.printplatform.repository.AdminActionRepository`, `java.util.List` to `ListingControllerTest.java` if not already present.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListingControllerTest#deleteListing_byAdminNotOwner_writesAuditRow test`
Expected: FAIL — no audit row is written yet, `actions` is empty.

- [ ] **Step 3: Wire `AdminAuditService` into `ListingController`**

In `src/main/java/com/printplatform/controller/ListingController.java`, add the dependency:

```java
    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final StlFileRepository stlFileRepository;
    private final AdminAuditService adminAuditService;

    public ListingController(ListingRepository listingRepository,
                             OfferRepository offerRepository,
                             StlFileRepository stlFileRepository,
                             AdminAuditService adminAuditService) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
        this.stlFileRepository = stlFileRepository;
        this.adminAuditService = adminAuditService;
    }
```

Add imports: `com.printplatform.model.AdminActionType`, `com.printplatform.service.AdminAuditService`.

Then change `deleteListing`:

```java
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteListing(@PathVariable UUID id,
                              @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        boolean isAdminAction = user.getRole() == Role.ADMIN && !listing.getUser().getId().equals(user.getId());
        if (isAdminAction) {
            adminAuditService.log(user, AdminActionType.DELETE_LISTING, "Listing", id, listing.getTitle());
        }
        // Remove dependent rows first (listing_id FKs are non-nullable).
        offerRepository.deleteAll(offerRepository.findByListingId(id));
        stlFileRepository.deleteByListingId(id);
        listingRepository.delete(listing);
    }
```

(Only logs when an admin deletes someone *else's* listing — an owner deleting their own listing, even an admin-role owner, isn't a moderation action.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=ListingControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/printplatform/controller/ListingController.java \
        src/test/java/com/printplatform/controller/ListingControllerTest.java
git commit -m "feat: audit-log admin listing deletions"
```

---

### Task 6: Audit log read endpoint

**Files:**
- Create: `src/main/java/com/printplatform/dto/AdminActionDto.java`
- Modify: `src/main/java/com/printplatform/service/AdminService.java`
- Modify: `src/main/java/com/printplatform/controller/AdminController.java`
- Test: `src/test/java/com/printplatform/service/AdminServiceTest.java`
- Test: `src/test/java/com/printplatform/controller/AdminControllerTest.java`

**Interfaces:**
- Consumes: `AdminActionRepository.findAllByOrderByCreatedAtDesc(Pageable)` (Task 1), `PageResponse<T>` (existing).
- Produces: `AdminService.getAuditLog(int page, int size)` returning `PageResponse<AdminActionDto>`.

- [ ] **Step 1: Write the failing test**

Add to `AdminServiceTest.java`:

```java
@Test
void getAuditLog_returnsPagedDtos() {
    AdminAction action = new AdminAction();
    action.setAdminId(UUID.randomUUID());
    action.setAdminEmail("admin@example.com");
    action.setActionType(AdminActionType.HIDE_LISTING);
    action.setTargetType("Listing");
    action.setTargetId(UUID.randomUUID());

    Page<AdminAction> page = new PageImpl<>(List.of(action));
    when(adminActionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

    PageResponse<AdminActionDto> result = adminService.getAuditLog(0, 20);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getActionType()).isEqualTo("HIDE_LISTING");
    assertThat(result.getContent().get(0).getAdminEmail()).isEqualTo("admin@example.com");
}
```

Add imports: `com.printplatform.model.AdminAction`, `com.printplatform.model.AdminActionType`, `com.printplatform.dto.AdminActionDto`, `com.printplatform.dto.PageResponse`, `org.springframework.data.domain.Page`, `org.springframework.data.domain.PageImpl`, `org.springframework.data.domain.Pageable`, `java.util.List` (adjust for what's already imported).

Also add `@Mock private AdminActionRepository adminActionRepository;` and update `setUp()`:

```java
    @BeforeEach
    void setUp() {
        adminService = new AdminService(codeRepository, userRepository, listingRepository, jwtService,
                adminAuditService, adminActionRepository);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminServiceTest test`
Expected: FAIL — compile error, `AdminService.getAuditLog` doesn't exist, constructor signature mismatch.

- [ ] **Step 3: Write `AdminActionDto`**

```java
package com.printplatform.dto;

import com.printplatform.model.AdminAction;
import java.time.LocalDateTime;

public class AdminActionDto {
    private String id;
    private String adminEmail;
    private String actionType;
    private String targetType;
    private String targetId;
    private String details;
    private LocalDateTime createdAt;

    public AdminActionDto(AdminAction a) {
        this.id         = a.getId().toString();
        this.adminEmail = a.getAdminEmail();
        this.actionType = a.getActionType().name();
        this.targetType = a.getTargetType();
        this.targetId   = a.getTargetId().toString();
        this.details    = a.getDetails();
        this.createdAt  = a.getCreatedAt();
    }

    public String getId()          { return id; }
    public String getAdminEmail()  { return adminEmail; }
    public String getActionType()  { return actionType; }
    public String getTargetType()  { return targetType; }
    public String getTargetId()    { return targetId; }
    public String getDetails()     { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Add the dependency and method to `AdminService`**

Add `adminActionRepository` as a fifth-turned-sixth constructor parameter:

```java
    private final AdminCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final JwtService jwtService;
    private final AdminAuditService adminAuditService;
    private final AdminActionRepository adminActionRepository;

    public AdminService(AdminCodeRepository codeRepository,
                        UserRepository userRepository,
                        ListingRepository listingRepository,
                        JwtService jwtService,
                        AdminAuditService adminAuditService,
                        AdminActionRepository adminActionRepository) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.jwtService = jwtService;
        this.adminAuditService = adminAuditService;
        this.adminActionRepository = adminActionRepository;
    }
```

Add imports: `com.printplatform.model.AdminAction`, `com.printplatform.dto.AdminActionDto`, `com.printplatform.repository.AdminActionRepository`, `org.springframework.data.domain.Page`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Pageable`.

Add the method:

```java
    /** Paged, newest-first admin action history (admin only). */
    public PageResponse<AdminActionDto> getAuditLog(int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AdminAction> result = adminActionRepository.findAllByOrderByCreatedAtDesc(pageable);
        return new PageResponse<>(result.map(AdminActionDto::new));
    }
```

Add `import com.printplatform.dto.PageResponse;`.

- [ ] **Step 5: Add the endpoint to `AdminController`**

```java
    /** Paged admin action history, newest first (admin only). */
    @GetMapping("/audit-log")
    public PageResponse<AdminActionDto> getAuditLog(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return adminService.getAuditLog(page, size);
    }
```

Add imports `com.printplatform.dto.AdminActionDto`, `com.printplatform.dto.PageResponse` to `AdminController.java`.

- [ ] **Step 6: Write the controller integration test**

Add to `AdminControllerTest.java`:

```java
@Test
void getAuditLog_admin_returns200() throws Exception {
    User admin = persistUser(Role.ADMIN);

    mockMvc.perform(get("/api/admin/audit-log")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
}

@Test
void getAuditLog_nonAdmin_returns403() throws Exception {
    User user = persistUser();

    mockMvc.perform(get("/api/admin/audit-log")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -q -Dtest=AdminServiceTest,AdminControllerTest test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/printplatform/dto/AdminActionDto.java \
        src/main/java/com/printplatform/service/AdminService.java \
        src/main/java/com/printplatform/controller/AdminController.java \
        src/test/java/com/printplatform/service/AdminServiceTest.java \
        src/test/java/com/printplatform/controller/AdminControllerTest.java
git commit -m "feat: add paged admin audit log endpoint"
```

---

### Task 7: API request logging filter

**Files:**
- Create: `src/main/java/com/printplatform/security/ClientIpResolver.java`
- Modify: `src/main/java/com/printplatform/security/AuthRateLimitFilter.java:71-81` (delegate to the new resolver)
- Create: `src/main/java/com/printplatform/model/ApiRequestLog.java`
- Create: `src/main/java/com/printplatform/repository/ApiRequestLogRepository.java`
- Create: `src/main/java/com/printplatform/security/ApiRequestLoggingFilter.java`
- Modify: `src/main/java/com/printplatform/security/SecurityConfig.java`
- Test: `src/test/java/com/printplatform/security/ApiRequestLoggingFilterTest.java`

**Interfaces:**
- Produces: `ApiRequestLogRepository.findByCreatedAtAfter(LocalDateTime)` — used by Task 9.
- Produces: `ClientIpResolver.resolve(HttpServletRequest)` — reused by Task 8's `PageViewRateLimiter`.

- [ ] **Step 1: Extract `ClientIpResolver` (small in-scope cleanup — the same X-Forwarded-For logic is needed by Task 8's rate limiter, so centralize it now instead of duplicating the security-sensitive parsing)**

```java
package com.printplatform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind Render's edge load balancer, which APPENDS the
 * connecting IP as the LAST entry of X-Forwarded-For rather than replacing the header —
 * the FIRST hop is attacker-controlled and must never be trusted for rate-limiting or logging.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].strip();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
```

In `AuthRateLimitFilter.java`, replace the body of `clientIp(...)` with a delegation (keep the method and its doc comment — just delegate):

```java
    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
```

Run: `mvn -q -Dtest=AuthRateLimitFilter* test` (if such a test class exists; otherwise skip — this is a pure refactor of identical logic, verified by the full suite in later steps) to confirm nothing broke.

- [ ] **Step 2: Write the failing test for the logging filter**

```java
package com.printplatform.security;

import com.printplatform.model.ApiRequestLog;
import com.printplatform.repository.ApiRequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiRequestLoggingFilterTest {

    @Mock private ApiRequestLogRepository apiRequestLogRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private ApiRequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiRequestLoggingFilter(apiRequestLogRepository);
    }

    @Test
    void doFilterInternal_logsMethodPathStatusAndDuration() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/listings");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        ArgumentCaptor<ApiRequestLog> captor = ArgumentCaptor.forClass(ApiRequestLog.class);
        verify(apiRequestLogRepository).save(captor.capture());
        ApiRequestLog saved = captor.getValue();
        assertThat(saved.getMethod()).isEqualTo("GET");
        assertThat(saved.getPath()).isEqualTo("/api/listings");
        assertThat(saved.getStatus()).isEqualTo(200);
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldNotFilter_analyticsAndTrafficPathsAreExcluded() {
        when(request.getRequestURI()).thenReturn("/api/analytics/pageview");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/admin/traffic");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/listings");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -Dtest=ApiRequestLoggingFilterTest test`
Expected: FAIL — `ApiRequestLoggingFilter`/`ApiRequestLog`/`ApiRequestLogRepository` don't exist yet.

- [ ] **Step 4: Write the entity**

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_request_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private long durationMs;

    private UUID userId;

    private String ip;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 5: Write the repository**

```java
package com.printplatform.repository;

import com.printplatform.model.ApiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, UUID> {
    List<ApiRequestLog> findByCreatedAtAfter(LocalDateTime since);
}
```

- [ ] **Step 6: Write the filter**

```java
package com.printplatform.security;

import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.User;
import com.printplatform.repository.ApiRequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs one row per /api/** call for the admin traffic dashboard. Runs after JwtAuthFilter
 * in the chain, so SecurityContextHolder already has the resolved principal, if any.
 * Logging failures are swallowed — they must never mask or break the real response.
 */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private final ApiRequestLogRepository apiRequestLogRepository;

    public ApiRequestLoggingFilter(ApiRequestLogRepository apiRequestLogRepository) {
        this.apiRequestLogRepository = apiRequestLogRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/analytics/") || uri.equals("/api/admin/traffic");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                ApiRequestLog log = new ApiRequestLog();
                log.setMethod(request.getMethod());
                log.setPath(request.getRequestURI());
                log.setStatus(response.getStatus());
                log.setDurationMs(System.currentTimeMillis() - start);
                log.setUserId(resolveUserId());
                log.setIp(ClientIpResolver.resolve(request));
                apiRequestLogRepository.save(log);
            } catch (Exception ignored) {
                // Best-effort logging only.
            }
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
```

- [ ] **Step 7: Register the filter in `SecurityConfig`**

In `src/main/java/com/printplatform/security/SecurityConfig.java`, add the dependency:

```java
    private final JwtAuthFilter jwtAuthFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final ApiRequestLoggingFilter apiRequestLoggingFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AuthRateLimitFilter authRateLimitFilter,
                          ApiRequestLoggingFilter apiRequestLoggingFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.apiRequestLoggingFilter = apiRequestLoggingFilter;
    }
```

And register it after the JWT filter so the security context is already populated:

```java
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(authRateLimitFilter, JwtAuthFilter.class)
            .addFilterAfter(apiRequestLoggingFilter, JwtAuthFilter.class);
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn -q -Dtest=ApiRequestLoggingFilterTest test`
Expected: PASS

- [ ] **Step 9: Run the full backend suite to confirm the `SecurityConfig`/`ClientIpResolver` changes didn't break anything**

Run: `mvn -q test`
Expected: PASS (all existing tests still green)

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/printplatform/security/ClientIpResolver.java \
        src/main/java/com/printplatform/security/AuthRateLimitFilter.java \
        src/main/java/com/printplatform/model/ApiRequestLog.java \
        src/main/java/com/printplatform/repository/ApiRequestLogRepository.java \
        src/main/java/com/printplatform/security/ApiRequestLoggingFilter.java \
        src/main/java/com/printplatform/security/SecurityConfig.java \
        src/test/java/com/printplatform/security/ApiRequestLoggingFilterTest.java
git commit -m "feat: log API requests for the admin traffic dashboard"
```

---

### Task 8: Public page-view tracking endpoint

**Files:**
- Create: `src/main/java/com/printplatform/model/PageView.java`
- Create: `src/main/java/com/printplatform/repository/PageViewRepository.java`
- Create: `src/main/java/com/printplatform/security/PageViewRateLimiter.java`
- Create: `src/main/java/com/printplatform/dto/PageViewRequest.java`
- Create: `src/main/java/com/printplatform/service/AnalyticsService.java` (write side only — read side added in Task 9)
- Create: `src/main/java/com/printplatform/controller/AnalyticsController.java`
- Modify: `src/main/java/com/printplatform/security/SecurityConfig.java`
- Test: `src/test/java/com/printplatform/controller/AnalyticsControllerTest.java`

**Interfaces:**
- Produces: `PageViewRepository.findByCreatedAtAfter(LocalDateTime)` — used by Task 9.
- Produces: `AnalyticsService.recordPageView(String path, UUID userId, String sessionId, String referrer)`.

- [ ] **Step 1: Write the failing test**

```java
package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.PageViewRequest;
import com.printplatform.repository.PageViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AnalyticsControllerTest extends AbstractControllerTest {

    @Autowired
    private PageViewRepository pageViewRepository;

    @Test
    void trackPageView_anonymous_recordsRow() throws Exception {
        PageViewRequest request = new PageViewRequest();
        request.setPath("/zlecenia");
        request.setSessionId("session-123");

        mockMvc.perform(post("/api/analytics/pageview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(pageViewRepository.findAll()).hasSize(1);
        assertThat(pageViewRepository.findAll().get(0).getPath()).isEqualTo("/zlecenia");
        assertThat(pageViewRepository.findAll().get(0).getSessionId()).isEqualTo("session-123");
    }

    @Test
    void trackPageView_blankPath_returns400() throws Exception {
        PageViewRequest request = new PageViewRequest();
        request.setPath("");
        request.setSessionId("session-123");

        mockMvc.perform(post("/api/analytics/pageview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AnalyticsControllerTest test`
Expected: FAIL — none of the new classes exist yet.

- [ ] **Step 3: Write the entity**

```java
package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "page_view")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageView {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String path;

    private UUID userId;

    @Column(nullable = false)
    private String sessionId;

    private String referrer;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 4: Write the repository**

```java
package com.printplatform.repository;

import com.printplatform.model.PageView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PageViewRepository extends JpaRepository<PageView, UUID> {
    List<PageView> findByCreatedAtAfter(LocalDateTime since);
}
```

- [ ] **Step 5: Write the rate limiter**

```java
package com.printplatform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps unauthenticated calls to /api/analytics/pageview per client IP, so the endpoint can't
 * be used to spam-fill the page_view table. Window is generous (unlike AuthRateLimitFilter's
 * login throttle) since normal browsing legitimately fires this once per navigation.
 */
@Component
public class PageViewRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_PER_WINDOW = 120;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        Window window = windows.computeIfAbsent(ip, k -> new Window());
        return !window.tooManyRequests();
    }

    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean tooManyRequests() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            return ++count > MAX_PER_WINDOW;
        }
    }
}
```

- [ ] **Step 6: Write the request DTO**

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PageViewRequest {
    @NotBlank
    @Size(max = 500)
    private String path;

    @NotBlank
    @Size(max = 100)
    private String sessionId;

    @Size(max = 500)
    private String referrer;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
}
```

- [ ] **Step 7: Write the service (write side)**

```java
package com.printplatform.service;

import com.printplatform.model.PageView;
import com.printplatform.repository.PageViewRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AnalyticsService {

    private final PageViewRepository pageViewRepository;

    public AnalyticsService(PageViewRepository pageViewRepository) {
        this.pageViewRepository = pageViewRepository;
    }

    public void recordPageView(String path, UUID userId, String sessionId, String referrer) {
        PageView view = new PageView();
        view.setPath(path);
        view.setUserId(userId);
        view.setSessionId(sessionId);
        view.setReferrer(referrer);
        pageViewRepository.save(view);
    }
}
```

- [ ] **Step 8: Write the controller**

```java
package com.printplatform.controller;

import com.printplatform.dto.PageViewRequest;
import com.printplatform.model.User;
import com.printplatform.security.PageViewRateLimiter;
import com.printplatform.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final PageViewRateLimiter rateLimiter;

    public AnalyticsController(AnalyticsService analyticsService, PageViewRateLimiter rateLimiter) {
        this.analyticsService = analyticsService;
        this.rateLimiter = rateLimiter;
    }

    /** Records one page-view event (public, unauthenticated, rate-limited). Best-effort — never errors out. */
    @PostMapping("/pageview")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trackPageView(@Valid @RequestBody PageViewRequest request,
                              @AuthenticationPrincipal User user,
                              HttpServletRequest servletRequest) {
        if (!rateLimiter.allow(servletRequest)) {
            return;
        }
        analyticsService.recordPageView(
                request.getPath(),
                user != null ? user.getId() : null,
                request.getSessionId(),
                request.getReferrer());
    }
}
```

- [ ] **Step 9: Permit the endpoint in `SecurityConfig`**

In `src/main/java/com/printplatform/security/SecurityConfig.java`, add to `authorizeHttpRequests`:

```java
                .requestMatchers(HttpMethod.POST, "/api/analytics/pageview").permitAll()
```

(place it alongside the other `permitAll()` rules, before `.requestMatchers("/api/admin/**").hasRole("ADMIN")`)

- [ ] **Step 10: Run tests to verify they pass**

Run: `mvn -q -Dtest=AnalyticsControllerTest test`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/printplatform/model/PageView.java \
        src/main/java/com/printplatform/repository/PageViewRepository.java \
        src/main/java/com/printplatform/security/PageViewRateLimiter.java \
        src/main/java/com/printplatform/dto/PageViewRequest.java \
        src/main/java/com/printplatform/service/AnalyticsService.java \
        src/main/java/com/printplatform/controller/AnalyticsController.java \
        src/main/java/com/printplatform/security/SecurityConfig.java \
        src/test/java/com/printplatform/controller/AnalyticsControllerTest.java
git commit -m "feat: add public rate-limited page-view tracking endpoint"
```

---

### Task 9: Traffic summary endpoint

**Files:**
- Create: `src/main/java/com/printplatform/dto/DailyCountDto.java`
- Create: `src/main/java/com/printplatform/dto/PathCountDto.java`
- Create: `src/main/java/com/printplatform/dto/ApiStatsDto.java`
- Create: `src/main/java/com/printplatform/dto/TrafficSummaryDto.java`
- Modify: `src/main/java/com/printplatform/service/AnalyticsService.java`
- Modify: `src/main/java/com/printplatform/controller/AdminController.java`
- Test: `src/test/java/com/printplatform/service/AnalyticsServiceTest.java`
- Test: `src/test/java/com/printplatform/controller/AdminControllerTest.java`

**Interfaces:**
- Consumes: `PageViewRepository.findByCreatedAtAfter` (Task 8), `ApiRequestLogRepository.findByCreatedAtAfter` (Task 7).
- Produces: `AnalyticsService.getTrafficSummary(int days)` returning `TrafficSummaryDto`.

- [ ] **Step 1: Write the failing test**

```java
package com.printplatform.service;

import com.printplatform.dto.TrafficSummaryDto;
import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.PageView;
import com.printplatform.repository.ApiRequestLogRepository;
import com.printplatform.repository.PageViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private PageViewRepository pageViewRepository;
    @Mock private ApiRequestLogRepository apiRequestLogRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(pageViewRepository, apiRequestLogRepository);
    }

    private PageView view(String path, LocalDateTime createdAt) {
        PageView v = new PageView();
        v.setPath(path);
        v.setSessionId("s1");
        v.setCreatedAt(createdAt);
        return v;
    }

    private ApiRequestLog apiLog(int status, long durationMs) {
        ApiRequestLog l = new ApiRequestLog();
        l.setMethod("GET");
        l.setPath("/api/listings");
        l.setStatus(status);
        l.setDurationMs(durationMs);
        l.setCreatedAt(LocalDateTime.now());
        return l;
    }

    @Test
    void getTrafficSummary_groupsPageViewsByDayAndTopPaths() {
        LocalDateTime today = LocalDateTime.now();
        when(pageViewRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
                view("/zlecenia", today),
                view("/zlecenia", today),
                view("/", today)
        ));
        when(apiRequestLogRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
                apiLog(200, 100),
                apiLog(500, 300)
        ));

        TrafficSummaryDto summary = analyticsService.getTrafficSummary(7);

        assertThat(summary.getPageViewsByDay()).hasSize(1);
        assertThat(summary.getPageViewsByDay().get(0).getCount()).isEqualTo(3);
        assertThat(summary.getTopPaths()).extracting("path").contains("/zlecenia", "/");
        assertThat(summary.getTopPaths().get(0).getPath()).isEqualTo("/zlecenia");
        assertThat(summary.getTopPaths().get(0).getCount()).isEqualTo(2);
        assertThat(summary.getApiStats().getTotalRequests()).isEqualTo(2);
        assertThat(summary.getApiStats().getErrorCount()).isEqualTo(1);
        assertThat(summary.getApiStats().getAvgDurationMs()).isEqualTo(200.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AnalyticsServiceTest test`
Expected: FAIL — `AnalyticsService` has no matching two-arg constructor, DTOs don't exist.

- [ ] **Step 3: Write the DTOs**

```java
package com.printplatform.dto;

public class DailyCountDto {
    private String date;
    private long count;

    public DailyCountDto(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() { return date; }
    public long getCount() { return count; }
}
```

```java
package com.printplatform.dto;

public class PathCountDto {
    private String path;
    private long count;

    public PathCountDto(String path, long count) {
        this.path = path;
        this.count = count;
    }

    public String getPath() { return path; }
    public long getCount() { return count; }
}
```

```java
package com.printplatform.dto;

public class ApiStatsDto {
    private long totalRequests;
    private long errorCount;
    private double avgDurationMs;

    public ApiStatsDto(long totalRequests, long errorCount, double avgDurationMs) {
        this.totalRequests = totalRequests;
        this.errorCount = errorCount;
        this.avgDurationMs = avgDurationMs;
    }

    public long getTotalRequests() { return totalRequests; }
    public long getErrorCount() { return errorCount; }
    public double getAvgDurationMs() { return avgDurationMs; }
}
```

```java
package com.printplatform.dto;

import java.util.List;

public class TrafficSummaryDto {
    private List<DailyCountDto> pageViewsByDay;
    private List<PathCountDto> topPaths;
    private ApiStatsDto apiStats;

    public TrafficSummaryDto(List<DailyCountDto> pageViewsByDay, List<PathCountDto> topPaths, ApiStatsDto apiStats) {
        this.pageViewsByDay = pageViewsByDay;
        this.topPaths = topPaths;
        this.apiStats = apiStats;
    }

    public List<DailyCountDto> getPageViewsByDay() { return pageViewsByDay; }
    public List<PathCountDto> getTopPaths() { return topPaths; }
    public ApiStatsDto getApiStats() { return apiStats; }
}
```

- [ ] **Step 4: Add the dependency and method to `AnalyticsService`**

```java
package com.printplatform.service;

import com.printplatform.dto.ApiStatsDto;
import com.printplatform.dto.DailyCountDto;
import com.printplatform.dto.PathCountDto;
import com.printplatform.dto.TrafficSummaryDto;
import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.PageView;
import com.printplatform.repository.ApiRequestLogRepository;
import com.printplatform.repository.PageViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int TOP_PATHS_LIMIT = 10;

    private final PageViewRepository pageViewRepository;
    private final ApiRequestLogRepository apiRequestLogRepository;

    public AnalyticsService(PageViewRepository pageViewRepository, ApiRequestLogRepository apiRequestLogRepository) {
        this.pageViewRepository = pageViewRepository;
        this.apiRequestLogRepository = apiRequestLogRepository;
    }

    public void recordPageView(String path, UUID userId, String sessionId, String referrer) {
        PageView view = new PageView();
        view.setPath(path);
        view.setUserId(userId);
        view.setSessionId(sessionId);
        view.setReferrer(referrer);
        pageViewRepository.save(view);
    }

    /** Aggregates in Java rather than SQL date-grouping, so behavior is identical between H2 (tests) and Postgres (prod). */
    public TrafficSummaryDto getTrafficSummary(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<PageView> views = pageViewRepository.findByCreatedAtAfter(since);
        Map<String, Long> byDay = new TreeMap<>();
        for (PageView v : views) {
            byDay.merge(v.getCreatedAt().format(DAY_FORMAT), 1L, Long::sum);
        }
        List<DailyCountDto> pageViewsByDay = byDay.entrySet().stream()
                .map(e -> new DailyCountDto(e.getKey(), e.getValue()))
                .toList();

        Map<String, Long> byPath = views.stream()
                .collect(Collectors.groupingBy(PageView::getPath, Collectors.counting()));
        List<PathCountDto> topPaths = byPath.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_PATHS_LIMIT)
                .map(e -> new PathCountDto(e.getKey(), e.getValue()))
                .toList();

        List<ApiRequestLog> apiLogs = apiRequestLogRepository.findByCreatedAtAfter(since);
        long totalRequests = apiLogs.size();
        long errorCount = apiLogs.stream().filter(l -> l.getStatus() >= 400).count();
        double avgDurationMs = apiLogs.stream().mapToLong(ApiRequestLog::getDurationMs).average().orElse(0.0);

        return new TrafficSummaryDto(pageViewsByDay, topPaths, new ApiStatsDto(totalRequests, errorCount, avgDurationMs));
    }
}
```

- [ ] **Step 5: Add the endpoint to `AdminController`**

Add the dependency:

```java
    private final AdminService adminService;
    private final AnalyticsService analyticsService;

    public AdminController(AdminService adminService, AnalyticsService analyticsService) {
        this.adminService = adminService;
        this.analyticsService = analyticsService;
    }
```

Add the endpoint:

```java
    /** Traffic summary: page views by day, top paths, API error/latency stats (admin only). */
    @GetMapping("/traffic")
    public TrafficSummaryDto getTraffic(@RequestParam(defaultValue = "7") int days) {
        return analyticsService.getTrafficSummary(days);
    }
```

Add `import com.printplatform.dto.TrafficSummaryDto;` and `import com.printplatform.service.AnalyticsService;` to `AdminController.java`.

- [ ] **Step 6: Write the controller integration test**

Add to `AdminControllerTest.java`:

```java
@Test
void getTraffic_admin_returns200() throws Exception {
    User admin = persistUser(Role.ADMIN);

    mockMvc.perform(get("/api/admin/traffic")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageViewsByDay").isArray())
            .andExpect(jsonPath("$.topPaths").isArray())
            .andExpect(jsonPath("$.apiStats").exists());
}

@Test
void getTraffic_nonAdmin_returns403() throws Exception {
    User user = persistUser();

    mockMvc.perform(get("/api/admin/traffic")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -q -Dtest=AnalyticsServiceTest,AdminControllerTest test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/printplatform/dto/DailyCountDto.java \
        src/main/java/com/printplatform/dto/PathCountDto.java \
        src/main/java/com/printplatform/dto/ApiStatsDto.java \
        src/main/java/com/printplatform/dto/TrafficSummaryDto.java \
        src/main/java/com/printplatform/service/AnalyticsService.java \
        src/main/java/com/printplatform/controller/AdminController.java \
        src/test/java/com/printplatform/service/AnalyticsServiceTest.java \
        src/test/java/com/printplatform/controller/AdminControllerTest.java
git commit -m "feat: add admin traffic summary endpoint"
```

---

### Task 10: Revenue summary endpoint

**Files:**
- Create: `src/main/java/com/printplatform/dto/DailyRevenueDto.java`
- Create: `src/main/java/com/printplatform/dto/RevenueSummaryDto.java`
- Modify: `src/main/java/com/printplatform/repository/PaymentRepository.java`
- Modify: `src/main/java/com/printplatform/service/AdminService.java`
- Modify: `src/main/java/com/printplatform/controller/AdminController.java`
- Test: `src/test/java/com/printplatform/service/AdminServiceTest.java`
- Test: `src/test/java/com/printplatform/controller/AdminControllerTest.java`

**Interfaces:**
- Consumes: `Payment.getStatus/getPlatformFee/getTotalPrice/getCreatedAt` (existing entity).
- Produces: `AdminService.getRevenueSummary(int days)` returning `RevenueSummaryDto`.

- [ ] **Step 1: Write the failing test**

Add to `AdminServiceTest.java` (and add `@Mock private PaymentRepository paymentRepository;`, updating `setUp()` — see Step 3):

```java
private Payment payment(PaymentStatus status, BigDecimal fee, BigDecimal total, LocalDateTime createdAt) {
    Payment p = new Payment();
    p.setContractorPrice(BigDecimal.TEN);
    p.setPlatformFeePercent(BigDecimal.TEN);
    p.setPlatformFee(fee);
    p.setShippingPrice(BigDecimal.ZERO);
    p.setTotalPrice(total);
    p.setStatus(status);
    p.setCreatedAt(createdAt);
    return p;
}

@Test
void getRevenueSummary_sumsOnlyRealizedPayments() {
    LocalDateTime now = LocalDateTime.now();
    when(paymentRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
            payment(PaymentStatus.RELEASED, new BigDecimal("10.00"), new BigDecimal("100.00"), now),
            payment(PaymentStatus.HELD, new BigDecimal("5.00"), new BigDecimal("50.00"), now),
            payment(PaymentStatus.PENDING, new BigDecimal("999.00"), new BigDecimal("999.00"), now)
    ));

    RevenueSummaryDto result = adminService.getRevenueSummary(7);

    assertThat(result.getTotalPlatformFee()).isEqualByComparingTo("15.00");
    assertThat(result.getTotalVolume()).isEqualByComparingTo("150.00");
    assertThat(result.getPaidCount()).isEqualTo(2);
    assertThat(result.getPendingCount()).isEqualTo(1);
    assertThat(result.getByDay()).hasSize(1);
}
```

Add imports: `com.printplatform.model.Payment`, `com.printplatform.model.PaymentStatus`, `com.printplatform.dto.RevenueSummaryDto`, `com.printplatform.repository.PaymentRepository`, `java.math.BigDecimal`, `java.time.LocalDateTime`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminServiceTest test`
Expected: FAIL — `AdminService.getRevenueSummary` doesn't exist, constructor mismatch.

- [ ] **Step 3: Update `AdminServiceTest.setUp()`**

```java
    @BeforeEach
    void setUp() {
        adminService = new AdminService(codeRepository, userRepository, listingRepository, jwtService,
                adminAuditService, adminActionRepository, paymentRepository);
    }
```

- [ ] **Step 4: Write the DTOs**

```java
package com.printplatform.dto;

import java.math.BigDecimal;

public class DailyRevenueDto {
    private String date;
    private BigDecimal platformFee;
    private BigDecimal totalVolume;

    public DailyRevenueDto(String date, BigDecimal platformFee, BigDecimal totalVolume) {
        this.date = date;
        this.platformFee = platformFee;
        this.totalVolume = totalVolume;
    }

    public String getDate() { return date; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getTotalVolume() { return totalVolume; }
}
```

```java
package com.printplatform.dto;

import java.math.BigDecimal;
import java.util.List;

public class RevenueSummaryDto {
    private List<DailyRevenueDto> byDay;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalVolume;
    private long paidCount;
    private long pendingCount;

    public RevenueSummaryDto(List<DailyRevenueDto> byDay, BigDecimal totalPlatformFee, BigDecimal totalVolume,
                              long paidCount, long pendingCount) {
        this.byDay = byDay;
        this.totalPlatformFee = totalPlatformFee;
        this.totalVolume = totalVolume;
        this.paidCount = paidCount;
        this.pendingCount = pendingCount;
    }

    public List<DailyRevenueDto> getByDay() { return byDay; }
    public BigDecimal getTotalPlatformFee() { return totalPlatformFee; }
    public BigDecimal getTotalVolume() { return totalVolume; }
    public long getPaidCount() { return paidCount; }
    public long getPendingCount() { return pendingCount; }
}
```

- [ ] **Step 5: Add the repository query**

In `src/main/java/com/printplatform/repository/PaymentRepository.java`:

```java
package com.printplatform.repository;

import com.printplatform.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOfferId(UUID offerId);
    List<Payment> findByCreatedAtAfter(LocalDateTime since);
}
```

- [ ] **Step 6: Add the dependency and method to `AdminService`**

Add the constructor parameter (now seven args):

```java
    private final AdminCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final JwtService jwtService;
    private final AdminAuditService adminAuditService;
    private final AdminActionRepository adminActionRepository;
    private final PaymentRepository paymentRepository;

    public AdminService(AdminCodeRepository codeRepository,
                        UserRepository userRepository,
                        ListingRepository listingRepository,
                        JwtService jwtService,
                        AdminAuditService adminAuditService,
                        AdminActionRepository adminActionRepository,
                        PaymentRepository paymentRepository) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.jwtService = jwtService;
        this.adminAuditService = adminAuditService;
        this.adminActionRepository = adminActionRepository;
        this.paymentRepository = paymentRepository;
    }
```

Add imports: `com.printplatform.model.Payment`, `com.printplatform.model.PaymentStatus`, `com.printplatform.dto.DailyRevenueDto`, `com.printplatform.dto.RevenueSummaryDto`, `com.printplatform.repository.PaymentRepository`, `java.math.BigDecimal`, `java.time.format.DateTimeFormatter`, `java.util.function.Function`, `java.util.TreeMap`.

Add the constant and method:

```java
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Revenue summary aggregated in Java (not SQL date-grouping) over "realized" payments — HELD or RELEASED,
     *  i.e. money actually captured; PENDING and REFUNDED are excluded from the sums but PENDING is still counted. */
    public RevenueSummaryDto getRevenueSummary(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Payment> payments = paymentRepository.findByCreatedAtAfter(since);

        Map<String, List<Payment>> byDayRaw = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(DAY_FORMAT), TreeMap::new, Collectors.toList()));

        List<DailyRevenueDto> byDay = byDayRaw.entrySet().stream()
                .map(e -> new DailyRevenueDto(
                        e.getKey(),
                        sumRealized(e.getValue(), Payment::getPlatformFee),
                        sumRealized(e.getValue(), Payment::getTotalPrice)))
                .toList();

        BigDecimal totalFee = sumRealized(payments, Payment::getPlatformFee);
        BigDecimal totalVolume = sumRealized(payments, Payment::getTotalPrice);
        long paidCount = payments.stream().filter(this::isRealized).count();
        long pendingCount = payments.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).count();

        return new RevenueSummaryDto(byDay, totalFee, totalVolume, paidCount, pendingCount);
    }

    private boolean isRealized(Payment p) {
        return p.getStatus() == PaymentStatus.HELD || p.getStatus() == PaymentStatus.RELEASED;
    }

    private BigDecimal sumRealized(List<Payment> payments, Function<Payment, BigDecimal> field) {
        return payments.stream()
                .filter(this::isRealized)
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
```

Add `import java.util.Map;` and `import java.util.stream.Collectors;` if not already present (Collectors may already be imported elsewhere in the file — check before adding a duplicate).

- [ ] **Step 7: Add the endpoint to `AdminController`**

```java
    /** Revenue summary from realized payments (admin only). */
    @GetMapping("/revenue")
    public RevenueSummaryDto getRevenue(@RequestParam(defaultValue = "7") int days) {
        return adminService.getRevenueSummary(days);
    }
```

Add `import com.printplatform.dto.RevenueSummaryDto;` to `AdminController.java`.

- [ ] **Step 8: Write the controller integration test**

Add to `AdminControllerTest.java`:

```java
@Test
void getRevenue_admin_returns200() throws Exception {
    User admin = persistUser(Role.ADMIN);

    mockMvc.perform(get("/api/admin/revenue")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byDay").isArray())
            .andExpect(jsonPath("$.totalPlatformFee").exists());
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn -q -Dtest=AdminServiceTest,AdminControllerTest test`
Expected: PASS

- [ ] **Step 10: Run the full backend suite**

Run: `mvn -q test`
Expected: PASS (confirms all seven `AdminService` constructor call sites across the codebase are consistent)

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/printplatform/dto/DailyRevenueDto.java \
        src/main/java/com/printplatform/dto/RevenueSummaryDto.java \
        src/main/java/com/printplatform/repository/PaymentRepository.java \
        src/main/java/com/printplatform/service/AdminService.java \
        src/main/java/com/printplatform/controller/AdminController.java \
        src/test/java/com/printplatform/service/AdminServiceTest.java \
        src/test/java/com/printplatform/controller/AdminControllerTest.java
git commit -m "feat: add admin revenue summary endpoint"
```

---

### Task 11: Frontend — page-view tracking wired into navigation

**Files:**
- Create: `frontend/src/app/services/analytics.service.ts`
- Create: `frontend/src/app/services/analytics.service.spec.ts`
- Modify: `frontend/src/app/app.component.ts`

**Interfaces:**
- Produces: `AnalyticsService.trackPageView(path: string): void` — used by `AppComponent`.

- [ ] **Step 1: Write the failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('trackPageView() POSTs the path with a generated session id', () => {
    service.trackPageView('/zlecenia');
    const req = httpMock.expectOne('/api/analytics/pageview');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.path).toBe('/zlecenia');
    expect(req.request.body.sessionId).toBeTruthy();
    req.flush(null);
  });

  it('trackPageView() reuses the same session id across calls', () => {
    service.trackPageView('/a');
    const first = httpMock.expectOne('/api/analytics/pageview');
    const firstSessionId = first.request.body.sessionId;
    first.flush(null);

    service.trackPageView('/b');
    const second = httpMock.expectOne('/api/analytics/pageview');
    expect(second.request.body.sessionId).toBe(firstSessionId);
    second.flush(null);
  });

  it('trackPageView() swallows request errors silently', () => {
    expect(() => {
      service.trackPageView('/broken');
      const req = httpMock.expectOne('/api/analytics/pageview');
      req.flush('error', { status: 500, statusText: 'Server Error' });
    }).not.toThrow();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- analytics.service.spec.ts`
Expected: FAIL — `./analytics.service` doesn't exist.

- [ ] **Step 3: Write the service**

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly SESSION_KEY = 'druk3d-session-id';
  private http = inject(HttpClient);

  trackPageView(path: string): void {
    this.http.post('/api/analytics/pageview', {
      path,
      sessionId: this.sessionId(),
      referrer: document.referrer || null,
    }).subscribe({ next: () => {}, error: () => {} });
  }

  private sessionId(): string {
    let id = sessionStorage.getItem(this.SESSION_KEY);
    if (!id) {
      id = crypto.randomUUID();
      sessionStorage.setItem(this.SESSION_KEY, id);
    }
    return id;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- analytics.service.spec.ts`
Expected: PASS

- [ ] **Step 5: Wire it into `AppComponent`'s existing router-event subscription**

In `frontend/src/app/app.component.ts`, add the import and injection:

```typescript
import { AnalyticsService } from './services/analytics.service';
// ...
  private analytics = inject(AnalyticsService);
```

And extend the existing `NavigationEnd` subscription:

```typescript
    this.routerSub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        let route = this.router.routerState.snapshot.root;
        while (route.firstChild) {
          route = route.firstChild;
        }
        this.isFullscreenRoute.set(!!route.data['fullscreen']);
        this.analytics.trackPageView(this.router.url);
      });
```

- [ ] **Step 6: Run the frontend suite to confirm nothing broke**

Run: `npm --prefix frontend test`
Expected: PASS (existing `app.component.spec.ts`, if it stubs `HttpClient`/`AnalyticsService` calls via `provideHttpClientTesting`, continues to pass; if it doesn't yet provide HTTP testing support, add `provideHttpClient(), provideHttpClientTesting()` to its `TestBed.configureTestingModule` providers array)

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/services/analytics.service.ts \
        frontend/src/app/services/analytics.service.spec.ts \
        frontend/src/app/app.component.ts
git commit -m "feat: track page views on navigation"
```

---

### Task 12: Frontend — Users tab suspend/unsuspend

**Files:**
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.ts`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.html`
- Test: `frontend/src/app/features/admin-panel/admin-panel.component.spec.ts` (create if it doesn't already exist — check first)

**Interfaces:**
- Consumes: `PUT /api/admin/users/{id}/suspend`, `PUT /api/admin/users/{id}/unsuspend` (Task 4).

- [ ] **Step 1: Check for an existing spec file and its `TestBed` setup**

Run: `Get-ChildItem frontend/src/app/features/admin-panel/admin-panel.component.spec.ts` (or `ls` on macOS/Linux)

If it exists, read it first and match its existing `TestBed` provider setup (likely `provideHttpClient()`, `provideHttpClientTesting()`, and a mock/stub for `AuthService`) before adding new tests. If it doesn't exist, create it following the `provideHttpClient`/`provideHttpClientTesting` pattern shown in `offer.service.spec.ts`, instantiating `AdminPanelComponent` via `TestBed.createComponent`.

- [ ] **Step 2: Write the failing test**

```typescript
it('suspendUser() calls the suspend endpoint and updates the row', () => {
  const fixture = TestBed.createComponent(AdminPanelComponent);
  const component = fixture.componentInstance;
  component.users.set([
    { id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: false }
  ]);

  component.suspendUser('u1');
  const req = httpMock.expectOne('/api/admin/users/u1/suspend');
  expect(req.request.method).toBe('PUT');
  req.flush({ id: 'u1', email: 'a@test.local', role: 'USER', firstName: null, lastName: null, createdAt: '2026-01-01', suspended: true });

  expect(component.users()[0].suspended).toBe(true);
});
```

(Adjust the harness boilerplate — imports, `beforeEach`/`afterEach`, `httpMock` setup — to match whatever convention Step 1 revealed or established.)

- [ ] **Step 3: Run test to verify it fails**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: FAIL — `suspended` isn't in the `UserSummary` interface, `suspendUser()` doesn't exist.

- [ ] **Step 4: Update the component**

In `admin-panel.component.ts`, add `suspended` to the `UserSummary` interface:

```typescript
interface UserSummary {
  id: string;
  email: string;
  role: string;
  firstName: string | null;
  lastName: string | null;
  createdAt: string;
  suspended: boolean;
}
```

Add a signal for the in-flight suspend action and the two methods:

```typescript
  suspendingUserId = signal<string | null>(null);

  suspendUser(id: string): void {
    this.suspendingUserId.set(id);
    this.http.put<UserSummary>(`/api/admin/users/${id}/suspend`, {}).subscribe({
      next: updated => {
        this.users.update(list => list.map(u => u.id === id ? updated : u));
        this.suspendingUserId.set(null);
      },
      error: () => this.suspendingUserId.set(null)
    });
  }

  unsuspendUser(id: string): void {
    this.suspendingUserId.set(id);
    this.http.put<UserSummary>(`/api/admin/users/${id}/unsuspend`, {}).subscribe({
      next: updated => {
        this.users.update(list => list.map(u => u.id === id ? updated : u));
        this.suspendingUserId.set(null);
      },
      error: () => this.suspendingUserId.set(null)
    });
  }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: PASS

- [ ] **Step 6: Add the button to the Users table**

In `admin-panel.component.html`, add a header cell and an action cell to the users table:

```html
                <tr>
                  <th scope="col">Email</th>
                  <th scope="col">Imię i nazwisko</th>
                  <th scope="col">Rola</th>
                  <th scope="col">Data rejestracji</th>
                  <th scope="col"><span class="sr-only">Akcje</span></th>
                </tr>
```

```html
                @for (u of users(); track u.id) {
                  <tr>
                    <td>{{ u.email }}</td>
                    <td>{{ userDisplayName(u) }}</td>
                    <td>
                      <span class="role-pill" [class.role-pill--admin]="u.role === 'ADMIN'">
                        {{ u.role === 'ADMIN' ? '🔑 Admin' : '👤 User' }}
                      </span>
                      @if (u.suspended) {
                        <span class="status-pill status-pill--closed">Zawieszony</span>
                      }
                    </td>
                    <td class="date-cell">{{ formatDate(u.createdAt) }}</td>
                    <td class="action-cell">
                      @if (u.suspended) {
                        <button class="btn btn--ghost btn--sm" (click)="unsuspendUser(u.id)" [disabled]="suspendingUserId() === u.id">
                          Przywróć
                        </button>
                      } @else {
                        <button class="btn btn--danger btn--sm" (click)="suspendUser(u.id)" [disabled]="suspendingUserId() === u.id">
                          Zawieś
                        </button>
                      }
                    </td>
                  </tr>
                }
```

(Reuses the existing `.status-pill--closed` and `.btn--danger`/`.btn--ghost` classes already defined in `admin-panel.component.css` — no new CSS needed.)

- [ ] **Step 7: Run the frontend suite**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/features/admin-panel/admin-panel.component.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.html \
        frontend/src/app/features/admin-panel/admin-panel.component.spec.ts
git commit -m "feat: add user suspend/unsuspend to admin panel"
```

---

### Task 13: Frontend — Listings tab hide/unhide

**Files:**
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.ts`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.html`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.spec.ts`

**Interfaces:**
- Consumes: `PUT /api/admin/listings/{id}/hide`, `PUT /api/admin/listings/{id}/unhide` (Task 4).

- [ ] **Step 1: Write the failing test**

```typescript
it('hideListing() calls the hide endpoint and updates the row', () => {
  const fixture = TestBed.createComponent(AdminPanelComponent);
  const component = fixture.componentInstance;
  component.listings.set([
    { id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'VISIBLE' }
  ]);

  component.hideListing('l1');
  const req = httpMock.expectOne('/api/admin/listings/l1/hide');
  expect(req.request.method).toBe('PUT');
  req.flush({ id: 'l1', title: 'Test', status: 'OPEN', createdAt: '2026-01-01', ownerEmail: 'a@test.local', ownerFirstName: null, ownerLastName: null, maxBudget: null, moderationStatus: 'HIDDEN' });

  expect(component.listings()[0].moderationStatus).toBe('HIDDEN');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: FAIL — `moderationStatus` isn't in the `AdminListing` interface, `hideListing()` doesn't exist.

- [ ] **Step 3: Update the component**

Add `moderationStatus` to the `AdminListing` interface:

```typescript
interface AdminListing {
  id: string;
  title: string;
  status: string;
  createdAt: string;
  ownerEmail: string;
  ownerFirstName: string | null;
  ownerLastName: string | null;
  maxBudget: number | null;
  moderationStatus: string;
}
```

Add the signal and methods:

```typescript
  moderatingListingId = signal<string | null>(null);

  hideListing(id: string): void {
    this.moderatingListingId.set(id);
    this.http.put<AdminListing>(`/api/admin/listings/${id}/hide`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? updated : l));
        this.moderatingListingId.set(null);
      },
      error: () => this.moderatingListingId.set(null)
    });
  }

  unhideListing(id: string): void {
    this.moderatingListingId.set(id);
    this.http.put<AdminListing>(`/api/admin/listings/${id}/unhide`, {}).subscribe({
      next: updated => {
        this.listings.update(list => list.map(l => l.id === id ? updated : l));
        this.moderatingListingId.set(null);
      },
      error: () => this.moderatingListingId.set(null)
    });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: PASS

- [ ] **Step 5: Add the badge and button to the Listings table**

In `admin-panel.component.html`, add a "Widoczność" header and badge/button, and widen the action cell:

```html
                <tr>
                  <th scope="col">Tytuł</th>
                  <th scope="col">Właściciel</th>
                  <th scope="col">Status</th>
                  <th scope="col">Widoczność</th>
                  <th scope="col">Data</th>
                  <th scope="col"><span class="sr-only">Akcje</span></th>
                </tr>
```

```html
                @for (l of listings(); track l.id) {
                  <tr>
                    <td class="listing-title">{{ l.title }}</td>
                    <td class="listing-owner">{{ l.ownerEmail }}</td>
                    <td>
                      <span class="status-pill" [class.status-pill--closed]="l.status === 'CLOSED'" [class.status-pill--progress]="l.status === 'IN_PROGRESS'">
                        {{ statusLabel(l.status) }}
                      </span>
                    </td>
                    <td>
                      @if (l.moderationStatus === 'HIDDEN') {
                        <span class="status-pill status-pill--closed">Ukryte</span>
                      } @else {
                        <span class="status-pill">Widoczne</span>
                      }
                    </td>
                    <td class="date-cell">{{ formatDate(l.createdAt) }}</td>
                    <td class="action-cell">
                      @if (confirmDeleteId() === l.id) {
                        <span class="confirm-text">Usunąć?</span>
                        <button class="btn btn--danger btn--sm" (click)="deleteListing(l.id)" [disabled]="deleting()">
                          @if (deleting()) { … } @else { Tak }
                        </button>
                        <button class="btn btn--ghost btn--sm" (click)="cancelDelete()" [disabled]="deleting()">Nie</button>
                      } @else {
                        @if (l.moderationStatus === 'HIDDEN') {
                          <button class="btn btn--ghost btn--sm" (click)="unhideListing(l.id)" [disabled]="moderatingListingId() === l.id">Pokaż</button>
                        } @else {
                          <button class="btn btn--ghost btn--sm" (click)="hideListing(l.id)" [disabled]="moderatingListingId() === l.id">Ukryj</button>
                        }
                        <button class="btn btn--danger btn--sm" (click)="deleteListing(l.id)">Usuń</button>
                      }
                    </td>
                  </tr>
                }
```

- [ ] **Step 6: Run the frontend suite**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/admin-panel/admin-panel.component.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.html \
        frontend/src/app/features/admin-panel/admin-panel.component.spec.ts
git commit -m "feat: add listing hide/unhide to admin panel"
```

---

### Task 14: Frontend — Traffic card

**Files:**
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.ts`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.html`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.spec.ts`

**Interfaces:**
- Consumes: `GET /api/admin/traffic?days=` (Task 9).

- [ ] **Step 1: Write the failing test**

```typescript
it('loads traffic summary on init and exposes it as a signal', () => {
  const fixture = TestBed.createComponent(AdminPanelComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/users/me').flush({ /* ...minimal profile fields used elsewhere in this spec... */ });
  httpMock.expectOne('/api/admin/listings').flush([]);
  httpMock.expectOne('/api/admin/users').flush([]);
  httpMock.expectOne(req => req.url === '/api/admin/codes').flush([]);

  const trafficReq = httpMock.expectOne(req => req.url === '/api/admin/traffic');
  trafficReq.flush({
    pageViewsByDay: [{ date: '2026-07-14', count: 5 }],
    topPaths: [{ path: '/', count: 3 }],
    apiStats: { totalRequests: 10, errorCount: 1, avgDurationMs: 42.5 }
  });

  expect(fixture.componentInstance.traffic()?.apiStats.totalRequests).toBe(10);
});
```

Adjust the mocked `/api/users/me` payload and any other pre-existing `ngOnInit` HTTP expectations to match whatever the existing spec file already sets up (this test only adds the new `/api/admin/traffic` expectation and assertion — don't duplicate boilerplate already handled by an existing `beforeEach`).

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: FAIL — no `/api/admin/traffic` request is made yet, `component.traffic` doesn't exist.

- [ ] **Step 3: Add the interfaces, signal, and load call**

```typescript
interface DailyCount { date: string; count: number; }
interface PathCount { path: string; count: number; }
interface ApiStats { totalRequests: number; errorCount: number; avgDurationMs: number; }
interface TrafficSummary {
  pageViewsByDay: DailyCount[];
  topPaths: PathCount[];
  apiStats: ApiStats;
}
```

```typescript
  traffic        = signal<TrafficSummary | null>(null);
  trafficLoading = signal(false);
```

```typescript
  ngOnInit(): void {
    this.loadProfile();
    this.loadListings();
    this.loadUsers();
    this.loadCodes();
    this.loadTraffic();
  }
```

```typescript
  private loadTraffic(): void {
    this.trafficLoading.set(true);
    this.http.get<TrafficSummary>('/api/admin/traffic').subscribe({
      next: t => { this.traffic.set(t); this.trafficLoading.set(false); },
      error: () => this.trafficLoading.set(false)
    });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: PASS

- [ ] **Step 5: Add the Traffic card to the template**

In `admin-panel.component.html`, add a new section after the Listings card:

```html
      <!-- ── Ruch na stronie ── -->
      <section class="card" aria-label="Ruch na stronie">
        <h2 class="card__title">📈 Ruch na stronie (7 dni)</h2>
        @if (trafficLoading()) {
          <p class="muted">Ładowanie...</p>
        } @else if (!traffic()) {
          <p class="muted">Brak danych.</p>
        } @else {
          <dl class="info-list">
            <div class="info-row"><dt>Odsłony łącznie</dt><dd>{{ totalPageViews() }}</dd></div>
            <div class="info-row"><dt>Zapytania API</dt><dd>{{ traffic()!.apiStats.totalRequests }}</dd></div>
            <div class="info-row"><dt>Błędy API</dt><dd>{{ traffic()!.apiStats.errorCount }}</dd></div>
            <div class="info-row"><dt>Śr. czas odpowiedzi</dt><dd>{{ traffic()!.apiStats.avgDurationMs | number:'1.0-0' }} ms</dd></div>
          </dl>
          <h3 class="card__subtitle">Najczęściej odwiedzane strony</h3>
          <ul class="code-list">
            @for (p of traffic()!.topPaths; track p.path) {
              <li class="code-row">
                <code class="code-row__code">{{ p.path }}</code>
                <span class="code-row__status">{{ p.count }} odsłon</span>
              </li>
            }
          </ul>
        }
      </section>
```

Add `DecimalPipe` to the component's `imports` array (for the `| number` pipe) and a `totalPageViews()` helper:

```typescript
import { DecimalPipe } from '@angular/common';
// ...
@Component({
  selector: 'app-admin-panel',
  imports: [FormsModule, DecimalPipe],
  // ...
})
```

```typescript
  totalPageViews(): number {
    return this.traffic()?.pageViewsByDay.reduce((sum, d) => sum + d.count, 0) ?? 0;
  }
```

Add a `.card__subtitle` rule to `admin-panel.component.css` only if no equivalent class already exists there — check the file first; if `.card__title` styling already covers a smaller heading variant, reuse it instead of adding a new class.

- [ ] **Step 6: Run the frontend suite**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/admin-panel/admin-panel.component.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.html \
        frontend/src/app/features/admin-panel/admin-panel.component.spec.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.css
git commit -m "feat: add traffic summary card to admin panel"
```

---

### Task 15: Frontend — Revenue card

**Files:**
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.ts`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.html`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.spec.ts`

**Interfaces:**
- Consumes: `GET /api/admin/revenue?days=` (Task 10).

- [ ] **Step 1: Write the failing test**

```typescript
it('loads revenue summary on init', () => {
  const fixture = TestBed.createComponent(AdminPanelComponent);
  fixture.detectChanges();

  // ...existing ngOnInit request flushes (profile/listings/users/codes/traffic)...

  const revenueReq = httpMock.expectOne(req => req.url === '/api/admin/revenue');
  revenueReq.flush({
    byDay: [{ date: '2026-07-14', platformFee: 15, totalVolume: 150 }],
    totalPlatformFee: 15,
    totalVolume: 150,
    paidCount: 2,
    pendingCount: 1
  });

  expect(fixture.componentInstance.revenue()?.totalPlatformFee).toBe(15);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: FAIL — no `/api/admin/revenue` request made, `component.revenue` doesn't exist.

- [ ] **Step 3: Add the interfaces, signal, and load call**

```typescript
interface DailyRevenue { date: string; platformFee: number; totalVolume: number; }
interface RevenueSummary {
  byDay: DailyRevenue[];
  totalPlatformFee: number;
  totalVolume: number;
  paidCount: number;
  pendingCount: number;
}
```

```typescript
  revenue        = signal<RevenueSummary | null>(null);
  revenueLoading = signal(false);
```

```typescript
  ngOnInit(): void {
    this.loadProfile();
    this.loadListings();
    this.loadUsers();
    this.loadCodes();
    this.loadTraffic();
    this.loadRevenue();
  }
```

```typescript
  private loadRevenue(): void {
    this.revenueLoading.set(true);
    this.http.get<RevenueSummary>('/api/admin/revenue').subscribe({
      next: r => { this.revenue.set(r); this.revenueLoading.set(false); },
      error: () => this.revenueLoading.set(false)
    });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: PASS

- [ ] **Step 5: Add the Revenue card to the template**

```html
      <!-- ── Przychód ── -->
      <section class="card" aria-label="Przychód">
        <h2 class="card__title">💰 Przychód (7 dni)</h2>
        @if (revenueLoading()) {
          <p class="muted">Ładowanie...</p>
        } @else if (!revenue()) {
          <p class="muted">Brak danych.</p>
        } @else {
          <dl class="info-list">
            <div class="info-row"><dt>Prowizja platformy</dt><dd>{{ revenue()!.totalPlatformFee | number:'1.2-2' }} zł</dd></div>
            <div class="info-row"><dt>Obrót łącznie</dt><dd>{{ revenue()!.totalVolume | number:'1.2-2' }} zł</dd></div>
            <div class="info-row"><dt>Zrealizowane płatności</dt><dd>{{ revenue()!.paidCount }}</dd></div>
            <div class="info-row"><dt>Oczekujące płatności</dt><dd>{{ revenue()!.pendingCount }}</dd></div>
          </dl>
        }
      </section>
```

(`DecimalPipe` is already in `imports` from Task 14.)

- [ ] **Step 6: Run the frontend suite**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/admin-panel/admin-panel.component.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.html \
        frontend/src/app/features/admin-panel/admin-panel.component.spec.ts
git commit -m "feat: add revenue summary card to admin panel"
```

---

### Task 16: Frontend — Audit Log card

**Files:**
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.ts`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.html`
- Modify: `frontend/src/app/features/admin-panel/admin-panel.component.spec.ts`

**Interfaces:**
- Consumes: `GET /api/admin/audit-log?page=&size=` (Task 6).

- [ ] **Step 1: Write the failing test**

```typescript
it('loads the audit log on init', () => {
  const fixture = TestBed.createComponent(AdminPanelComponent);
  fixture.detectChanges();

  // ...existing ngOnInit request flushes (profile/listings/users/codes/traffic/revenue)...

  const auditReq = httpMock.expectOne(req => req.url === '/api/admin/audit-log');
  auditReq.flush({
    content: [{ id: 'a1', adminEmail: 'admin@test.local', actionType: 'HIDE_LISTING', targetType: 'Listing', targetId: 'l1', details: null, createdAt: '2026-07-14T10:00:00' }],
    page: 0, size: 20, totalElements: 1, totalPages: 1, last: true
  });

  expect(fixture.componentInstance.auditLog().length).toBe(1);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: FAIL — no `/api/admin/audit-log` request made, `component.auditLog` doesn't exist.

- [ ] **Step 3: Add the interface, signal, and load call**

```typescript
interface AuditLogEntry {
  id: string;
  adminEmail: string;
  actionType: string;
  targetType: string;
  targetId: string;
  details: string | null;
  createdAt: string;
}
```

```typescript
  auditLog        = signal<AuditLogEntry[]>([]);
  auditLogLoading  = signal(false);
```

```typescript
  ngOnInit(): void {
    this.loadProfile();
    this.loadListings();
    this.loadUsers();
    this.loadCodes();
    this.loadTraffic();
    this.loadRevenue();
    this.loadAuditLog();
  }
```

```typescript
  private loadAuditLog(): void {
    this.auditLogLoading.set(true);
    this.http.get<{ content: AuditLogEntry[] }>('/api/admin/audit-log').subscribe({
      next: page => { this.auditLog.set(page.content); this.auditLogLoading.set(false); },
      error: () => this.auditLogLoading.set(false)
    });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend test -- admin-panel.component.spec.ts`
Expected: PASS

- [ ] **Step 5: Add an `actionLabel` helper and the Audit Log card to the template**

```typescript
  actionLabel(type: string): string {
    const map: Record<string, string> = {
      DELETE_LISTING: 'Usunięto ogłoszenie',
      BAN_USER: 'Zawieszono użytkownika',
      UNBAN_USER: 'Przywrócono użytkownika',
      HIDE_LISTING: 'Ukryto ogłoszenie',
      UNHIDE_LISTING: 'Przywrócono ogłoszenie',
    };
    return map[type] ?? type;
  }
```

```html
      <!-- ── Dziennik działań administratora ── -->
      <section class="card" aria-label="Dziennik działań administratora">
        <h2 class="card__title">🗒️ Dziennik działań ({{ auditLog().length }})</h2>
        @if (auditLogLoading()) {
          <p class="muted">Ładowanie...</p>
        } @else if (auditLog().length === 0) {
          <p class="muted">Brak zarejestrowanych działań.</p>
        } @else {
          <div class="table-wrap" role="region" aria-label="Tabela dziennika działań" tabindex="0">
            <table class="users-table">
              <thead>
                <tr>
                  <th scope="col">Administrator</th>
                  <th scope="col">Działanie</th>
                  <th scope="col">Cel</th>
                  <th scope="col">Data</th>
                </tr>
              </thead>
              <tbody>
                @for (a of auditLog(); track a.id) {
                  <tr>
                    <td>{{ a.adminEmail }}</td>
                    <td>{{ actionLabel(a.actionType) }}</td>
                    <td class="monospace">{{ a.targetType }} · {{ a.targetId }}</td>
                    <td class="date-cell">{{ formatDate(a.createdAt) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
```

- [ ] **Step 6: Run the frontend suite**

Run: `npm --prefix frontend test`
Expected: PASS

- [ ] **Step 7: Run the backend suite once more end-to-end**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/features/admin-panel/admin-panel.component.ts \
        frontend/src/app/features/admin-panel/admin-panel.component.html \
        frontend/src/app/features/admin-panel/admin-panel.component.spec.ts
git commit -m "feat: add admin action audit log card to admin panel"
```

---

## After all tasks

Run the full stack once end-to-end (`mvn test` + `npm --prefix frontend test`), then use `superpowers:finishing-a-development-branch` to decide how to integrate (PR, merge, etc.) — this plan doesn't cover that step.
