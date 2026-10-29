-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Minimal stand-ins for the parts of a Supabase database our migrations rely
-- on, so the schema can be verified on a plain postgis/postgis container.
-- NEVER apply this to a real Supabase instance: it already has all of this.
--
-- Mirrors: API roles, auth.users + auth.uid()/role()/jwt() (reading the same
-- GUCs PostgREST sets), storage.buckets/objects + path helpers, and the
-- supabase_realtime publication.

create role anon nologin noinherit;
create role authenticated nologin noinherit;
create role service_role nologin noinherit bypassrls;
create role authenticator login noinherit password 'authenticator';
grant anon, authenticated, service_role to authenticator;

-- The verify script sets the database search_path to "$user", public, extensions
-- (Supabase's default) before applying this file.

grant usage on schema public to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- auth
-- ---------------------------------------------------------------------------
create schema auth;
grant usage on schema auth to anon, authenticated, service_role;

-- Subset of GoTrue's auth.users / auth.identities, enough for seed.sql.
create table auth.users (
  instance_id          uuid,
  id                   uuid primary key,
  aud                  text,
  role                 text,
  email                text unique,
  encrypted_password   text,
  email_confirmed_at   timestamptz,
  confirmation_token   text,
  recovery_token       text,
  email_change_token_new text,
  email_change         text,
  raw_app_meta_data    jsonb,
  raw_user_meta_data   jsonb not null default '{}'::jsonb,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create table auth.identities (
  id              uuid primary key default gen_random_uuid(),
  provider_id     text not null,
  user_id         uuid not null references auth.users (id) on delete cascade,
  identity_data   jsonb not null,
  provider        text not null,
  last_sign_in_at timestamptz,
  created_at      timestamptz,
  updated_at      timestamptz,
  unique (provider_id, provider)
);

create function auth.jwt() returns jsonb
language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb;
$$;

create function auth.uid() returns uuid
language sql stable as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), ''),
    auth.jwt() ->> 'sub')::uuid;
$$;

create function auth.role() returns text
language sql stable as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.role', true), ''),
    auth.jwt() ->> 'role');
$$;

grant execute on function auth.jwt(), auth.uid(), auth.role() to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- storage
-- ---------------------------------------------------------------------------
create schema storage;
grant usage on schema storage to anon, authenticated, service_role;

create table storage.buckets (
  id                 text primary key,
  name               text not null unique,
  owner              uuid,
  public             boolean not null default false,
  file_size_limit    bigint,
  allowed_mime_types text[],
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

create table storage.objects (
  id         uuid primary key default gen_random_uuid(),
  bucket_id  text references storage.buckets (id),
  name       text,
  owner      uuid,
  metadata   jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (bucket_id, name)
);

alter table storage.buckets enable row level security;
alter table storage.objects enable row level security;
grant select on storage.buckets to anon, authenticated;
grant all on storage.buckets, storage.objects to service_role;
grant select, insert, update, delete on storage.objects to anon, authenticated;
create policy buckets_read on storage.buckets for select using (true);

create function storage.foldername(name text) returns text[]
language plpgsql immutable as $$
declare
  _parts text[];
begin
  select string_to_array(name, '/') into _parts;
  return _parts[1:array_length(_parts, 1) - 1];
end
$$;

create function storage.filename(name text) returns text
language plpgsql immutable as $$
declare
  _parts text[];
begin
  select string_to_array(name, '/') into _parts;
  return _parts[array_length(_parts, 1)];
end
$$;

-- ---------------------------------------------------------------------------
-- realtime
-- ---------------------------------------------------------------------------
create publication supabase_realtime;

-- Supabase migration history table, so the self-host migrate script and the
-- CLI agree on what has been applied.
create schema if not exists supabase_migrations;
create table if not exists supabase_migrations.schema_migrations (
  version    text primary key,
  statements text[],
  name       text
);
