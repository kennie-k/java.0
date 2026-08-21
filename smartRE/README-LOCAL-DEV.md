# SmartRE Kenya — Local Development Guide

Two modes. Same codebase. No code changes between them.

---

## MODE 1 — Run in IntelliJ (individual services)

### Prerequisites running locally
- PostgreSQL 18 on localhost:5432 with the password set as `DB_PASSWORD` in your `.env` file
- Redis on localhost:6379
- Kafka on localhost:29092

Copy `.env.example` to `.env` and set `DB_PASSWORD` before starting infra — it's required
(no hardcoded default) and shared by all six local Postgres containers below:

```bash
cp .env.example .env   # then edit DB_PASSWORD (and any other values you need)
docker-compose -f docker-compose-infra.yml up -d
```

Note: `docker-compose-infra.yml` binds Postgres/Redis/Kafka ports to `127.0.0.1` only —
they are not reachable from outside the machine, matching the posture of `docker-compose.yml`.

### Run a service in IntelliJ

1. Open the service in IntelliJ
2. Go to Run → Edit Configurations
3. Add environment variable: `SPRING_PROFILES_ACTIVE=local`
4. Run the service

The `local` profile activates `application-local.yaml` which points to localhost instead of Docker container names.

### Service ports

| Service              | Port | Database        |
|----------------------|------|-----------------|
| api-gateway          | 8080 | —               |
| user-service         | 8081 | user_db         |
| verification-service | 8082 | verification_db |
| property-service     | 8083 | property_db     |
| viewing-service      | 8084 | viewing_db      |
| payment-service      | 8085 | payment_db      |
| review-service       | 8086 | review_db       |

All databases on localhost:5432. Password: value of `DB_PASSWORD` in your `.env` file.

### Start order (to avoid startup errors)
1. user-service
2. verification-service
3. property-service
4. payment-service
5. viewing-service
6. review-service
7. api-gateway (last)

### Test individually
```bash
curl http://localhost:8081/actuator/health
```

### Test through gateway (start all services first)
```bash
curl http://localhost:8080/actuator/health
```

---

## MODE 2 — Full Docker (everything containerised)

```bash
docker-compose up --build -d
```

Everything runs in containers. Services talk to each other via Docker container names. Test through gateway at http://localhost:8080.

```bash
docker-compose ps      # check all services healthy
docker-compose logs -f # watch logs
```

Tear down:
```bash
docker-compose down          # keep data
docker-compose down -v       # wipe all data
```

---

## Databases

All six databases must exist before running locally:

```sql
CREATE DATABASE user_db;
CREATE DATABASE verification_db;
CREATE DATABASE property_db;
CREATE DATABASE viewing_db;
CREATE DATABASE payment_db;
CREATE DATABASE review_db;
```

Flyway creates all tables automatically on first startup. You never need to run SQL manually.

---

## ngrok (for M-Pesa callbacks)

```bash
ngrok http 8080
```

Update .env with the ngrok URL:
```
MPESA_CALLBACK_URL=https://YOUR-ID.ngrok-free.app/api/payments/mpesa/callback
MPESA_B2C_CALLBACK_URL=https://YOUR-ID.ngrok-free.app/api/revenue/mpesa/b2c/callback
```

---

## Backup & restore (Postgres volumes)

There is no automated backup pipeline yet (out of scope for this pass) — this is a manual
procedure to use before risky operations (`docker-compose down -v`, migrations, upgrades)
until a real scheduled backup job exists.

**Back up all six databases** with the helper script (works against either `docker-compose.yml`
or `docker-compose-infra.yml` — it detects whichever DB containers are running):

```bash
./scripts/backup-db.sh            # writes timestamped .sql.gz dumps to ./backups/
```

**Restore a single database from a dump:**

```bash
gunzip -c backups/user_db_YYYYMMDD-HHMMSS.sql.gz | \
  docker exec -i smartre-user-db-1 psql -U postgres -d user_db
```

(Container names follow `<project-dir>-<service>-1`; run `docker ps` to confirm the exact name.)

A real runbook (automated nightly dumps + off-host retention + a tested restore drill) belongs
in ops/infra tooling once this moves past local dev / a single docker-compose host — tracked as
follow-up, not covered here.

---

## Observability (Docker mode only)

| Tool        | URL                   | Credentials       |
|-------------|-----------------------|-------------------|
| Kafka UI    | http://localhost:8090 | —                 |
| Grafana     | http://localhost:3000 | admin / smartre2026 |
| Prometheus  | http://localhost:9090 | —                 |
