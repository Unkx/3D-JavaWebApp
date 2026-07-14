# Seller/Customer Ratings — Design Spec

**Date:** 2026-07-14
**Status:** Approved

---

## Overview

Second of three planned marketplace additions (admin tools — shipped; ratings — this spec; listing search — still queued). Adds bidirectional post-delivery ratings: once an `Offer` reaches `DELIVERED`, both parties (the listing owner/customer and the offer's contractor/seller) can each leave a 1–5 star rating with an optional comment about the other. Averages are shown on user profiles, listing cards, and offer cards. Admins can hide abusive ratings, reusing the moderation pattern already built for listings.

---

## Data model

### New table: `rating` (entity `Rating`)

| column | type | notes |
|---|---|---|
| id | UUID | PK |
| offer_id | UUID | FK to `offers` |
| rater_id | UUID | FK to `users` — who wrote it |
| rated_user_id | UUID | FK to `users` — who it's about |
| stars | int | 1–5, required |
| comment | varchar(500), nullable | optional free text |
| moderation_status | enum (VISIBLE, HIDDEN) | default VISIBLE, `@ColumnDefault("'VISIBLE'")` per the `ddl-auto=update` lesson from the admin-tools work |
| created_at | timestamp | |

**Unique constraint on `(offer_id, rater_id)`** — one rating per person per offer. Since buyer and seller have different `rater_id`s on the same offer, this single constraint naturally allows exactly one rating in each direction without a separate "direction" column.

No denormalized average column on `User` — averages are computed on read (fetch a user's visible ratings, average in Java), matching the "aggregate on read, no cached column" approach already used for the traffic/revenue dashboards. Rejected caching an average on `User` as premature: no measured read-heavy pressure at this data volume, and it adds transactional-update-on-every-rating-write complexity for no current benefit.

### `AdminActionType` — extend with two values

Add `HIDE_RATING`, `UNHIDE_RATING` alongside the existing five, following the exact pattern already used for listing moderation (`AdminAuditService.log(...)` called from the new hide/unhide endpoints).

---

## Eligibility rules

- A rating may only be created once the associated `Offer.status == DELIVERED`.
- The rater must be either the offer's `user` (the seller/contractor who made the offer) or the offer's `listing.user` (the customer who posted the listing) — no one else.
- `rated_user_id` is inferred automatically as "the other party" — never taken from client input.
- **Self-offer guard:** if `listing.user.id == offer.user.id` (a user offering on their own listing), reject rating creation entirely — defensive, even if this is already prevented upstream at offer-creation time.
- Attempting a second rating for the same `(offer, rater)` pair → `400 Bad Request` ("already rated this order").

---

## Endpoints

- `POST /api/offers/{offerId}/ratings` (authenticated) → body `{ stars: number, comment?: string }`. Validates eligibility, infers `ratedUserId`, creates the row. `stars` validated `@Min(1) @Max(5)`.
- `GET /api/offers/{offerId}/ratings` (authenticated, must be a party to the offer) → both ratings for that offer, if they exist — drives "already rated" / "waiting on them" UI in My Orders. Returns an empty/partial list if one or both sides haven't rated yet.
- `GET /api/users/{userId}/ratings?page=&size=` (public, no auth) → paged list of *visible* ratings received by that user, newest first, plus the average and count — used by profile, listing cards, and offer cards. Uses the existing `PageResponse<T>` envelope.
- `PUT /api/admin/ratings/{id}/hide` / `PUT /api/admin/ratings/{id}/unhide` (admin only) → toggles `moderationStatus`, writes an `AdminAction` via the existing `AdminAuditService`.
- `GET /api/admin/ratings?page=&size=` (admin only) → all ratings (visible and hidden) for the moderation list, newest first.

---

## Frontend

- **My Orders** (`my-orders.component.ts`/`.html`): a "Oceń" (Rate) button appears on offers in `DELIVERED` status, in both the listings tab (customer rating the seller) and the offers tab (seller rating the customer). Clicking opens an inline star-picker (1–5) + optional comment textarea, submits via the new endpoint. Once submitted for that offer/rater pair, the button is replaced with a "read-only" confirmation state; if the other party hasn't rated yet, a subtle "czeka na ocenę drugiej strony" note shows (informational only, not blocking).
- **User panel** (`user-panel.component.ts`/`.html`): a new "Oceny" card showing average stars + count + the paged list of received reviews, matching the existing card-grid pattern.
- **Admin panel** (`admin-panel.component.ts`/`.html`): a new "Oceny" card listing all ratings (stars, comment, rater→rated pair, date, visibility) with hide/unhide buttons — same list-plus-action pattern as the existing Listings card, including the confirm-step convention established for suspend/hide in the admin-tools work.

**Scope note (revised during planning):** the counterpart's name/rating badge is added to My Orders rows only, not to the browse-grid listing cards or the listing-detail offer list. Investigation during planning found that *no* page in the current frontend displays the other party's name anywhere (listing cards and listing-detail only use `user.id` internally for permission checks, never render it) — adding badges there would mean designing and shipping new owner/offerer-name UI first, which is scope growth beyond a ratings feature. My Orders already renders per-offer/per-listing rows for both roles, making it the natural, lowest-scope home for the badge; the browse grid and offer list can get owner-name display (and then a badge) as a future, separately-scoped UI pass.

---

## Error handling & edge cases

- Rating an offer not yet `DELIVERED` → `400 Bad Request`.
- Rating by someone who isn't a party to the offer → `403 Forbidden`.
- Duplicate `(offer, rater)` rating → `400 Bad Request`.
- Hidden ratings are excluded from both the average calculation and the public `GET /api/users/{userId}/ratings` list, but remain visible (with their hidden state marked) in the admin moderation list.
- A user with zero visible ratings shows "Brak ocen" (no ratings yet) rather than a 0.0 average, both on the profile card and on listing/offer badges.
- `comment` is optional — a rating can be stars-only.

---

## Testing

Consistent with the codebase's existing conventions: JUnit service/controller tests for the new `RatingService`/`RatingController`/admin endpoints (matching `AdminServiceTest`/`AdminControllerTest` style — mocked-repository unit tests plus H2-backed `AbstractControllerTest` integration tests), and `*.component.spec.ts` additions for My Orders / User Panel / Admin Panel using the existing `HttpTestingController` pattern.

---

## What is NOT in scope

- Listing search improvements — separate spec, separate build order.
- Replies to ratings, flagging/reporting by other users (only admin-initiated hide), or edit/delete by the rating's author — ratings are one-time and locked in once submitted, per the approved design decision.
- A dedicated public "view another user's full profile" page — the rating badge attaches to My Orders rows instead.
- Rating badges on the browse-grid listing cards or the listing-detail offer list — neither currently displays the counterpart's name at all, so adding a badge there is a separate future UI pass, not part of this feature.
- Denormalized/cached average-rating storage or a scheduled recomputation job.
- Rating anything other than a `DELIVERED` offer (e.g. no rating on `REJECTED` or abandoned offers).
