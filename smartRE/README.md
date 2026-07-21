# SmartRE Kenya — Smart Real Estate Platform

7 Spring Boot microservices + Spring Cloud Gateway + PostgreSQL.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation, routing, CORS |
| user-service | 8081 | Registration, login, profiles |
| verification-service | 8082 | Identity + property ownership trust engine |
| property-service | 8083 | Listings, search, trust flag management |
| viewing-service | 8084 | Viewing scheduling and confirmation |
| payment-service | 8085 | M-Pesa Daraja STK Push + escrow |
| review-service | 8086 | Post-transaction reviews and ratings |

---

## How requests flow

Every request in production goes through the gateway:

```
Client → API Gateway :8080 → Service :808X → Postgres DB
```

The gateway:
1. Validates the JWT signature
2. Extracts email, role, userId from the token
3. Injects X-Auth-Email, X-Auth-Role, X-Auth-UserId headers
4. Routes to the correct service

Each downstream service trusts those injected headers.
For open endpoints (search, trust-status, M-Pesa callback) the gateway
forwards without requiring a token.

---

## Quick Start

```bash
unzip smart-real-estate-system.zip && cd smartre
cp .env.example .env
# Edit .env with your Daraja credentials if testing M-Pesa

docker-compose up --build
# First build: ~5-8 min  |  Subsequent: ~30 sec per changed service
```

---

## Testing — via the Gateway (recommended)

All requests go through port 8080. This tests the real production flow.

### Step 1 — Register and get a token
```
POST http://localhost:8080/api/auth/register
{
  "fullName": "Alice Seller",
  "email": "alice@test.com",
  "password": "password123",
  "role": "SELLER"
}

POST http://localhost:8080/api/auth/login
{ "email": "alice@test.com", "password": "password123" }
```
Copy the `token` from the login response.

### Step 2 — Call any endpoint through the gateway
```
Authorization: Bearer <token>

GET  http://localhost:8080/api/users/me
POST http://localhost:8080/api/verification/identity/start
POST http://localhost:8080/api/properties
GET  http://localhost:8080/api/properties/search?county=Nairobi
POST http://localhost:8080/api/viewings
POST http://localhost:8080/api/payments/initiate
POST http://localhost:8080/api/reviews
```

### Step 3 — Public endpoints (no token needed)
```
GET  http://localhost:8080/api/properties/search
GET  http://localhost:8080/api/properties/{id}
GET  http://localhost:8080/api/verification/trust-status/{userId}
GET  http://localhost:8080/api/reviews/seller/{sellerId}/rating
```

---

## Swagger UIs via the Gateway

Each service's Swagger UI is accessible through the gateway.
This lets you test the real auth flow (gateway → service) from Swagger.

| Service | Swagger URL via Gateway |
|---|---|
| User Service | http://localhost:8080/docs/user-service/swagger-ui.html |
| Verification Service | http://localhost:8080/docs/verification-service/swagger-ui.html |
| Property Service | http://localhost:8080/docs/property-service/swagger-ui.html |
| Viewing Service | http://localhost:8080/docs/viewing-service/swagger-ui.html |
| Payment Service | http://localhost:8080/docs/payment-service/swagger-ui.html |
| Review Service | http://localhost:8080/docs/review-service/swagger-ui.html |

**How to use:**
1. Open any Swagger UI above
2. Call POST /api/auth/register then POST /api/auth/login
3. Copy the token
4. Click Authorize → paste token → Authorize
5. All calls now go through the gateway with real JWT validation

## Direct Service Swagger UIs (bypass gateway — development only)

| Service | Direct URL |
|---|---|
| User | http://localhost:8081/swagger-ui.html |
| Verification | http://localhost:8082/swagger-ui.html |
| Property | http://localhost:8083/swagger-ui.html |
| Viewing | http://localhost:8084/swagger-ui.html |
| Payment | http://localhost:8085/swagger-ui.html |
| Review | http://localhost:8086/swagger-ui.html |

---

## Full End-to-End Test Sequence (all via gateway port 8080)

```
# 1. Register seller
POST /api/auth/register  { role: "SELLER" }

# 2. Register buyer (separate call)
POST /api/auth/register  { role: "BUYER" }

# 3. Login as seller — save seller_token
POST /api/auth/login

# 4. Start identity verification (seller_token)
POST /api/verification/identity/start

# 5. Upload 4 documents (seller_token) — use any public HTTPS image URLs
POST /api/verification/identity/documents  { documentCategory: "NATIONAL_ID_FRONT", documentUrl: "..." }
POST /api/verification/identity/documents  { documentCategory: "NATIONAL_ID_BACK",  documentUrl: "..." }
POST /api/verification/identity/documents  { documentCategory: "KRA_PIN_CERTIFICATE", documentUrl: "..." }
POST /api/verification/identity/documents  { documentCategory: "SELFIE_WITH_ID",    documentUrl: "..." }

# 6. Submit for review
POST /api/verification/identity/submit

# 7. Simulate AI screening for each document (open endpoint — no token needed)
POST /api/verification/identity/internal/{verificationId}/ai-screening
{ "documentId": "...", "aiAuthenticityScore": 92, "aiTamperDetected": false,
  "aiMetadataClean": true, "aiFontConsistency": true,
  "aiSignatureDetected": true, "aiSealDetected": false }
# Repeat for each document. After the 4th call with score >= 85,
# verification auto-approves and property-service is notified.

# 8. Create a listing (seller_token)
POST /api/properties
{ "title": "3BR House Kilimani", "propertyType": "HOUSE",
  "listingType": "SALE", "county": "Nairobi", "price": 12000000,
  "bedrooms": 3, "bathrooms": 2 }

# 9. Login as buyer — save buyer_token
POST /api/auth/login

# 10. Search listings (no token)
GET /api/properties/search?county=Nairobi

# 11. Check seller trust status (no token)
GET /api/verification/trust-status/{sellerId}

# 12. Schedule viewing (buyer_token)
POST /api/viewings
{ "propertyId": "...", "sellerId": "...", "scheduledAt": "2026-08-01T10:00:00" }

# 13. Seller confirms (seller_token)
PUT /api/viewings/{id}/confirm-seller

# 14. Buyer confirms (buyer_token)
PUT /api/viewings/{id}/confirm-buyer

# 15. Mark complete (seller_token or admin)
PUT /api/viewings/{id}/complete

# 16. Initiate payment (buyer_token) — needs real Daraja for STK push
POST /api/payments/initiate
{ "propertyId": "...", "sellerId": "...", "amount": 500000,
  "phoneNumber": "254708374149", "paymentType": "DEPOSIT" }

# 17. Post review (buyer_token) — after payment COMPLETED
POST /api/reviews
{ "sellerId": "...", "propertyId": "...", "paymentId": "...",
  "rating": 5, "comment": "Excellent seller, very responsive." }

# 18. Check seller rating (no token)
GET /api/reviews/seller/{sellerId}/rating
```

---

## Local Development (databases in Docker, services in IDE)

```bash
# Start only databases
docker-compose up user-db verification-db property-db viewing-db payment-db review-db

# Then run each service from IntelliJ/VS Code
# Set environment variables in the run config:

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/<service_db>
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_SECRET=superSecretRealEstatePlatformKey2026MakeItAtLeast64CharactersLongForSecurity
```

For verification-service also add:
```
PROPERTY_SERVICE_URL=http://localhost:8083
DOCUMENT_ANALYSIS_ENABLED=false
SMILE_IDENTITY_ENABLED=false
ARDHISASA_ENABLED=false
BIOMETRIC_HARD_BLOCK=false
```

---

## M-Pesa Sandbox Testing

1. Register at developer.safaricom.co.ke
2. Create an app, subscribe to Lipa Na M-Pesa Online
3. Run: `ngrok http 8080`
4. Set in `.env`:
   ```
   MPESA_CONSUMER_KEY=<your key>
   MPESA_CONSUMER_SECRET=<your secret>
   MPESA_PASSKEY=<your passkey>
   MPESA_CALLBACK_URL=https://<ngrok-url>/api/payments/mpesa/callback
   ```
5. Test phone: `254708374149` always succeeds in sandbox
