-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Extensions and internal schemas.
--
-- Supabase installs extensions into the `extensions` schema and keeps it on the
-- default search_path. We follow the same convention so the migrations behave
-- identically on hosted Supabase, the self-host docker stack and a bare
-- PostGIS container used for CI.

create schema if not exists extensions;
-- Supabase grants this already; repeated here so bare Postgres matches.
grant usage on schema extensions to anon, authenticated, service_role;

create extension if not exists pgcrypto with schema extensions;  -- gen_random_bytes, digest
create extension if not exists citext   with schema extensions;  -- case-insensitive handles
create extension if not exists postgis  with schema extensions;  -- geography, ST_* functions

-- pgmq (Postgres message queue) ships with Supabase but not with plain Postgres.
-- Install it when the server offers it; otherwise the job API in
-- 20261029000700_uploads_jobs.sql falls back to a SKIP LOCKED table queue.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pgmq') then
    execute 'create extension if not exists pgmq';
  else
    raise notice 'pgmq not available: using the SKIP LOCKED table queue fallback';
  end if;
end
$$;

-- `private` holds helper functions and internal tables. It is never listed in
-- the PostgREST `schemas` setting (see config.toml), so nothing here is reachable
-- over HTTP. RLS policies call the helpers, which is why API roles need USAGE.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated, service_role;

comment on schema private is
  'Internal helpers, job queue and RLS predicates. Not exposed through PostgREST.';
