#!/usr/bin/env bash
# Manual backup of all SmartRE Postgres databases.
#
# This is a stopgap, not a real backup pipeline: run it by hand before
# destructive operations (docker-compose down -v, migrations, upgrades).
# A scheduled/automated version with off-host retention is tracked as
# follow-up work, not implemented here.
#
# Usage:
#   ./scripts/backup-db.sh [output-dir]
#
# Requires: docker, a running set of <db>-db containers (from either
# docker-compose.yml or docker-compose-infra.yml), and the DB_PASSWORD
# used to start them (read from .env if present).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
OUT_DIR="${1:-$REPO_ROOT/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"

DATABASES=(user_db verification_db property_db viewing_db payment_db review_db)

mkdir -p "$OUT_DIR"

# Pull DB_PASSWORD from .env if it's not already exported, so pg_dump inside
# the container can authenticate (POSTGRES_PASSWORD env var, matches how the
# containers themselves were started).
if [ -z "${DB_PASSWORD:-}" ] && [ -f "$REPO_ROOT/.env" ]; then
  DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "$REPO_ROOT/.env" | tail -1 | cut -d '=' -f2-)"
fi
if [ -z "${DB_PASSWORD:-}" ]; then
  echo "DB_PASSWORD not set and not found in $REPO_ROOT/.env - aborting." >&2
  exit 1
fi

find_container() {
  local db_service="$1"
  # Matches container names from either compose project, e.g.
  # "smartre-user-db-1" (docker-compose.yml) - the -infra.yml DB containers
  # share the same POSTGRES_DB names so either works as a dump source.
  docker ps --format '{{.Names}}' | grep -E "(^|-)${db_service}-db(-1)?$" | head -1
}

FAILED=0
for db in "${DATABASES[@]}"; do
  service="${db%_db}"
  container="$(find_container "$service" || true)"
  if [ -z "$container" ]; then
    echo "WARN: no running container found for ${service}-db, skipping $db" >&2
    FAILED=1
    continue
  fi

  dest="$OUT_DIR/${db}_${STAMP}.sql.gz"
  echo "Dumping $db from container '$container' -> $dest"
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$container" \
    pg_dump -U postgres -d "$db" | gzip > "$dest"
done

if [ "$FAILED" -ne 0 ]; then
  echo "One or more databases were not backed up - see warnings above." >&2
  exit 1
fi

echo "All backups written to $OUT_DIR"
