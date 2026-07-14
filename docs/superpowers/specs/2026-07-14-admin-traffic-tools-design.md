# Admin Traffic & Moderation Tools — Design Spec

**Date:** 2026-07-14
**Status:** Approved

---

## Overview

First of three planned admin/marketplace additions (search, ratings, admin tools — this spec covers admin tools only). Expands the admin panel from its current state (list listings, list users, generate/redeem admin codes) with: traffic analytics (page views + API request stats), a revenue overview, user suspension, listing moderation (hide without delete), and an admin action audit log.

No existing request-tracking or analytics infrastructure exists in the codebase today (no Spring Actuator, no interceptor/filter that logs requests). This spec introduces the minimum needed for the current traffic scale — a small marketplace, not high-QPS — favoring direct synchronous writes over queues/async pipelines.

---

## Data model

### New table: `api_request_log` (entity `ApiRequestLog`)

| column | type | notes |
|---|---|---|
| id | UUID | PK |
| method | varchar | GET/POST/etc |
| path | varchar | request URI, no query string |
| status | int | HTTP response status |
| duration_ms | int | wall time for the request |
| user_id | UUID, nullable | resolved principal, if authenticated |
| ip | varchar | client IP |
| created_at | timestamp | |

Written once per `/api/**` request by a new `OncePerRequestFilter`. The filter must **not** log requests to `/api/analytics/**` or `/api/admin/traffic**` themselves, to avoid a self-referential noise loop.

### New table: `page_view` (entity `PageView`)

| column | type | notes |
|---|---|---|
| id | UUID | PK |
| path | varchar | Angular route path |
| user_id | UUID, nullable | if logged in |
| session_id | varchar | client-generated, stored in sessionStorage |
| referrer | varchar, nullable | |
| created_at | timestamp | |

Written by a new unauthenticated `POST /api/analytics/pageview`, called from a router-event subscriber in the Angular app (fire-and-forget; failures are swallowed, never block navigation). Since this endpoint is unauthenticated and public, it must be covered by a per-IP rate limit — reuse the existing `AuthRateLimitFilter` pattern rather than inventing a new limiter.

### New table: `admin_action` (entity `AdminAction`, audit log)

| column | type | notes |
|---|---|---|
| id | UUID | PK |
| admin_id | UUID | FK to users |
| admin_email | varchar | denormalized, survives if the admin account is later altered |
| action_type | enum | `DELETE_LISTING`, `BAN_USER`, `UNBAN_USER`, `HIDE_LISTING`, `UNHIDE_LISTING` |
| target_type | varchar | e.g. `"Listing"`, `"User"` |
| target_id | UUID | |
| details | text, nullable | free-form context |
| created_at | timestamp | |

Written by a new `AdminAuditService.log(...)`, called from every admin action, including the **existing** admin-path `deleteListing` in `ListingController` (currently unaudited) and admin-code redemption.

### `User` — add `suspended` (boolean, default false)

A suspended user is rejected at:
- Login (`AuthController`) — reject with a clear error before issuing a token.
- **Every subsequent request** with an already-issued JWT — `JwtAuthFilter` must re-check the current `suspended` flag per-request (not just at login), otherwise a ban has no effect until the existing token naturally expires. Reuses the existing `UserDetails.isEnabled()` / `isAccountNonLocked()` hooks (currently hardcoded `true`) rather than adding new state to check.

### `Listing` — add `moderationStatus` (enum: `VISIBLE`, `HIDDEN`, default `VISIBLE`)

Orthogonal to the existing business `ListingStatus` (`OPEN`/`CLOSED`/`AWARDED`) — a listing can be `OPEN` and `HIDDEN` at the same time (admin hid an active listing pending review). The public listing feed (`ListingController.getOpenListings`) filters `moderationStatus = VISIBLE`; the owner's own view and the admin panel still show hidden listings, tagged with a badge.

---

## Endpoints

All under the existing admin-only guard pattern (`/api/admin/**`, role check as already used in `AdminController`).

- `GET /api/admin/traffic?range=7d|30d` → page-view counts by day + top paths; API error-rate and average latency summary from `ApiRequestLog`.
- `GET /api/admin/revenue?range=7d|30d` → sums/counts from the existing `Payment` entity (`platformFee`, `totalPrice`) grouped by day and by `status`. No new table — this is a read-only aggregation query.
- `PUT /api/admin/users/{id}/suspend` / `PUT /api/admin/users/{id}/unsuspend` → toggles `User.suspended`, writes an `AdminAction`.
- `PUT /api/admin/listings/{id}/hide` / `PUT /api/admin/listings/{id}/unhide` → toggles `Listing.moderationStatus`, writes an `AdminAction`.
- `GET /api/admin/audit-log?page=&size=` → paged `AdminAction` list, newest first (mirrors the existing `PageResponse<T>` pattern used by `ListingController`).
- `POST /api/analytics/pageview` (public, unauthenticated, rate-limited) → records one `PageView` row.

---

## Frontend (`admin-panel.component.ts`)

The component is already tab-oriented (profile / listings / users / codes, all backed by signals + plain `HttpClient` calls — no NgRx or similar). Extend with the same pattern:

- **Traffic tab** — calls `GET /api/admin/traffic`; renders day-by-day page views and top-paths as a simple bar/sparkline. Check whether a charting approach already exists elsewhere in the app before adding a new one; if none exists, use the `dataviz` skill's guidance for a minimal, theme-aware chart rather than pulling in a charting library.
- **Revenue tab** — calls `GET /api/admin/revenue`; same visual treatment as Traffic.
- **Audit Log tab** — calls `GET /api/admin/audit-log`; plain paged table (admin email, action, target, timestamp), no chart needed.
- **Users tab** — add a Suspend/Unsuspend button per row (same confirm-then-act pattern already used for `deleteListing`'s `confirmDeleteId`).
- **Listings tab** — add a Hide/Unhide button per row, same confirm pattern; hidden listings get a visual badge in the admin table.

---

## Error handling & edge cases

- Analytics filter/endpoint failures must never surface to the end user or block the request/navigation they're attached to — logging is best-effort.
- `ApiRequestLog`/`PageView` writes happen on the request thread (per the recommended synchronous approach) — acceptable at current traffic level; if this ever needs to change, that's a future revisit, not part of this spec.
- Suspending an admin's own account: allowed (no special-case guard) — consistent with how `deleteListing`'s owner/admin check already has no self-protection carve-out elsewhere in this codebase.
- Re-hiding an already-hidden listing / re-suspending an already-suspended user: idempotent, no error, still writes an audit row (accurate history of admin intent, even if repeated).
- No retention/cleanup job for `api_request_log` / `page_view` in this pass (explicitly out of scope below) — acceptable at current data volume; revisit if table growth becomes a problem.

---

## Testing

Consistent with the codebase's existing conventions:

- Backend: JUnit tests for `AdminService` additions (suspend/unsuspend, hide/unhide, audit writes) and the new `ApiRequestLogFilter`, following the existing `*ServiceTest`/`*ControllerTest` style.
- Frontend: `admin-panel.component.spec.ts` extended for the new tabs/buttons; a new `analytics.service.spec.ts` if a dedicated Angular service is introduced for the pageview beacon (matching the existing one-service-per-concern pattern, e.g. `theme.service.ts`).

---

## What is NOT in scope

- Listing search improvements and the ratings subsystem — separate specs, separate build order (this is admin-tools only).
- Async/queued log writes, batch flushing, or a separate rollup/aggregation job — explicitly deferred per the "right-sized for current scale" decision; the leaner aggregated-counters approach and the full async-pipeline approach were both considered and rejected as respectively too coarse and premature.
- Data retention/pruning jobs for the new log tables.
- Geo-IP lookup, device/browser detection, or any analytics beyond path/day/count.
- IP-based banning or shadow-banning — suspension is account-level only.
- Any change to the existing hard-delete listing/user flows — hide/suspend are additive, not replacements.
