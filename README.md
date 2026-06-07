# Platforma Druku 3D

A marketplace where users post 3D-printing requests (listings) and printer owners
submit offers. Listings support multiple STL models (interactive 3D preview) and
image attachments.

**Stack:** Spring Boot 3 · PostgreSQL · JWT auth · Angular 21 · three.js

## Prerequisites
- JDK 21, Maven
- Node 20 LTS (the build also runs on 22) + npm
- PostgreSQL 14+ with a database named `3D-JavaApp`

## Configuration
All secrets are read from environment variables with local-dev defaults baked in,
so the app runs out of the box for development. For any shared/production
environment, set real values — see [.env.example](.env.example):

| Variable | Purpose |
|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Token signing key (base64, ≥ 256-bit) — **must** be set in prod |
| `JWT_EXPIRATION` | Token lifetime in ms (default 7 days) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Seeded admin account — **change in prod** |

## Running

Backend (port 8080):
```bash
mvn spring-boot:run
```
On first start a default admin is seeded (credentials logged to the console).

Frontend (port 4200, proxies `/api` to the backend):
```bash
cd frontend
npm install
npm start
```
Open http://localhost:4200

## Roles & admin codes
- A default **admin** account is seeded on startup.
- Admins can generate single-use **admin codes** (Profile → "Kody administratora").
- A code promotes an account to admin — entered at registration or in the profile.
- Admins can delete any listing.

## API overview
- `POST /api/auth/register`, `POST /api/auth/login`
- `GET/POST /api/listings`, `GET/DELETE /api/listings/{id}`
- `GET/POST/DELETE /api/listings/{id}/stl-files[/{fileId}]` — STL + image attachments
- `GET/POST /api/offers`, `PUT /api/offers/{id}/select`
- `POST /api/admin/codes`, `GET /api/admin/codes`, `POST /api/admin/redeem`
