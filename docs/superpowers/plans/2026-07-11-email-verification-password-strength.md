# Email Verification & Password Strength Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** New registrations must confirm their email before they can log in, and new passwords must meet a minimum strength bar.

**Architecture:** Mirrors the existing password-reset flow (`PasswordResetToken` / `PasswordResetService` / `EmailService`) with a parallel `EmailVerificationToken` / `EmailVerificationService` pair. `AuthService.login()` gates on a new `User.emailVerified` flag; `AuthService.register()` no longer auto-logs-in (no JWT returned) and instead issues a verification email. Google/Facebook OAuth signups are marked verified at creation time since both providers already guarantee a confirmed email at the point Auth verifies their token.

**Tech Stack:** Spring Boot 3.2.5 / Java 21 / JPA-Hibernate (`ddl-auto=update`, no Flyway/Liquibase) / PostgreSQL (H2 in tests) backend; Angular 21 standalone components + Vitest frontend.

## Global Constraints

- Password rule (registration + password-reset): minimum 8 characters, must contain at least one uppercase letter, one lowercase letter, and one digit. No special-character requirement. Login itself is NOT re-validated against this rule (existing 6-char passwords must keep working).
- Verification token: UUID v4, single-use, expires 24 hours after issuance, old unused tokens for a user are invalidated when a new one is issued.
- `resend-verification` always returns 200 regardless of whether the email exists or is already verified (anti-enumeration, matches `forgot-password`'s existing pattern).
- Google/Facebook OAuth-created accounts get `emailVerified = true` at creation — no verification email sent for them.
- Existing (pre-feature) users must be grandfathered to `emailVerified = true` via a one-time manual SQL backfill run at deploy time — NOT via application code, since Hibernate `ddl-auto=update` will add the column with a database-level default of `false` for every existing row.
- Reference spec: `docs/superpowers/specs/2026-07-11-email-verification-password-strength-design.md`.

---

### Task 1: Make the auth rate limiter's request cap configurable

**Why this is first:** `AuthControllerTest` is a `@SpringBootTest` whose `AuthRateLimitFilter` bean is a shared, stateful singleton across every test method in the class (fixed in-memory per-IP window, hardcoded at 10 requests/minute — see the class-level comment in `AuthControllerTest.java`). Task 8 below adds 5 new test methods to that class, each making one `/api/auth/**` call, on top of the 8 that already exist. Left hardcoded at 10, the suite would start intermittently failing on test order/timing. Making the cap configurable — with the Java field defaulting to the same value the `@Value` annotation defaults to — fixes this with zero behavior change in production and zero changes to the existing `AuthRateLimitFilterTest` (which constructs the filter directly with `new AuthRateLimitFilter()`, bypassing Spring, so it must keep working off the field's plain Java default).

**Files:**
- Modify: `src/main/java/com/printplatform/security/AuthRateLimitFilter.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`

**Interfaces:**
- Produces: `AuthRateLimitFilter` still has the exact same public behavior (`shouldNotFilter`, `doFilterInternal`), just with the cap now configurable. No other task depends on new symbols here.

- [ ] **Step 1: Run the existing rate-limit filter tests to confirm today's baseline passes**

Run: `mvn test -Dtest=AuthRateLimitFilterTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 2: Make the cap configurable, defaulting to the current value of 10**

In `src/main/java/com/printplatform/security/AuthRateLimitFilter.java`, replace the hardcoded constant and thread the cap through to `Window`:

```java
package com.printplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throttles requests to /api/auth/** per client IP (fixed window) to blunt
 * credential-stuffing and brute-force attempts against login/register.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    // Every this many requests, opportunistically evict windows that have been idle
    // for a full cycle, so the map doesn't grow unbounded under sustained unique-IP traffic.
    private static final long SWEEP_EVERY_N_REQUESTS = 500;

    // Java default doubles as the fallback for tests that construct this filter directly
    // (new AuthRateLimitFilter()), bypassing Spring's @Value injection entirely.
    @Value("${app.rate-limit.auth.max-requests-per-window:10}")
    private int maxRequestsPerWindow = 10;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Window window = windows.computeIfAbsent(clientIp(request), k -> new Window());

        if (window.tooManyRequests(maxRequestsPerWindow)) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Zbyt wiele prób logowania. Spróbuj ponownie za chwilę.\"}");
            return;
        }

        if (requestCount.incrementAndGet() % SWEEP_EVERY_N_REQUESTS == 0) {
            evictStaleWindows();
        }

        chain.doFilter(request, response);
    }

    /**
     * Best-effort per-client key. When the app sits behind the frontend/reverse proxy,
     * getRemoteAddr() is the proxy's IP (one shared bucket for everyone), so prefer the
     * first hop of X-Forwarded-For. Falls back to the socket address when absent.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].strip();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private void evictStaleWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(now));
    }

    /** Resets its counter once WINDOW_MILLIS has elapsed since the first request in the window. */
    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean isStale(long now) {
            return now - windowStart > WINDOW_MILLIS;
        }

        synchronized boolean tooManyRequests(int maxRequestsPerWindow) {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            return ++count > maxRequestsPerWindow;
        }
    }
}
```

- [ ] **Step 3: Add the property to both properties files**

In `src/main/resources/application.properties`, add near the CORS block:

```properties
# Auth endpoint throttling — requests per client IP per 60s window.
app.rate-limit.auth.max-requests-per-window=${AUTH_RATE_LIMIT_MAX:10}
```

In `src/test/resources/application.properties`, add:

```properties
# Headroom for the growing AuthControllerTest suite — this bean is a singleton shared
# across every test method in that @SpringBootTest class.
app.rate-limit.auth.max-requests-per-window=100
```

- [ ] **Step 4: Re-run the rate-limit filter tests — must still pass unchanged**

Run: `mvn test -Dtest=AuthRateLimitFilterTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0` (unchanged — proves the Java-side default of `10` still governs direct `new AuthRateLimitFilter()` construction)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/printplatform/security/AuthRateLimitFilter.java src/main/resources/application.properties src/test/resources/application.properties
git commit -m "feat: make auth rate limiter's request cap configurable"
```

---

### Task 2: Add `emailVerified` to `User`, verify the seeded admin account

**Files:**
- Modify: `src/main/java/com/printplatform/model/User.java`
- Modify: `src/main/java/com/printplatform/config/DataInitializer.java`

**Interfaces:**
- Produces: `User.isEmailVerified(): boolean`, `User.setEmailVerified(boolean): void`. New `User` instances default to `false` (Java field default) unless explicitly set — Task 7 relies on this default for `register()`, and explicitly sets `true` for OAuth account creation.

- [ ] **Step 1: Run the full existing test suite to confirm today's baseline is green**

Run: `mvn test`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: Add the field to `User`**

In `src/main/java/com/printplatform/model/User.java`, add the import and field, plus getter/setter. Insert the field right after `googleId` (near the other account-state fields), and the accessor after `getGoogleId`/`setGoogleId`:

```java
import org.hibernate.annotations.ColumnDefault;
```

```java
    @Column(unique = true)
    private String googleId;

    // Column-level default of false is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, and this field
    // is a primitive boolean — reading a NULL column into it throws, not just defaults to false.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified = false;
```

```java
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
```

- [ ] **Step 3: Mark the seeded admin account as verified**

In `src/main/java/com/printplatform/config/DataInitializer.java`, in `run()`:

```java
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        userRepository.save(admin);
```

- [ ] **Step 4: Re-run the full suite — must still be green (no behavior depends on the new field yet)**

Run: `mvn test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/printplatform/model/User.java src/main/java/com/printplatform/config/DataInitializer.java
git commit -m "feat: add emailVerified field to User"
```

---

### Task 3: Add `EmailVerificationToken` entity and repository

**Files:**
- Create: `src/main/java/com/printplatform/model/EmailVerificationToken.java`
- Create: `src/main/java/com/printplatform/repository/EmailVerificationTokenRepository.java`

**Interfaces:**
- Produces: `EmailVerificationToken` (getters/setters: `id`, `user`, `token`, `expiresAt`, `used`, `createdAt`) and `EmailVerificationTokenRepository` with `findByToken(UUID): Optional<EmailVerificationToken>`, `deleteByUser(User): void`, `deleteExpired(LocalDateTime): void`. Task 6 (`EmailVerificationService`) consumes both directly.

- [ ] **Step 1: Create the entity**

`src/main/java/com/printplatform/model/EmailVerificationToken.java`:

```java
package com.printplatform.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private UUID token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public UUID getToken() { return token; }
    public void setToken(UUID token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Create the repository**

`src/main/java/com/printplatform/repository/EmailVerificationTokenRepository.java`:

```java
package com.printplatform.repository;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(UUID token);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
```

- [ ] **Step 3: Compile to confirm the new files are wired correctly**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/printplatform/model/EmailVerificationToken.java src/main/java/com/printplatform/repository/EmailVerificationTokenRepository.java
git commit -m "feat: add EmailVerificationToken entity and repository"
```

---

### Task 4: Strengthen password rules on registration and password-reset

**Files:**
- Modify: `src/main/java/com/printplatform/dto/RegisterRequest.java`
- Modify: `src/main/java/com/printplatform/dto/ResetPasswordRequest.java`

**Interfaces:**
- No new symbols — same DTO shape, stricter `@Valid` constraints. Task 8's controller tests exercise this at the HTTP boundary (this codebase has no dedicated bean-validation unit test layer — validation is tested through the controller everywhere else, e.g. `register_invalidBody_returns400`).

- [ ] **Step 1: Update `RegisterRequest`**

`src/main/java/com/printplatform/dto/RegisterRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @Email(message = "Podaj poprawny adres email")
    @NotBlank(message = "Email jest wymagany")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Hasło musi zawierać wielką literę, małą literę i cyfrę"
    )
    private String password;

    /** Optional: if a valid admin code is supplied, the new account becomes an administrator. */
    private String adminCode;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAdminCode() { return adminCode; }
    public void setAdminCode(String adminCode) { this.adminCode = adminCode; }
}
```

- [ ] **Step 2: Update `ResetPasswordRequest`** (a reset is effectively setting a new password — same rule applies)

`src/main/java/com/printplatform/dto/ResetPasswordRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Hasło musi zawierać wielką literę, małą literę i cyfrę"
    )
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
```

- [ ] **Step 3: Compile**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/printplatform/dto/RegisterRequest.java src/main/java/com/printplatform/dto/ResetPasswordRequest.java
git commit -m "feat: require 8+ char passwords with upper/lower/digit on register and reset"
```

---

### Task 5: Add `EmailService.sendVerificationEmail`

**Files:**
- Modify: `src/main/java/com/printplatform/service/EmailService.java`

**Interfaces:**
- Produces: `EmailService.sendVerificationEmail(String to, UUID token): void`. Consumed by `EmailVerificationService` in Task 6.

- [ ] **Step 1: Add the method**

In `src/main/java/com/printplatform/service/EmailService.java`, add after `sendPasswordResetEmail`:

```java
    public void sendVerificationEmail(String to, UUID token) {
        String link = baseUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Potwierdź swój adres email — Druk3D");
        message.setText(
            "Cześć,\n\n" +
            "Dziękujemy za rejestrację w serwisie Druk3D.\n\n" +
            "Kliknij poniższy link, aby potwierdzić swój adres email (ważny przez 24 godziny):\n\n" +
            link + "\n\n" +
            "Jeśli to nie Ty zakładałeś/-aś to konto, zignoruj tę wiadomość.\n\n" +
            "— Zespół Druk3D"
        );
        mailSender.send(message);
    }
```

- [ ] **Step 2: Compile**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/printplatform/service/EmailService.java
git commit -m "feat: add verification email template to EmailService"
```

---

### Task 6: Add `EmailVerificationService`

**Files:**
- Create: `src/main/java/com/printplatform/dto/ResendVerificationRequest.java`
- Create: `src/main/java/com/printplatform/service/EmailVerificationService.java`
- Test: `src/test/java/com/printplatform/service/EmailVerificationServiceTest.java`

**Interfaces:**
- Consumes: `EmailVerificationTokenRepository` (Task 3), `EmailService.sendVerificationEmail` (Task 5), `UserRepository.findByEmail` (existing).
- Produces: `EmailVerificationService.issueAndSendToken(User): void`, `.verify(String rawToken): void`, `.resend(String email): void`. Task 7 (`AuthService.register`) calls `issueAndSendToken`; Task 8 (`AuthController`) calls `verify` and `resend`.

- [ ] **Step 1: Add the DTO**

`src/main/java/com/printplatform/dto/ResendVerificationRequest.java`:

```java
package com.printplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ResendVerificationRequest {

    @NotBlank
    @Email
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/printplatform/service/EmailVerificationServiceTest.java`:

```java
package com.printplatform.service;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationTokenRepository tokenRepository;
    @Mock
    private EmailService emailService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, tokenRepository, emailService);
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void issueAndSendToken_deletesOldTokensCreatesNewTokenExpiring24hAndSendsEmail() {
        User user = buildUser("user@example.com");

        service.issueAndSendToken(user);

        verify(tokenRepository).deleteByUser(user);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(25));

        verify(emailService).sendVerificationEmail(eq("user@example.com"), eq(saved.getToken()));
    }

    @Test
    void issueAndSendToken_emailSendingFails_doesNotPropagateException() {
        User user = buildUser("user@example.com");
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendVerificationEmail(anyString(), any(UUID.class));

        assertThatCode(() -> service.issueAndSendToken(user)).doesNotThrowAnyException();

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void verify_validToken_marksUserVerifiedAndTokenUsed() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().plusHours(23));
        evt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        service.verify(tokenUuid.toString());

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(evt.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).save(evt);
    }

    @Test
    void verify_malformedTokenString_throwsBadRequest() {
        assertThatThrownBy(() -> service.verify("not-a-uuid"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void verify_unknownToken_throwsBadRequest() {
        UUID tokenUuid = UUID.randomUUID();
        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verify_expiredToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        evt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verify_alreadyUsedToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().plusHours(1));
        evt.setUsed(true);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resend_unverifiedUser_issuesNewTokenAndSendsEmail() {
        User user = buildUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.resend("user@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository).deleteByUser(user);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("user@example.com"), any(UUID.class));
    }

    @Test
    void resend_alreadyVerifiedUser_doesNothing() {
        User user = buildUser("user@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.resend("user@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resend_unknownEmail_onlyDeletesExpiredAndDoesNothingElse() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.resend("ghost@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails with a compile error (service doesn't exist yet)**

Run: `mvn test -Dtest=EmailVerificationServiceTest`
Expected: `COMPILATION ERROR` — `cannot find symbol: class EmailVerificationService`

- [ ] **Step 4: Implement `EmailVerificationService`**

`src/main/java/com/printplatform/service/EmailVerificationService.java`:

```java
package com.printplatform.service;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void issueAndSendToken(User user) {
        tokenRepository.deleteByUser(user);
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(UUID.randomUUID());
        evt.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(evt);
        try {
            emailService.sendVerificationEmail(user.getEmail(), evt.getToken());
        } catch (MailException e) {
            log.warn("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public void verify(String rawToken) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy.");
        }

        EmailVerificationToken evt = tokenRepository.findByToken(tokenUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy."));

        if (evt.isUsed() || evt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy.");
        }

        User user = evt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        evt.setUsed(true);
        tokenRepository.save(evt);
    }

    @Transactional
    public void resend(String email) {
        tokenRepository.deleteExpired(LocalDateTime.now());
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueAndSendToken);
        // silently no-ops when email not found or already verified — prevents enumeration
    }
}
```

- [ ] **Step 5: Run the tests again — must pass**

Run: `mvn test -Dtest=EmailVerificationServiceTest`
Expected: `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/printplatform/dto/ResendVerificationRequest.java src/main/java/com/printplatform/service/EmailVerificationService.java src/test/java/com/printplatform/service/EmailVerificationServiceTest.java
git commit -m "feat: add EmailVerificationService"
```

---

### Task 7: Wire verification into `AuthService` (register/login/OAuth)

**Files:**
- Modify: `src/main/java/com/printplatform/service/AuthService.java`
- Modify: `src/test/java/com/printplatform/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `EmailVerificationService.issueAndSendToken(User)` (Task 6).
- Produces: `AuthService.register(RegisterRequest): void` (return type changes from `AuthResponse` — Task 8's `AuthController` must be updated in the same PR, it's done next in Task 8). `login()` now throws `403 FORBIDDEN` when `!user.isEmailVerified()`. `loginWithGoogle`/`loginWithFacebook` unchanged in signature, but newly-created users now have `emailVerified = true`.

- [ ] **Step 1: Update the failing/changing tests first**

In `src/test/java/com/printplatform/service/AuthServiceTest.java`:

Replace the `register_newEmail_savesUserAndReturnsAuthResponse` test (register no longer returns a token — it returns void and triggers a verification email instead) and inject the new dependency. Full replacement of the test class's setup and the four `register_*` tests:

```java
    @Mock
    private FacebookAuthClient facebookAuthClient;
    @Mock
    private GoogleAuthClient googleAuthClient;
    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, adminService,
                facebookAuthClient, googleAuthClient, emailVerificationService);
    }
```

```java
    @Test
    void register_newEmail_savesUnverifiedUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secret123");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        authService.register(request);

        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("new@example.com")
                        && u.getPassword().equals("encoded-secret")
                        && u.getRole() == Role.USER
                        && !u.isEmailVerified()));
        verify(emailVerificationService).issueAndSendToken(argThat(u -> u.getEmail().equals("new@example.com")));
        verifyNoInteractions(adminService);
        verifyNoInteractions(jwtService);
    }
```

Update `register_existingEmail_throwsConflict` to assert no email is sent:

```java
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailVerificationService);
```

(append that to the existing test body, in place of nothing — the test currently only asserts `save` is never called).

Update `register_withNonBlankAdminCode_appliesCodeToNewUser` and the two blank/null admin-code tests: remove the `when(jwtService.generateToken(...))` stub (no longer called) and change `request.setPassword("secret123")` to `request.setPassword("Secret123")` in all four register tests above (the mock encoder stub only matches the exact string passed in, so it must match whatever the test sets — using a value that also satisfies the new complexity rule keeps these tests meaningful as regression coverage, even though `AuthServiceTest` bypasses `@Valid` entirely).

Now update the login test for the verified-gate. Add `user.setEmailVerified(true);` to `buildUser(...)` used by `login_validCredentials_returnsAuthResponse`, or simpler: change the shared `buildUser` helper itself to default verified, since every other test in this file that relies on it (`login_wrongPassword_throwsUnauthorized`, `login_facebookOnlyAccount_...`) fails on password/account-type before ever reaching the verified check, and the two OAuth-creation tests (`loginWithFacebook_newUser_...`, `loginWithGoogle_newUser_...`) build their `User` inside `AuthService`, not via this test helper:

```java
    private User buildUser(String email, String encodedPassword, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setEmailVerified(true);
        return user;
    }
```

Add one new test, right after `login_wrongPassword_throwsUnauthorized`:

```java
    @Test
    void login_unverifiedEmail_throwsForbidden() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unverified@example.com");
        request.setPassword("secret123");

        User user = buildUser("unverified@example.com", "encoded-secret", Role.USER);
        user.setEmailVerified(false);
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-secret")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(jwtService, never()).generateToken(any());
    }
```

Finally, assert the OAuth-creation tests mark the new user verified. In `loginWithFacebook_newUser_createsAccountAndReturnsAuthResponse`, extend the `argThat` in the `verify(userRepository).save(...)` assertion:

```java
        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("newfb@example.com")
                        && "fb123".equals(u.getFacebookId())
                        && u.getPassword() == null
                        && u.getFirstName().equals("Jan")
                        && u.getRole() == Role.USER
                        && u.isEmailVerified()));
```

Same for `loginWithGoogle_newUser_createsAccountAndReturnsAuthResponse`:

```java
        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("newgoogle@example.com")
                        && "google123".equals(u.getGoogleId())
                        && u.getPassword() == null
                        && u.getFirstName().equals("Anna")
                        && u.getRole() == Role.USER
                        && u.isEmailVerified()));
```

- [ ] **Step 2: Run the tests to see them fail (compile error — new constructor param, `register()` still returns `AuthResponse`)**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: `COMPILATION ERROR`

- [ ] **Step 3: Implement the `AuthService` changes**

Full replacement of `src/main/java/com/printplatform/service/AuthService.java`:

```java
package com.printplatform.service;

import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.dto.GoogleLoginRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.FacebookAuthClient;
import com.printplatform.security.GoogleAuthClient;
import com.printplatform.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminService adminService;
    private final FacebookAuthClient facebookAuthClient;
    private final GoogleAuthClient googleAuthClient;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AdminService adminService,
                       FacebookAuthClient facebookAuthClient,
                       GoogleAuthClient googleAuthClient,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.facebookAuthClient = facebookAuthClient;
        this.googleAuthClient = googleAuthClient;
        this.emailVerificationService = emailVerificationService;
    }

    public void register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Konto z tym emailem już istnieje");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        // Optional admin code promotes the new account to administrator.
        if (request.getAdminCode() != null && !request.getAdminCode().isBlank()) {
            adminService.applyCodeToNewUser(request.getAdminCode(), user);
        }

        userRepository.save(user);
        emailVerificationService.issueAndSendToken(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło"));

        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "To konto używa logowania przez Facebook.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Potwierdź adres email, aby się zalogować. Sprawdź swoją skrzynkę pocztową.");
        }
        return toResponse(user);
    }

    public AuthResponse loginWithFacebook(FacebookLoginRequest request) {
        FacebookAuthClient.FacebookProfile profile = facebookAuthClient.verify(request.getAccessToken());

        if (profile.email() == null || profile.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Potrzebujemy dostępu do Twojego emaila, aby się zalogować.");
        }

        // Auto-link trusts profile.email() as verified by Facebook (Graph API only returns
        // a confirmed email address) — do not extend this pattern to a provider that doesn't
        // guarantee email verification.
        User user = userRepository.findByFacebookId(profile.facebookId())
                .orElseGet(() -> userRepository.findByEmail(profile.email())
                        .map(existing -> linkFacebookId(existing, profile.facebookId()))
                        .orElseGet(() -> createFacebookUser(profile)));

        return toResponse(user);
    }

    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleAuthClient.GoogleProfile profile = googleAuthClient.verify(request.getIdToken());

        // Unlike Facebook, Google logins do NOT auto-link onto an existing account —
        // reject instead, so the user goes back to whichever method that account already uses.
        User user = userRepository.findByGoogleId(profile.googleId())
                .orElseGet(() -> {
                    if (userRepository.findByEmail(profile.email()).isPresent()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Konto z tym adresem email już istnieje. Zaloguj się przez email lub Facebook.");
                    }
                    return createGoogleUser(profile);
                });

        return toResponse(user);
    }

    private User linkFacebookId(User user, String facebookId) {
        user.setFacebookId(facebookId);
        return userRepository.save(user);
    }

    private User createFacebookUser(FacebookAuthClient.FacebookProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setFacebookId(profile.facebookId());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.USER);
        // Graph API only returns a confirmed email address — no separate verification needed.
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private User createGoogleUser(GoogleAuthClient.GoogleProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setGoogleId(profile.googleId());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.USER);
        // GoogleAuthClient.verify() already rejects tokens where email_verified != true.
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }
}
```

- [ ] **Step 4: Run the tests — must pass**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: `Tests run: 18, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/printplatform/service/AuthService.java src/test/java/com/printplatform/service/AuthServiceTest.java
git commit -m "feat: gate login on email verification, mark OAuth signups pre-verified"
```

---

### Task 8: Update `AuthController` — register contract change + two new endpoints

**Files:**
- Modify: `src/main/java/com/printplatform/controller/AuthController.java`
- Modify: `src/test/java/com/printplatform/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AuthService.register(RegisterRequest): void` (Task 7), `EmailVerificationService.verify(String)` / `.resend(String)` (Task 6).
- Produces: `POST /api/auth/register` now returns `201` with an **empty body** (was `AuthResponse`). `GET /api/auth/verify-email?token=`, `POST /api/auth/resend-verification`. `POST /api/auth/login` now also returns `403` for unverified accounts. These are the contracts Task 10 (frontend `auth.service.ts`) is written against.

- [ ] **Step 1: Update the controller tests first**

Full replacement of `src/test/java/com/printplatform/controller/AuthControllerTest.java`:

```java
package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.ForgotPasswordRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.dto.ResendVerificationRequest;
import com.printplatform.dto.ResetPasswordRequest;
import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.PasswordResetToken;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.PasswordResetTokenRepository;
import com.printplatform.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOTE: /api/auth/** is throttled per client IP by AuthRateLimitFilter, a singleton bean shared
 * by every test in this class' Spring context (fixed in-memory window, not reset between test
 * methods). The cap is bumped to 100 in src/test/resources/application.properties specifically
 * to give this class headroom — do not hit /api/auth/** from any other test class sharing this
 * same context configuration without accounting for the shared budget.
 */
@Transactional
class AuthControllerTest extends AbstractControllerTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void register_newEmail_returns201AndSendsVerificationEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newbie-" + UUID.randomUUID() + "@test.local");
        request.setPassword("Secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(request.getEmail()).orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        verify(emailService).sendVerificationEmail(eq(request.getEmail()), any(UUID.class));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        User existing = persistUser();

        RegisterRequest request = new RegisterRequest();
        request.setEmail(existing.getEmail());
        request.setPassword("Secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("123"); // too short

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordMissingUppercase_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("weakpw-" + UUID.randomUUID() + "@test.local");
        request.setPassword("alllowercase1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_verifiedUser_validCredentials_returns200WithToken() throws Exception {
        String rawPassword = "correct-password";
        User user = new User();
        user.setEmail("login-" + UUID.randomUUID() + "@test.local");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        userRepository.save(user);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(rawPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        User user = new User();
        user.setEmail("wrongpass-" + UUID.randomUUID() + "@test.local");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setRole(Role.USER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("totally-wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unverifiedEmail_returns403() throws Exception {
        User user = persistUser(); // persistUser() does not set emailVerified — defaults to false

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("Password123"); // matches AbstractControllerTest.persistUser()

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPassword_existingEmail_returns200AndTriggersEmail() throws Exception {
        User user = persistUser();
        doNothing().when(emailService).sendPasswordResetEmail(any(), any());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(user.getEmail());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), any(UUID.class));
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("not-a-uuid");
        request.setNewPassword("brandNewPass1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_validToken_returns200AndUpdatesPassword() throws Exception {
        User user = persistUser();

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(UUID.randomUUID());
        prt.setExpiresAt(LocalDateTime.now().plusHours(1));
        passwordResetTokenRepository.save(prt);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(prt.getToken().toString());
        request.setNewPassword("brandNewPass1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brandNewPass1", reloaded.getPassword())).isTrue();
    }

    @Test
    void verifyEmail_validToken_returns200AndMarksUserVerified() throws Exception {
        User user = persistUser(); // starts unverified

        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(UUID.randomUUID());
        evt.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(evt);

        mockMvc.perform(get("/api/auth/verify-email").param("token", evt.getToken().toString()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_unverifiedEmail_returns200AndTriggersEmail() throws Exception {
        User user = persistUser(); // starts unverified

        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail(user.getEmail());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailService).sendVerificationEmail(eq(user.getEmail()), any(UUID.class));
    }
}
```

- [ ] **Step 2: Run the tests to see them fail (compile error — controller doesn't have the new endpoints, register still returns a body)**

Run: `mvn test -Dtest=AuthControllerTest`
Expected: `COMPILATION ERROR` or assertion failures on `status().isCreated()` body assumptions

- [ ] **Step 3: Implement the controller changes**

Full replacement of `src/main/java/com/printplatform/controller/AuthController.java`:

```java
package com.printplatform.controller;

import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.dto.ForgotPasswordRequest;
import com.printplatform.dto.GoogleLoginRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.dto.ResendVerificationRequest;
import com.printplatform.dto.ResetPasswordRequest;
import com.printplatform.service.AuthService;
import com.printplatform.service.EmailVerificationService;
import com.printplatform.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/facebook")
    public AuthResponse loginWithFacebook(@Valid @RequestBody FacebookLoginRequest request) {
        return authService.loginWithFacebook(request);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.initReset(request.getEmail());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
    }

    @GetMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public void verifyEmail(@RequestParam String token) {
        emailVerificationService.verify(token);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.OK)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.getEmail());
    }
}
```

Note: no `SecurityConfig` change is needed — `/api/auth/**` is already `permitAll()`.

- [ ] **Step 4: Run the tests — must pass**

Run: `mvn test -Dtest=AuthControllerTest`
Expected: `Tests run: 13, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full backend suite to catch any other regressions (e.g. other controller tests using `persistUser()`)**

Run: `mvn test`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/printplatform/controller/AuthController.java src/test/java/com/printplatform/controller/AuthControllerTest.java
git commit -m "feat: add verify-email and resend-verification endpoints, register no longer auto-logs-in"
```

---

### Task 9: One-time production backfill script

**Files:**
- Create: `scripts/backfill-email-verified.sql`

**Interfaces:** None — operational script, not invoked by application code.

- [ ] **Step 1: Write the script**

`scripts/backfill-email-verified.sql`:

```sql
-- One-time backfill for the email-verification feature (2026-07-11).
--
-- Hibernate's ddl-auto=update adds the new `email_verified` column with a database-level
-- DEFAULT of false (see User.java's @ColumnDefault("false")), so every row that existed
-- before this deploy starts out unverified. Run this ONCE, immediately after deploying the
-- backend that introduces the column, to grandfather existing accounts in — otherwise every
-- user who registered before today is locked out of login until they click a verification
-- link they never received.
--
-- Safe to run more than once (idempotent — only touches rows still at the default).
-- Does NOT affect accounts created after this point in time; new registrations correctly
-- start unverified via application logic (AuthService.register()).

UPDATE users
SET email_verified = true
WHERE email_verified = false;
```

- [ ] **Step 2: Commit**

```bash
git add scripts/backfill-email-verified.sql
git commit -m "docs: add one-time backfill script for existing users' email_verified flag"
```

---

### Task 10: Frontend `auth.service.ts` — register contract change + new methods

**Files:**
- Modify: `frontend/src/app/services/auth.service.ts`
- Modify: `frontend/src/app/services/auth.service.spec.ts`

**Interfaces:**
- Produces: `AuthService.register(payload: RegisterPayload): Observable<void>` (was `Observable<AuthUser>` — no longer persists a session), `AuthService.verifyEmail(token: string): Observable<void>`, `AuthService.resendVerification(email: string): Observable<void>`. Task 11 (`auth.component.ts`) and Task 12 (`VerifyEmailComponent`) consume these.

- [ ] **Step 1: Update the failing/changing test first**

In `frontend/src/app/services/auth.service.spec.ts`, replace the `register()` test (it no longer persists a user) and add two new tests, right after the `resetPassword()` test:

```typescript
  it('register() POSTs payload and does not persist a session (registration no longer auto-logs-in)', () => {
    service.register({ email: user.email, password: 'pw', adminCode: 'CODE' }).subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: user.email, password: 'pw', adminCode: 'CODE' });
    req.flush(null);

    expect(service.currentUser()).toBeNull();
  });
```

```typescript
  it('resetPassword() POSTs token and newPassword', () => {
    service.resetPassword('tok', 'newpw').subscribe();
    const req = httpMock.expectOne('/api/auth/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok', newPassword: 'newpw' });
    req.flush(null);
  });

  it('verifyEmail() GETs with the token as a query param', () => {
    service.verifyEmail('tok123').subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/auth/verify-email' && r.params.get('token') === 'tok123');
    expect(req.request.method).toBe('GET');
    req.flush(null);
  });

  it('resendVerification() POSTs the email', () => {
    service.resendVerification('a@b.com').subscribe();
    const req = httpMock.expectOne('/api/auth/resend-verification');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com' });
    req.flush(null);
  });
```

(the `resetPassword()` block above is unchanged — shown only so the new tests' placement after it is unambiguous)

- [ ] **Step 2: Run the frontend tests to see the new ones fail**

Run: `npm test -- --run` (from `frontend/`)
Expected: failures for `register() POSTs payload and does not persist a session`, `verifyEmail()`, `resendVerification()` — methods/behavior don't exist yet

- [ ] **Step 3: Implement the service changes**

In `frontend/src/app/services/auth.service.ts`, replace `register()` and add the two new methods after `resetPassword()`:

```typescript
  register(payload: RegisterPayload): Observable<void> {
    return this.http.post<void>('/api/auth/register', payload);
  }
```

```typescript
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', { token, newPassword });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.get<void>('/api/auth/verify-email', { params: { token } });
  }

  resendVerification(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/resend-verification', { email });
  }
```

- [ ] **Step 4: Run the frontend tests — must pass**

Run: `npm test -- --run` (from `frontend/`)
Expected: all `auth.service.spec.ts` tests pass

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/services/auth.service.ts frontend/src/app/services/auth.service.spec.ts
git commit -m "feat: register no longer auto-logs-in, add verifyEmail/resendVerification"
```

---

### Task 11: Frontend `auth.component` — password rules, register-sent state, unverified-login resend

**Files:**
- Modify: `frontend/src/app/features/auth/auth.component.ts`
- Modify: `frontend/src/app/features/auth/auth.component.html`
- Modify: `frontend/src/app/features/auth/auth.component.spec.ts`

**Interfaces:**
- Consumes: `AuthService.register(): Observable<void>` (Task 10, changed), `AuthService.resendVerification(email): Observable<void>` (Task 10, new).
- Produces: `AuthComponent.registerSent: Signal<boolean>`, `.unverifiedEmail: Signal<string | null>`, `.resendVerificationSent: Signal<boolean>`, `.resendVerification(): void`. No other task consumes these — this is the UI leaf.

- [ ] **Step 1: Update the failing/changing tests first**

In `frontend/src/app/features/auth/auth.component.spec.ts`:

Add `resendVerification` to the `authStub` shape and its `beforeEach` initialization:

```typescript
  let authStub: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    forgotPassword: ReturnType<typeof vi.fn>;
    loginWithFacebook: ReturnType<typeof vi.fn>;
    loginWithGoogle: ReturnType<typeof vi.fn>;
    resendVerification: ReturnType<typeof vi.fn>;
  };
```

```typescript
    authStub = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      login: vi.fn(),
      register: vi.fn(),
      forgotPassword: vi.fn(),
      loginWithFacebook: vi.fn(),
      loginWithGoogle: vi.fn(),
      resendVerification: vi.fn()
    };
```

Replace the `register form validation` describe block's minlength test (now 8, not 6) and add a complexity test:

```typescript
  describe('register form validation', () => {
    it('flags a mismatch between password and passwordConfirm', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret13');
      expect(component.registerForm.hasError('mismatch')).toBe(true);
      expect(component.registerForm.invalid).toBe(true);
    });

    it('is valid when passwords match and meet strength rules', () => {
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      expect(component.registerForm.valid).toBe(true);
    });

    it('rejects passwords shorter than 8 characters', () => {
      const component = createComponent();
      component.re.password.setValue('Abc123');
      expect(component.re.password.hasError('minlength')).toBe(true);
    });

    it('rejects passwords missing an uppercase letter', () => {
      const component = createComponent();
      component.re.password.setValue('secret123');
      expect(component.re.password.hasError('complexity')).toBe(true);
    });

    it('rejects passwords missing a digit', () => {
      const component = createComponent();
      component.re.password.setValue('SecretPass');
      expect(component.re.password.hasError('complexity')).toBe(true);
    });
  });
```

Replace the `register()` describe block (registration no longer auto-logs-in):

```typescript
  describe('register()', () => {
    it('does nothing but mark fields touched when the form is invalid', () => {
      const component = createComponent();
      component.register();
      expect(authStub.register).not.toHaveBeenCalled();
      expect(component.re.email.touched).toBe(true);
    });

    it('registers and shows the registerSent state on success, trimming an empty adminCode away', () => {
      authStub.register.mockReturnValue(of(undefined));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      component.re.adminCode.setValue('   ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Secret12', adminCode: undefined });
      expect(component.registerSent()).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('passes a trimmed adminCode through when provided', () => {
      authStub.register.mockReturnValue(of(undefined));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');
      component.re.adminCode.setValue('  CODE1  ');

      component.register();

      expect(authStub.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Secret12', adminCode: 'CODE1' });
    });

    it('sets serverError on failure', () => {
      authStub.register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
      const component = createComponent();
      component.re.email.setValue('a@b.com');
      component.re.password.setValue('Secret12');
      component.re.passwordConfirm.setValue('Secret12');

      component.register();

      expect(component.serverError()).toBe('Rejestracja nie powiodła się.');
      expect(component.registerSent()).toBe(false);
    });
  });
```

Add a new describe block for the unverified-login/resend flow, after the `login()` describe block:

```typescript
  describe('login() when the account is unverified', () => {
    it('sets unverifiedEmail so the resend button can appear', () => {
      authStub.login.mockReturnValue(
        throwError(() => new HttpErrorResponse({ error: { message: 'Potwierdź adres email.' }, status: 403 }))
      );
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('secret');

      component.login();

      expect(component.serverError()).toBe('Potwierdź adres email.');
      expect(component.unverifiedEmail()).toBe('a@b.com');
    });

    it('does not set unverifiedEmail for other error statuses', () => {
      authStub.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
      const component = createComponent();
      component.le.email.setValue('a@b.com');
      component.le.password.setValue('wrong');

      component.login();

      expect(component.unverifiedEmail()).toBeNull();
    });
  });

  describe('resendVerification()', () => {
    it('calls the API with unverifiedEmail and sets resendVerificationSent', () => {
      authStub.resendVerification.mockReturnValue(of(undefined));
      const component = createComponent();
      component.unverifiedEmail.set('a@b.com');

      component.resendVerification();

      expect(authStub.resendVerification).toHaveBeenCalledWith('a@b.com');
      expect(component.resendVerificationSent()).toBe(true);
    });
  });
```

- [ ] **Step 2: Run the frontend tests to see the new/changed ones fail**

Run: `npm test -- --run` (from `frontend/`)
Expected: failures — `registerSent`, `unverifiedEmail`, `resendVerificationSent`, `resendVerification()` don't exist yet; minlength/complexity assertions fail against the old 6-char rule

- [ ] **Step 3: Implement the component changes**

In `frontend/src/app/features/auth/auth.component.ts`, add a module-level password-complexity validator function above the `@Component` decorator:

```typescript
function passwordComplexity(control: AbstractControl) {
  const value: string = control.value ?? '';
  if (!value) return null; // required validator handles emptiness
  const hasLower = /[a-z]/.test(value);
  const hasUpper = /[A-Z]/.test(value);
  const hasDigit = /\d/.test(value);
  return hasLower && hasUpper && hasDigit ? null : { complexity: true };
}
```

Update the `registerForm` password validators:

```typescript
  registerForm = this.fb.group({
    email:           ['', [Validators.required, Validators.email]],
    password:        ['', [Validators.required, Validators.minLength(8), passwordComplexity]],
    passwordConfirm: ['', [Validators.required]],
    adminCode:       ['']
  }, { validators: this.passwordsMatch });
```

Add new signals next to the existing ones:

```typescript
  activeTab   = signal<Tab>('login');
  loading     = signal(false);
  serverError = signal<string | null>(null);
  forgotSent  = signal(false);
  registerSent = signal(false);
  unverifiedEmail = signal<string | null>(null);
  resendVerificationSent = signal(false);
  returnUrl   = '/';
```

Update `login()`'s error handler to detect the 403-unverified case:

```typescript
  login(): void {
    if (this.loginForm.invalid) { this.loginForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    this.unverifiedEmail.set(null);
    this.resendVerificationSent.set(false);
    const { email, password } = this.loginForm.getRawValue();
    this.auth.login({ email: email!, password: password! }).subscribe({
      next: () => { this.loading.set(false); this.router.navigateByUrl(this.returnUrl); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Nieprawidłowy email lub hasło.');
        if (err.status === 403) {
          this.unverifiedEmail.set(email!);
        }
      }
    });
  }
```

Add a `resendVerification()` method, near `submitForgot()`:

```typescript
  resendVerification(): void {
    const email = this.unverifiedEmail();
    if (!email) return;
    this.loading.set(true);
    this.auth.resendVerification(email).subscribe({
      next: () => { this.loading.set(false); this.resendVerificationSent.set(true); },
      error: () => { this.loading.set(false); this.resendVerificationSent.set(true); } // anti-enumeration: same as forgot-password
    });
  }
```

Update `register()` — no more auto-login/navigation, show the sent state instead:

```typescript
  register(): void {
    if (this.registerForm.invalid) { this.registerForm.markAllAsTouched(); return; }
    this.loading.set(true);
    this.serverError.set(null);
    const { email, password, adminCode } = this.registerForm.getRawValue();
    this.auth.register({ email: email!, password: password!, adminCode: adminCode?.trim() || undefined }).subscribe({
      next: () => { this.loading.set(false); this.registerSent.set(true); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.serverError.set(err.error?.message ?? 'Rejestracja nie powiodła się.');
      }
    });
  }
```

Update `switchTab()` to also reset the new signals:

```typescript
  switchTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.serverError.set(null);
    this.forgotSent.set(false);
    this.forgotForm.reset();
    this.registerSent.set(false);
    this.unverifiedEmail.set(null);
    this.resendVerificationSent.set(false);
  }
```

Now update `frontend/src/app/features/auth/auth.component.html`.

Add a resend-verification action right after the server error alert block (only shows when `unverifiedEmail()` is set and hasn't been resent yet):

```html
    <!-- Server error -->
    @if (serverError()) {
      <div class="alert alert--error" role="alert" aria-live="assertive">
        ⚠️ {{ serverError() }}
      </div>
    }

    @if (unverifiedEmail() && !resendVerificationSent()) {
      <p class="form__switch">
        <button type="button" class="link-btn" (click)="resendVerification()" [disabled]="loading()">
          Wyślij ponownie link weryfikacyjny
        </button>
      </p>
    } @else if (resendVerificationSent()) {
      <div class="alert alert--success" role="status" aria-live="polite">
        Wysłano ponownie. Sprawdź swoją skrzynkę pocztową.
      </div>
    }
```

Wrap the existing register `<form>` so it's hidden once `registerSent()` is true, and show a success message instead. Replace the `<!-- REGISTER PANEL -->` block:

```html
    <!-- REGISTER PANEL -->
    @if (activeTab() === 'register') {
      @if (registerSent()) {
        <div class="alert alert--success" role="status" aria-live="polite">
          Sprawdź swoją skrzynkę email, aby potwierdzić konto.
        </div>
        <p class="form__switch">
          <button type="button" class="link-btn" (click)="switchTab('login')">Wróć do logowania</button>
        </p>
      } @else {
      <form
        id="panel-register"
        role="tabpanel"
        aria-labelledby="tab-register"
        class="form"
        [formGroup]="registerForm"
        (ngSubmit)="register()"
        novalidate
      >
        <div class="field">
          <label class="field__label" for="reg-email">Email</label>
          <input
            id="reg-email"
            type="email"
            class="field__input"
            [class.field__input--error]="re['email'].invalid && re['email'].touched"
            formControlName="email"
            placeholder="jan@kowalski.pl"
            autocomplete="email"
            [attr.aria-invalid]="re['email'].invalid && re['email'].touched ? 'true' : null"
            aria-describedby="re-email-err"
          />
          @if (re['email'].invalid && re['email'].touched) {
            <p id="re-email-err" class="field__error" role="alert">
              @if (re['email'].errors?.['required']) { Email jest wymagany. }
              @else { Podaj poprawny adres email. }
            </p>
          }
        </div>

        <div class="field">
          <label class="field__label" for="reg-pw">Hasło</label>
          <input
            id="reg-pw"
            type="password"
            class="field__input"
            [class.field__input--error]="re['password'].invalid && re['password'].touched"
            formControlName="password"
            placeholder="min. 8 znaków"
            autocomplete="new-password"
            [attr.aria-invalid]="re['password'].invalid && re['password'].touched ? 'true' : null"
            aria-describedby="re-pw-err"
          />
          @if (re['password'].invalid && re['password'].touched) {
            <p id="re-pw-err" class="field__error" role="alert">
              @if (re['password'].errors?.['required']) { Hasło jest wymagane. }
              @else if (re['password'].errors?.['minlength']) { Hasło musi mieć co najmniej 8 znaków. }
              @else { Hasło musi zawierać wielką literę, małą literę i cyfrę. }
            </p>
          }
        </div>

        <div class="field">
          <label class="field__label" for="reg-pw2">Powtórz hasło</label>
          <input
            id="reg-pw2"
            type="password"
            class="field__input"
            [class.field__input--error]="(re['passwordConfirm'].invalid || registerForm.errors?.['mismatch']) && re['passwordConfirm'].touched"
            formControlName="passwordConfirm"
            placeholder="••••••••"
            autocomplete="new-password"
            [attr.aria-invalid]="(re['passwordConfirm'].invalid || registerForm.errors?.['mismatch']) && re['passwordConfirm'].touched ? 'true' : null"
            aria-describedby="re-pw2-err"
          />
          @if (re['passwordConfirm'].touched) {
            @if (re['passwordConfirm'].errors?.['required']) {
              <p id="re-pw2-err" class="field__error" role="alert">Powtórz hasło.</p>
            } @else if (registerForm.errors?.['mismatch']) {
              <p id="re-pw2-err" class="field__error" role="alert">Hasła nie są identyczne.</p>
            }
          }
        </div>

        <div class="field">
          <label class="field__label" for="reg-admin">
            Kod administratora <span class="field__optional">(opcjonalnie)</span>
          </label>
          <input
            id="reg-admin"
            type="text"
            class="field__input"
            formControlName="adminCode"
            placeholder="np. ABCD-EFGH-JKLM"
            autocomplete="off"
            aria-describedby="re-admin-hint"
          />
          <p id="re-admin-hint" class="field__hint">
            Masz kod? Konto zostanie utworzone z uprawnieniami administratora.
          </p>
        </div>

        <button
          type="submit"
          class="btn btn--primary btn--full"
          [disabled]="loading()"
          [attr.aria-busy]="loading()"
        >
          @if (loading()) {
            <span class="spinner" aria-hidden="true"></span> Tworzenie konta...
          } @else { Utwórz konto }
        </button>

        <p class="form__switch">
          Masz już konto?
          <button type="button" class="link-btn" (click)="switchTab('login')">Zaloguj się</button>
        </p>
      </form>
      }
    }
```

- [ ] **Step 4: Run the frontend tests — must pass**

Run: `npm test -- --run` (from `frontend/`)
Expected: all `auth.component.spec.ts` tests pass

- [ ] **Step 5: Manually verify in the browser**

Run: `npm start` (from `frontend/`) and separately `mvn spring-boot:run` (from repo root) — need both since the frontend proxies `/api/**` to the backend in dev.
Navigate to `http://localhost:4200/logowanie?tab=register`, submit the register form with a weak password (e.g. `abc12345`) and confirm the "musi zawierać wielką literę..." error shows inline without a page reload. Then submit with a valid strong password and a fresh email; confirm the success alert replaces the form (no navigation, no auto-login). Then try logging in with that same fresh (unverified) account and confirm a 403 message plus a "Wyślij ponownie link weryfikacyjny" button appear.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/auth/auth.component.ts frontend/src/app/features/auth/auth.component.html frontend/src/app/features/auth/auth.component.spec.ts
git commit -m "feat: enforce password strength in register form, add unverified-login resend flow"
```

---

### Task 12: Frontend `VerifyEmailComponent` + route

**Files:**
- Create: `frontend/src/app/features/verify-email/verify-email.component.ts`
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `AuthService.verifyEmail(token): Observable<void>` (Task 10), `AuthService.resendVerification(email): Observable<void>` (Task 10).
- Produces: nothing consumed elsewhere — routed leaf component, modeled directly on the existing `ResetPasswordComponent`.

- [ ] **Step 1: Create the component**

`frontend/src/app/features/verify-email/verify-email.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../auth/auth.component.css'],
  template: `
    <div class="auth-page">
      <div class="auth-card">

        <a routerLink="/" class="auth-logo" aria-label="Strona główna">
          <span class="auth-logo__icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M10 2L18 6.5V13.5L10 18L2 13.5V6.5L10 2Z" fill="var(--accent)" opacity="0.9"/>
              <path d="M10 2L18 6.5L10 11L2 6.5L10 2Z" fill="white" opacity="0.22"/>
              <path d="M10 11V18L2 13.5V6.5L10 11Z" fill="white" opacity="0.1"/>
            </svg>
          </span>
          <span>Druk3D</span>
        </a>

        @if (loading()) {
          <p class="form__switch">Potwierdzanie...</p>
        } @else if (success()) {
          <div class="alert alert--success" role="status" aria-live="polite">
            Konto potwierdzone!
          </div>
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Zaloguj się</a>
          </p>
        } @else {
          <div class="alert alert--error" role="alert">
            ⚠️ Link wygasł lub jest nieprawidłowy.
          </div>
          @if (resendSent()) {
            <div class="alert alert--success" role="status" aria-live="polite">
              Wysłano nowy link. Sprawdź swoją skrzynkę pocztową.
            </div>
          } @else {
            <div class="field">
              <label class="field__label" for="ve-email">Email</label>
              <input
                id="ve-email"
                type="email"
                class="field__input"
                [value]="email() ?? ''"
                (input)="email.set($any($event.target).value)"
                placeholder="jan@kowalski.pl"
                autocomplete="email"
              />
            </div>
            <button type="button" class="btn btn--primary btn--full" (click)="resend()" [disabled]="!email()">
              Wyślij nowy link
            </button>
          }
          <p class="form__switch">
            <a routerLink="/logowanie" class="link-btn">Wróć do logowania</a>
          </p>
        }

      </div>
    </div>
  `,
  styles: [`
    .alert--success {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #166534;
      border-radius: 6px;
      padding: 0.75rem 1rem;
      font-size: 0.875rem;
      margin-bottom: 1rem;
    }
  `]
})
export class VerifyEmailComponent implements OnInit {
  private auth  = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading    = signal(true);
  success    = signal(false);
  resendSent = signal(false);
  email      = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => { this.loading.set(false); }
    });
  }

  resend(): void {
    const email = this.email();
    if (!email) return;
    this.auth.resendVerification(email).subscribe({
      next: () => this.resendSent.set(true),
      error: () => this.resendSent.set(true) // anti-enumeration: same as forgot-password
    });
  }
}
```

Note: unlike `ResetPasswordComponent`, the token alone doesn't tell the failure state who it belonged to (and the backend doesn't expose that for an invalid/expired token — same anti-enumeration reasoning as `resend-verification` itself), so the failure state asks the user to type their email before resending, rather than trying to recover it from the dead token.

- [ ] **Step 2: Add the route**

In `frontend/src/app/app.routes.ts`, add after the `reset-password` route:

```typescript
  { path: 'reset-password', loadComponent: () => import('./features/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
  { path: 'verify-email', loadComponent: () => import('./features/verify-email/verify-email.component').then(m => m.VerifyEmailComponent) },
```

- [ ] **Step 3: Run the full frontend test suite to confirm no regressions (no dedicated spec for this component — same as `ResetPasswordComponent`, which also has none, per this codebase's existing convention)**

Run: `npm test -- --run` (from `frontend/`)
Expected: all tests pass, no new failures

- [ ] **Step 4: Manually verify in the browser**

With both `npm start` and the backend running, register a fresh account, find the verification email in the backend logs / mail catcher (or query `email_verification_tokens` directly against the local dev DB if SMTP isn't configured locally), and visit `http://localhost:4200/verify-email?token=<that token>`. Confirm "Konto potwierdzone!" shows and the account can then log in. Then visit the same URL again with the same (now-used) token and confirm the expired/invalid state shows instead.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/verify-email/verify-email.component.ts frontend/src/app/app.routes.ts
git commit -m "feat: add /verify-email page"
```

---

## Self-Review Notes

- **Spec coverage:** `emailVerified` field + backfill (Task 2, 9) — covered. `EmailVerificationToken`/24h expiry (Task 3, 6) — covered. Password strength on register + reset (Task 4) — covered. `EmailService.sendVerificationEmail` (Task 5) — covered. Register no longer issues JWT (Task 7, 8, 10) — covered. Login gates on verified (Task 7) — covered. OAuth signups pre-verified (Task 7) — covered. `verify-email` / `resend-verification` endpoints (Task 8) — covered. Frontend resend-on-403 UX (Task 11) — covered. `/verify-email` page (Task 12) — covered.
- **Rate-limit budget:** flagged and fixed in Task 1 before it could silently break Task 8's tests — see that task's rationale note.
- **Type consistency:** `EmailVerificationService.issueAndSendToken(User)` name matches between Task 6's definition and Task 7's `AuthService.register()` call. `AuthService.register()` return type (`void`) matches `AuthController.register()`'s call site in Task 8. `AuthService` constructor's added `EmailVerificationService` parameter matches both `AuthServiceTest.setUp()` (Task 7) and Spring's auto-wiring in `AuthController`/context (Task 8) — no explicit bean wiring needed since both are `@Service`/`@Component`.
- **DataInitializer:** seeded admin explicitly marked verified in Task 2, otherwise it would be locked out by Task 7's login gate.
- **`AbstractControllerTest.persistUser()`:** deliberately left unmodified — it mints bearer tokens directly (bypassing `/api/auth/login`), so the dozens of other controller test classes across the codebase that depend on it for authentication are unaffected by the new login-time verification gate.
