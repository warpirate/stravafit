#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Applies backend/supabase/migrations to the self-hosted database, in order,
# each in its own transaction. Applied versions are recorded in
# supabase_migrations.schema_migrations (the same table the Supabase CLI uses),
# so re-running is safe and only new files are applied.
#
# Runs inside the `migrate` compose service:
#   docker compose --profile migrate run --rm migrate
# Set SEED=1 to also load the demo seed (development only).
set -eu

psql_q() { psql -v ON_ERROR_STOP=1 -X -q "$@"; }

psql_q -c "create schema if not exists supabase_migrations;
           create table if not exists supabase_migrations.schema_migrations (
             version text primary key, statements text[], name text);"

applied=0
for file in $(ls /migrations/*.sql | sort); do
  base=$(basename "$file" .sql)
  version=${base%%_*}
  name=${base#*_}
  exists=$(psql -X -tAq -c "select 1 from supabase_migrations.schema_migrations where version = '$version'")
  if [ "$exists" = "1" ]; then
    continue
  fi
  echo "applying $base"
  psql_q --single-transaction \
    -f "$file" \
    -c "insert into supabase_migrations.schema_migrations (version, name) values ('$version', '$name')"
  applied=$((applied + 1))
done
echo "migrations applied: $applied"

if [ "${SEED:-0}" = "1" ]; then
  echo "loading demo seed"
  psql_q --single-transaction -f /seed.sql
fi
