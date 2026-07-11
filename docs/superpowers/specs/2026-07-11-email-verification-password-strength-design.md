# Email Verification & Password Strength — Design Spec

**Date:** 2026-07-11
**Status:** Approved

---

## Overview

Site is now live to real users. Two hardening changes to registration:

1. New accounts must confirm ownership of their email before they can log in.
2. New account passwords must meet a minimum strength bar (currently only a 6-char length check).

Builds directly on the existing `PasswordResetToken` / `EmailService` pattern from [password reset](2026-06-28-password-reset-design.md), which explicitly left "email verification on registration" out of scope.

---

## Backend

### `User` entity change

Add field:
```
emailVerified   BOOLEAN NOT NULL DEFAULT false
```

`isEnabled()` (UserDetails override) stays hardcoded `true` — verification gates login explicitly in `AuthService.login()`, not via Spring Security's enabled/disabled mechanism, to keep the existing 403 error-shape pattern (see `EMAIL_NOT_VERIFIED` below) rather than Spring's generic `DisabledException`.

**Backfill:** existing rows get `emailVerified = false` from the Hibernate `ddl-auto=update` column add. A one-time `UPDATE users SET email_verified = true WHERE email_verified = false;` must run once after deploy, before traffic hits the new column default — otherwise every pre-existing user is locked out on next login. This is a manual SQL step (no Flyway/Liquibase in this project), run directly against the Render/Postgres instance.

### New entity: `EmailVerificationToken`

Same shape as `PasswordResetToken`:
```
id          UUID (PK)
user        FK → users.id
token       UUID (unique, indexed)
expiresAt   TIMESTAMP
used        BOOLEAN DEFAULT false
createdAt   TIMESTAMP
```

Expires **24 hours** after creation (vs. password reset's 1 hour — lower urgency, no account-takeover risk). Single-use. Old unused tokens for the same user are invalidated when a new one is issued (same pattern as password reset).

### New files

| File | Purpose |
|------|---------|
| `model/EmailVerificationToken.java` | JPA entity |
| `repository/EmailVerificationTokenRepository.java` | `findByToken`, `deleteByUser` |
| `dto/ResendVerificationRequest.java` | `{ email }` |

### Modified files

| File | Change |
|------|--------|
| `model/User.java` | Add `emailVerified` field + getter/setter |
| `dto/RegisterRequest.java` | Password `@Size(min = 8)` + `@Pattern` (upper+lower+digit) |
| `service/AuthService.java` | `register()` sets `emailVerified=false`, issues token, sends email; `login()` checks `emailVerified` before issuing JWT; `createGoogleUser()`/`createFacebookUser()` set `emailVerified=true` |
| `service/EmailService.java` | Add `sendVerificationEmail(to, token)` |
| `controller/AuthController.java` | Add `GET /api/auth/verify-email`, `POST /api/auth/resend-verification` |
| `security/SecurityConfig.java` | Permit new endpoints |

### Password rule

Regex: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$` combined with `@Size(min = 8)`.

```java
@NotBlank(message = "Hasło jest wymagane")
@Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków")
@Pattern(
    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
    message = "Hasło musi zawierać wielką literę, małą literę i cyfrę"
)
private String password;
```

Applies to registration only. Login form / `LoginRequest` unchanged (must still accept old 6-char passwords already stored). Password-reset's `ResetPasswordRequest` gets the same new rule, since a reset is effectively setting a new password.

### Endpoints

#### `POST /api/auth/register` (existing, modified)
- On success: creates user with `emailVerified=false`, issues `EmailVerificationToken`, sends verification email
- Returns 201 as before — **no JWT issued** (registration no longer logs the user in immediately, since they can't pass the verification gate yet)
- Frontend must treat register success as "check your email," not as an auto-login

#### `POST /api/auth/login` (existing, modified)
- After password check succeeds, before issuing JWT: if `!user.emailVerified` → 403 with body `{ "error": "EMAIL_NOT_VERIFIED", "message": "Potwierdź adres email, aby się zalogować." }`
- OAuth logins (`/facebook`, `/google`) unaffected — those accounts are always `emailVerified=true`

#### `GET /api/auth/verify-email?token={uuid}`
- Public
- Validates token exists, not used, not expired
- On success: sets `user.emailVerified=true`, marks token used, returns 200
- On failure: 400 `{ "error": "INVALID_TOKEN", "message": "Link wygasł lub jest nieprawidłowy." }`

#### `POST /api/auth/resend-verification`
- Public
- Body: `{ "email": "user@example.com" }`
- **Always returns 200** (anti-enumeration, same as forgot-password)
- If user exists and `!emailVerified`: invalidate old tokens, issue new one, send email
- If user doesn't exist or already verified: no-op, still 200
- Rate-limited by existing `AuthRateLimitFilter` (same bucket as login/forgot-password)

### Security properties

- Verification token: UUID v4 (122 bits entropy), single-use, 24h expiry, old tokens invalidated on reissue
- Email enumeration prevented on resend-verification (always 200)
- OAuth signups trusted as pre-verified: Google flow already rejects tokens where `email_verified != true` at the provider level (`GoogleAuthClient.verify()`); Facebook flow only returns confirmed emails from the Graph API (existing trust boundary, documented in `AuthService` comments)

---

## Frontend

### Modified: `auth.component.ts` / `.html`

**Register flow:**
- On successful register response: show "Sprawdź swoją skrzynkę email, aby potwierdzić konto." instead of auto-navigating in as logged-in
- `registerForm.password`: add `Validators.minLength(8)` + custom validator function checking upper/lower/digit, matching backend regex
- Password field error messages: add cases for missing uppercase/lowercase/digit (Polish, matching existing error-message style)

**Login flow:**
- On 403 with `error: 'EMAIL_NOT_VERIFIED'`: show message + "Wyślij ponownie link weryfikacyjny" button
- Button calls new `authService.resendVerification(email)`, shows "Wysłano ponownie." on response (always success per backend contract)

### New route + component: `/verify-email`

`VerifyEmailComponent` (standalone, lazy-loaded), modeled on `ResetPasswordComponent`:
- Reads `?token=` from query params on init, calls `GET /api/auth/verify-email` immediately
- Success: "Konto potwierdzone!" + button "Zaloguj się" → `/auth`
- Failure: "Link wygasł lub jest nieprawidłowy." + button "Wyślij nowy link" (prompts for email, calls resend-verification)
- No token in URL: same failure state

### New service methods in `auth.service.ts`

```typescript
verifyEmail(token: string): Observable<void>
resendVerification(email: string): Observable<void>
```

---

## Email template

Plain text, mirrors password-reset template:

```
Cześć,

Dziękujemy za rejestrację w serwisie Druk3D.

Kliknij poniższy link, aby potwierdzić swój adres email (ważny przez 24 godziny):

{APP_BASE_URL}/verify-email?token={token}

Jeśli to nie Ty zakładałeś/-aś to konto, zignoruj tę wiadomość.

— Zespół Druk3D
```

---

## Rollout sequence

1. Deploy backend + frontend together (register/login contract changes are breaking if split)
2. Immediately after deploy, run the one-time backfill SQL against production Postgres (see backfill note above) — otherwise all existing users are locked out on next login attempt
3. Verify with one throwaway registration end-to-end (register → receive email → click link → login succeeds)

---

## What is NOT in scope

- Rate-limiting resend-verification beyond the existing `AuthRateLimitFilter` bucket
- CAPTCHA on registration
- Disposable/temporary email domain blocking
- HTML email templates (plain text, consistent with password reset)
- Password strength meter/indicator in the UI (validation messages only)
