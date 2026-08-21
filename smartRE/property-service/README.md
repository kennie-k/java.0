# property-service

Property listing CRUD, public search, and admin moderation for SmartRE Kenya. Port `8083`.

## Responsibilities

- Create/update/delete property listings (seller/agent owned).
- Public search (`/api/properties/search`) by county, city, type, listing type, keyword,
  price range, bedrooms, and verification status — paginated and sortable.
- Listing lifecycle: `DRAFT` → `PENDING_VERIFICATION` → `ACTIVE` → `SOLD`/`RENTED`/`SUSPENDED`/`WITHDRAWN`,
  driven by seller-identity and property-ownership verification signals.
- Consumes `verification-events` from Kafka (see `kafka/VerificationEventConsumer`) to flip trust
  flags and activate/reactivate listings when verification-service approves a seller or a property.
- Cross-seller duplicate-photo fraud detection (`service/ImageHashService`): every uploaded image
  is hashed (SHA-256) and checked against every other seller's image hashes; a match blocks the
  create/update and is recorded as a `DUPLICATE_PHOTO_FRAUD_DETECTED` audit entry.
- Detail/search results are cached in Redis (see `redis.*` config) with short TTLs, invalidated on
  writes.

## Running locally

See the repo-level `smartRE/docker-compose.yml` / `docker-compose-infra.yml`, or run standalone
with the `local` Spring profile against a local Postgres + Redis + Kafka (see
`src/main/resources/application-local.yaml` for expected local ports/creds).

```bash
mvn -f pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

## Configuration of note

| Property | Purpose |
|---|---|
| `services.internal-secret` | Shared secret required on `X-Internal-Secret` header for `/api/properties/internal/**` (service-to-service only, no user auth). |
| `services.gateway-public-url` / `services.s3-public-base-url` | Allowlisted hosts `ImageHashService` will fetch images from directly for duplicate-photo hashing — anything else is refused (SSRF guard). Must match wherever user-service actually issues public document URLs. |
| `redis.view-debounce-window-minutes` | Debounces the public `GET /api/properties/{id}` view-count increment to at most once per viewer (user id, or IP if anonymous) per property per window. |

## Known gaps (intentionally out of scope for the current fix pass)

- **Geo-radius search**: `latitude`/`longitude` are stored per property but there's no
  "properties within N km of a point" search endpoint yet. Adding it properly needs a
  Haversine-based native query (or PostGIS), a new DTO/controller param set, and a supporting
  index — a small enough change to be worth its own pass rather than folding into an unrelated fix.

## Tests

`src/test/java/.../service/PropertyServiceTest.java` — Mockito-based unit tests covering
ownership authorization, create-status branching, search parameter mapping, seller-wide
activation/suspension, ownership/duplicate-parcel handling, admin actions, view-count debounce,
and the duplicate-photo fraud check. No integration/`@SpringBootTest` tests exist yet (would need
Testcontainers for Postgres/Redis/Kafka).
