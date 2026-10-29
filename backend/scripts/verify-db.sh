#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Verifies the Supabase schema on a throwaway PostGIS container:
#   stubs -> migrations (in order) -> seed -> pgTAP tests (pg_prove).
#
# Usage: backend/scripts/verify-db.sh [--keep]
#   --keep   leave the container running for inspection
#
# Requires Docker. Network access is needed once to apt-install pgTAP.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$HERE/.." && pwd)"
IMAGE="${VERIFY_DB_IMAGE:-postgis/postgis:16-3.4}"
CONTAINER="${VERIFY_DB_CONTAINER:-stravafit-db-verify}"
DB=stravafit
KEEP=false
[[ "${1:-}" == "--keep" ]] && KEEP=true

cleanup() {
  if [[ "$KEEP" == false ]]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# Git Bash on Windows rewrites /paths in arguments; docker exec must not.
export MSYS_NO_PATHCONV=1

docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
echo "==> starting $IMAGE"
docker run -d --name "$CONTAINER" -e POSTGRES_PASSWORD=postgres "$IMAGE" \
  postgres -c wal_level=logical >/dev/null

# The image starts a temporary server for its init scripts, stops it, then
# starts the real one: wait for the second "ready" log line, not the first.
ready=false
for _ in $(seq 1 120); do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]]; then
    echo "container exited during startup:" >&2
    docker logs --tail 30 "$CONTAINER" >&2 || true
    exit 1
  fi
  if (( $(docker logs "$CONTAINER" 2>&1 | grep -c 'database system is ready to accept connections') >= 2 )) \
     && docker exec "$CONTAINER" psql -U postgres -qtAc 'select 1' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || { echo "postgres did not become ready" >&2; exit 1; }

echo "==> installing pgTAP"
# The image is Debian bullseye, whose PGDG repo has moved to apt-archive.
docker exec "$CONTAINER" bash -c '
  set -e
  if ! apt-get update -qq >/dev/null 2>&1; then
    sed -i "s#http://apt.postgresql.org/pub/repos/apt#http://apt-archive.postgresql.org/pub/repos/apt#" \
      /etc/apt/sources.list.d/*.list
    apt-get update -qq >/dev/null
  fi
  apt-get install -y -qq postgresql-16-pgtap libtap-parser-sourcehandler-pgtap-perl >/dev/null'

echo "==> copying sources"
docker exec "$CONTAINER" mkdir -p /work
# docker cp needs a native path on Windows (MSYS path conversion is off).
HOST_BACKEND="$BACKEND"
if command -v cygpath >/dev/null 2>&1; then HOST_BACKEND="$(cygpath -w "$BACKEND")"; fi
docker cp "$HOST_BACKEND/supabase/." "$CONTAINER:/work/supabase"
docker cp "$HOST_BACKEND/ci/." "$CONTAINER:/work/ci"

psql_db() { docker exec -i "$CONTAINER" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 -q "$@"; }

# A fresh database from template0 mimics Supabase: no PostGIS until our
# extensions migration installs it into the `extensions` schema.
docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -q \
  -c "create database $DB template template0" \
  -c "alter database $DB set search_path = \"\$user\", public, extensions"

echo "==> applying Supabase stubs"
psql_db -f /work/ci/supabase-stubs.sql

echo "==> applying migrations"
for f in $(docker exec "$CONTAINER" bash -c 'ls /work/supabase/migrations/*.sql | sort'); do
  echo "    $(basename "$f")"
  psql_db -f "$f"
done

echo "==> loading seed"
psql_db -f /work/supabase/seed.sql

echo "==> running pgTAP tests"
psql_db -c 'create extension if not exists pgtap with schema extensions'
docker exec "$CONTAINER" bash -c \
  "cd /work/supabase/tests && pg_prove -U postgres -d $DB --ext .sql \$(ls *.sql | sort)"

echo "==> all green"
