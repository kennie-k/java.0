---
name: smartre-dev
description: Use when developing, debugging, or maintaining the SmartRE Kenya real-estate platform in this repo (smartRE Java microservices + smartRE-front Next.js app) — architecture map, run/build commands, and known gotchas (CSP/image rendering, gateway routing, category casing).
---

# SmartRE Kenya — codebase map & maintenance guide

Two top-level projects in this repo:

- `smartRE/` — Java 21 / Spring Boot 3.4.4 microservices (Maven, one `pom.xml` per service, no parent aggregator pom)
- `smartRE-front/` — Next.js 14.2.5 (App Router), React 18, TypeScript, Tailwind, Zustand

## Backend services (smartRE/)

All services are independent Maven projects. Build each with `mvn -f smartRE/<service>/pom.xml clean package`. Local orchestration is via `smartRE/docker-compose.yml` (app services: user, verification, property, viewing, payment, review, api-gateway) and `docker-compose-infra.yml` (Postgres+pgbouncer per service, Redis, Kafka+Zookeeper, Prometheus/Grafana/Loki/Alertmanager/Promtail).

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | Spring Cloud Gateway — single entry point, JWT `AuthFilter`, rate limiting (Redis), CORS, routes to all services |
| user-service | 8081 | Auth, users, profile, document upload/storage (local disk or S3), agent applications |
| verification-service | 8082 | Seller identity verification, property ownership verification, admin review queues, reports |
| property-service | 8083 | Property listings CRUD/search, consumes verification events (Kafka) to flip trust flags |
| viewing-service | 8084 | Scheduling property viewings, M-Pesa viewing-fee payments |
| payment-service | 8085 | M-Pesa STK push / B2C payments, escrow, revenue, reconciliation |
| review-service | 8086 | Seller ratings/reviews, consumes payment events (Kafka) to unlock reviews |

Cross-service communication: **all client-facing calls go through api-gateway**; services also talk to each other directly for internal-only endpoints (paths under `/api/*/internal/**`, deliberately left open/no-auth in the gateway since they're not meant to be reached from outside the docker network) and via **Kafka** (`PaymentEventPublisher`/`PaymentEventConsumer`, `VerificationEventPublisher`/`VerificationEventConsumer`) for async state propagation (e.g. payment confirmed → review unlocked; verification approved → property trust flags updated).

Every service uses Flyway migrations (`src/main/resources/db/migration`), Postgres via PgBouncer, Spring Data Redis (JWT blacklist, rate limiting), Resilience4j circuit breakers for calls to other services, Actuator + Prometheus metrics, springdoc swagger UI (proxied through the gateway at `/docs/<service>/**`).

### File/document storage (user-service)

`DocumentUploadController` (`user-service/.../controller/DocumentUploadController.java`) handles all uploads at `POST /api/documents/upload` (multipart, `file` + `category`). Storage backend is local-disk by default (`storage.local-dir`, served back at `GET /api/documents/files/**`) or S3 if `s3.enabled=true` (needs real `S3_ACCESS_KEY`/`S3_SECRET_KEY`, fails fast on placeholder creds). The returned `url` is stored **verbatim** wherever the frontend puts it (property `imageUrls`, verification document URLs, etc.) — there's no URL rewriting downstream, so whatever `app.public-url` (local) or `s3.public-base-url` (S3) is set to at upload time is permanent in that record.

**Gotcha:** object keys are `documents/<category.toLowerCase()>/<uuid>.<ext>`. The gateway has a public (unauthenticated) route for `GET /api/documents/files/documents/property_image/**` and `.../profile_image/**` specifically — any other category falls through to the authenticated route. If you add a new "should be publicly viewable" upload category, add its lowercase path to `document-files-public` in `api-gateway/src/main/resources/application.yaml`, or it'll silently require auth.

## Frontend (smartRE-front/)

App Router with three route groups reflecting access level:
- `(public)` — properties list/detail, seller profiles (no auth required)
- `(auth)` — login/register/forgot-password/reset-password
- `(dashboard)` — authenticated user pages (profile, listings, viewings, payments, verification, ownership, agent-application, reviews)
- `(admin)` — admin-only pages (overview, users, manage-listings, verification-queue, agent-applications, revenue, reports)

Auth: cookie-based (httpOnly `sre_token`, sent via axios `withCredentials`). `lib/store.ts` is a Zustand store (`useAuthStore`) persisted to localStorage under key `sre_user` — **the JWT itself is deliberately stripped before it reaches this store** (see comment in the file); only non-sensitive user info is kept. `hooks/useAuthGuard.ts` is the standard per-page guard: redirects to `/login` if not hydrated+authed, or to a fallback route if the role isn't allowed.

`lib/api.ts` is the single axios client + all typed API call wrappers (`propertyApi`, `userApi`, `documentApi`, etc.). Base URL comes from `NEXT_PUBLIC_API_URL` (defaults `http://localhost:8080`, i.e. the gateway). A 401 response triggers a toast + redirect to login and clears the local session, except on the login/register endpoints themselves.

### Images — how they render, and the CSP trap

Two different rendering paths exist and they are **not equivalent**:
1. Plain `next/image` (`PropertyCard.tsx`, `PropertyDetailClient.tsx` gallery) — Next.js rewrites the `<img src>` to same-origin `/_next/image?url=...`, so the browser only ever requests the frontend's own origin. CSP's `img-src 'self'` covers this regardless of what scheme/host the real image lives on.
2. `next/image` with the **`unoptimized`** prop (`components/ui/DocumentThumbnailGrid.tsx`, used on the admin verification-queue and agent-applications pages) — this puts the *raw* remote URL directly into the DOM, bypassing the `/_next/image` proxy entirely. The browser then enforces CSP against that raw URL directly.

The CSP is set in `next.config.js` (`headers()` → `Content-Security-Policy` header). It must explicitly list every origin that any `unoptimized` image (or other raw cross-origin resource) will be loaded from. **This is baked into `.next/routes-manifest.json` at build time** — editing `next.config.js` and running `next start` alone does *nothing*; you must `npm run build` first, and if a stale `next-server` process is still holding port 3000, `pkill -f "next start"` will **not** kill it (the process execs into `next-server`, so its argv no longer contains "next start" — kill by port instead, e.g. `fuser -k 3000/tcp`).

If images silently fail to render (broken image icon, no visible network error, but nothing shows), always check for a `securitypolicyviolation` DOM event / "Refused to load the image... Content Security Policy" console message before assuming it's a backend/network problem — this exact failure mode already happened once (img-src was missing the local API origin, `http://localhost:8080`, that `connect-src` and `images.remotePatterns` already both carved out).

`next.config.js` also controls Next's own image-optimizer allowlist (`images.remotePatterns`) — this is a *separate* mechanism from the CSP header and only matters for the non-`unoptimized` path.

## Running locally

```bash
# backend: from smartRE/
docker compose -f docker-compose-infra.yml up -d   # postgres/pgbouncer/redis/kafka/observability
docker compose up -d                                 # the 7 app services (each waits on its infra deps' healthchecks)

# frontend: from smartRE-front/
npm install
npm run dev      # dev server, picks up next.config.js changes on the fly
npm run build && npm start   # production mode — REQUIRES a build after any next.config.js change
```

Frontend env: `smartRE-front/.env.local` → `NEXT_PUBLIC_API_URL` (gateway URL, default `http://localhost:8080`).

## Conventions worth knowing

- Java DTOs are plain request/response classes per endpoint (`CreatePropertyRequest`, `PropertyResponse`, etc.), not shared between services — no common/shared module exists, each service's package is self-contained (`com.kenyarealestate.<service>`).
- Entities use Lombok `@Builder` with `@Builder.Default` for collection/boolean defaults (see `Property.imageUrls`).
- Category/enum-like strings passed between frontend and backend (upload `category`, document categories) are uppercase on the frontend (`PROPERTY_IMAGE`) and lowercased server-side for storage paths — when tracing a routing or storage bug, check both casings explicitly rather than assuming they match.
- Gateway routes are order-sensitive (first matching predicate wins) — always put more specific `Path` predicates before broader `/**` catch-alls for the same service, matching the existing pattern in `application.yaml`.
