# Messaging & Order Tracking — Design Spec

## Overview

Two features for the 3D print marketplace:
1. **Direct messaging** between listing owner and offer makers, scoped per listing
2. **Order status tracking** after offer is accepted, with carrier/tracking number support

## Decisions

- Messaging: per-listing conversation threads (one conversation per user per listing)
- Delivery: poll-based (no WebSocket), 10s polling in conversation, 30s for navbar badge
- Order stages: PRINTING → SHIPPED → DELIVERED with carrier name + tracking number at SHIPPED
- UI: dedicated messaging page `/wiadomosci`, order tracking on existing `/moje-zlecenia`
- Architecture: Conversation entity (Option A) for clean queries

---

## Database Schema

### `conversations`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| listing_id | UUID | FK → listings, NOT NULL |
| participant1_id | UUID | FK → users, NOT NULL (listing owner) |
| participant2_id | UUID | FK → users, NOT NULL (interested party) |
| created_at | timestamp | NOT NULL, default now |

- Unique constraint: `(listing_id, participant2_id)`

### `messages`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| conversation_id | UUID | FK → conversations, NOT NULL |
| sender_id | UUID | FK → users, NOT NULL |
| content | text | NOT NULL, max 2000 chars |
| read | boolean | NOT NULL, default false |
| created_at | timestamp | NOT NULL, default now |

### `order_tracking`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| offer_id | UUID | FK → offers, UNIQUE, NOT NULL |
| carrier_name | varchar(100) | nullable |
| tracking_number | varchar(100) | nullable |
| shipped_at | timestamp | nullable |
| delivered_at | timestamp | nullable |
| created_at | timestamp | NOT NULL, default now |

### Enum changes

Extend `OfferStatus`: `PENDING, SELECTED, REJECTED, PAID, PRINTING, SHIPPED, DELIVERED`

---

## API Endpoints

### Messaging

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/conversations` | yes | Create or get existing conversation. Body: `{ listingId }`. participant1 = listing owner, participant2 = caller |
| GET | `/api/conversations` | yes | List all conversations for current user. Returns: listing title, other participant email, last message preview, unread count. Sorted by latest message |
| GET | `/api/conversations/{id}/messages` | yes | Messages in conversation (paginated, oldest first). Only participants can access |
| POST | `/api/conversations/{id}/messages` | yes | Send message. Body: `{ content }`. Only participants |
| PUT | `/api/conversations/{id}/read` | yes | Mark all messages as read for current user |
| GET | `/api/conversations/unread-count` | yes | Total unread count across all conversations (navbar badge) |

### Order Tracking

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| PUT | `/api/offers/{id}/status` | yes | Update offer status. Body: `{ status }`. Seller only. Valid: SELECTED→PRINTING→SHIPPED→DELIVERED |
| PUT | `/api/offers/{id}/tracking` | yes | Set carrier + tracking number. Body: `{ carrierName, trackingNumber }`. Seller only, SHIPPED status required |
| GET | `/api/offers/{id}/tracking` | yes | Get tracking info. Buyer and seller can access |

### Status Transition Rules

- Only seller (offer maker) advances: SELECTED → PRINTING → SHIPPED → DELIVERED
- Buyer (listing owner) can confirm DELIVERED
- No backward transitions
- SHIPPED requires carrierName + trackingNumber
- OrderTracking row created automatically when status moves to PRINTING

---

## Frontend UI

### Messaging Page — `/wiadomosci`

**Conversation list (left panel):**
- All conversations, sorted by latest message
- Each item: listing title, other user's email, last message snippet, timestamp
- Unread: bold text + blue dot
- Click opens conversation

**Conversation view (right panel):**
- Header: listing title (link to listing), other user's email
- Message bubbles: own = right-aligned blue, other = left-aligned gray
- Input bar at bottom with send button
- Auto-scroll to newest
- Poll every 10s for new messages

**Navbar:**
- "Wiadomości" link with unread count badge
- Badge polls every 30s

### Offer Card Actions — Listing Detail Page

On each offer card:

**If current user is listing owner:**
- "Akceptuj ofertę" button (green) — selects offer
- "Napisz wiadomość" button (outline) — creates/opens conversation with offer maker

**If current user is offer maker:**
- "Napisz wiadomość" button — opens conversation with listing owner

**Otherwise:** no action buttons

### Order Tracking — `/moje-zlecenia`

**Seller view (offer maker):**
- Status timeline: PRINTING → SHIPPED → DELIVERED
- Current step highlighted, completed steps checked
- "Rozpocznij druk" button → PRINTING
- "Oznacz jako wysłane" → form: carrier dropdown (DPD, InPost, Poczta Polska, Kurier, Inne) + tracking number input
- "Dostarczono" → DELIVERED

**Buyer view (listing owner):**
- Same timeline, read-only
- Shows carrier + tracking number when available (copyable)
- "Potwierdź odbiór" button → confirms DELIVERED

### Entry Points

- "Napisz wiadomość" on offer cards in listing detail
- "Wiadomości" in navbar (with badge)
- Order tracking visible on My Orders page for accepted offers

---

## Styling

Follows existing design system:
- Outfit font
- Blue gradient accents
- Consistent card patterns
- Responsive (mobile drawer for conversation list)

---

## Polling Strategy

| What | Interval | When |
|------|----------|------|
| Messages in open conversation | 10s | While conversation view is active |
| Unread count (navbar) | 30s | While logged in, any page |
| Order status | Manual refresh / page load | On My Orders page |
