-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Job queue (pgmq when installed, SKIP LOCKED table otherwise) and the upload
-- lifecycle that feeds the ingest worker (blueprint §10, ADR-008).
--
-- Worker contract (Kotlin worker connects with the service role over JDBC):
--   select * from private.job_claim('ingest', 10, 60);   -- lease up to 10 jobs for 60 s
--   select private.job_complete('ingest', msg_id);       -- success
--   select private.job_fail('ingest', msg_id, 'reason'); -- retry with backoff / dead-letter

-- ---------------------------------------------------------------------------
-- Queue registry and fallback storage
-- ---------------------------------------------------------------------------

create table private.job_queues (
  name         text primary key check (name ~ '^[a-z_]{1,40}$'),
  max_attempts smallint not null default 5 check (max_attempts between 1 and 50),
  description  text not null
);

insert into private.job_queues (name, max_attempts, description) values
  ('ingest',            5, 'Parse an uploaded file, recompute metrics, create the activity'),
  ('post_process',      5, 'Segments, best efforts, gear distance, feed/notification fan-out'),
  ('privacy_reprocess', 8, 'Rebuild public stream derivatives and caches after a privacy change'),
  ('media',             5, 'Strip EXIF/GPS from photos, resize, move to the photos bucket'),
  ('cleanup',           8, 'Purge Storage objects and derived data of deleted activities');

create table private.job_queue (
  msg_id      bigserial primary key,
  queue       text not null references private.job_queues (name),
  payload     jsonb not null,
  enqueued_at timestamptz not null default now(),
  visible_at  timestamptz not null default now(),
  read_ct     integer not null default 0
);

create index job_queue_ready_idx on private.job_queue (queue, visible_at, msg_id);

create table private.job_archive (
  msg_id      bigint primary key,
  queue       text not null,
  payload     jsonb not null,
  enqueued_at timestamptz not null,
  archived_at timestamptz not null default now(),
  read_ct     integer not null,
  outcome     text not null check (outcome in ('done', 'dead'))
);

-- Dead letters and error history for both backends.
create table private.job_failures (
  id        bigserial primary key,
  queue     text not null,
  msg_id    bigint not null,
  payload   jsonb,
  error     text not null,
  dead      boolean not null,
  failed_at timestamptz not null default now()
);

create index job_failures_queue_idx on private.job_failures (queue, failed_at desc);

-- These are internal tables; RLS on with no policies = backend roles only.
alter table private.job_queues   enable row level security;
alter table private.job_queue    enable row level security;
alter table private.job_archive  enable row level security;
alter table private.job_failures enable row level security;
revoke all on table private.job_queues, private.job_queue, private.job_archive,
  private.job_failures from anon, authenticated;
grant all on table private.job_queues, private.job_queue, private.job_archive,
  private.job_failures to service_role;
grant usage, select on sequence private.job_queue_msg_id_seq, private.job_failures_id_seq
  to service_role;

do $$
declare
  q text;
begin
  if exists (select 1 from pg_extension where extname = 'pgmq') then
    for q in select name from private.job_queues loop
      execute format('select pgmq.create(%L)', q);
    end loop;
  end if;
end
$$;

-- ---------------------------------------------------------------------------
-- Job API
-- ---------------------------------------------------------------------------

create or replace function private.job_backend()
returns text
language sql
stable
set search_path = ''
as $$
  select case when exists (select 1 from pg_catalog.pg_extension where extname = 'pgmq')
              then 'pgmq' else 'table' end;
$$;

create or replace function private.job_assert_queue(p_queue text)
returns void
language plpgsql
stable
set search_path = ''
as $$
begin
  if not exists (select 1 from private.job_queues where name = p_queue) then
    raise exception 'unknown job queue %', p_queue using errcode = 'invalid_parameter_value';
  end if;
end;
$$;

create or replace function private.job_enqueue(p_queue text, p_payload jsonb, p_delay_s integer default 0)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id bigint;
begin
  perform private.job_assert_queue(p_queue);
  if private.job_backend() = 'pgmq' then
    execute 'select * from pgmq.send($1, $2, $3)' into v_id using p_queue, p_payload, p_delay_s;
  else
    insert into private.job_queue (queue, payload, visible_at)
    values (p_queue, p_payload, now() + make_interval(secs => greatest(p_delay_s, 0)))
    returning msg_id into v_id;
  end if;
  return v_id;
end;
$$;

comment on function private.job_enqueue(text, jsonb, integer) is
  'Enqueue a job on a registered queue. Uses pgmq when installed, else the SKIP LOCKED table.';

create or replace function private.job_claim(p_queue text, p_qty integer, p_visibility_s integer)
returns table (msg_id bigint, read_ct integer, enqueued_at timestamptz, payload jsonb)
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.job_assert_queue(p_queue);
  if p_qty < 1 or p_visibility_s < 1 then
    raise exception 'qty and visibility timeout must be positive' using errcode = 'invalid_parameter_value';
  end if;

  if private.job_backend() = 'pgmq' then
    return query execute
      'select m.msg_id, m.read_ct, m.enqueued_at, m.message from pgmq.read($1, $2, $3) m'
      using p_queue, p_visibility_s, p_qty;
    return;
  end if;

  return query
  with picked as (
    select j.msg_id
      from private.job_queue j
     where j.queue = p_queue and j.visible_at <= now()
     order by j.msg_id
     limit p_qty
     for update skip locked
  )
  update private.job_queue j
     set read_ct = j.read_ct + 1,
         visible_at = now() + make_interval(secs => p_visibility_s)
    from picked
   where j.msg_id = picked.msg_id
  returning j.msg_id, j.read_ct, j.enqueued_at, j.payload;
end;
$$;

comment on function private.job_claim(text, integer, integer) is
  'Lease up to p_qty visible jobs for p_visibility_s seconds (at-least-once delivery).';

create or replace function private.job_complete(p_queue text, p_msg_id bigint)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_ok boolean;
begin
  perform private.job_assert_queue(p_queue);
  if private.job_backend() = 'pgmq' then
    execute 'select pgmq.archive($1, $2)' into v_ok using p_queue, p_msg_id;
    return coalesce(v_ok, false);
  end if;

  with done as (
    delete from private.job_queue j where j.queue = p_queue and j.msg_id = p_msg_id
    returning j.*
  )
  insert into private.job_archive (msg_id, queue, payload, enqueued_at, read_ct, outcome)
  select d.msg_id, d.queue, d.payload, d.enqueued_at, d.read_ct, 'done' from done d;
  get diagnostics v_ok = row_count;
  return v_ok;
end;
$$;

-- Records the error; retries with exponential backoff (30 s * 2^attempt, capped
-- at 1 h) until the queue's max_attempts, then dead-letters the job.
create or replace function private.job_fail(p_queue text, p_msg_id bigint, p_error text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_max     smallint;
  v_read_ct integer;
  v_payload jsonb;
  v_dead    boolean;
  v_backoff integer;
begin
  select max_attempts into v_max from private.job_queues where name = p_queue;
  if v_max is null then
    perform private.job_assert_queue(p_queue);
  end if;

  if private.job_backend() = 'pgmq' then
    execute format('select read_ct, message from pgmq.%I where msg_id = $1', 'q_' || p_queue)
      into v_read_ct, v_payload using p_msg_id;
  else
    select j.read_ct, j.payload into v_read_ct, v_payload
      from private.job_queue j where j.queue = p_queue and j.msg_id = p_msg_id;
  end if;

  if v_read_ct is null then
    return false;
  end if;

  v_dead := v_read_ct >= v_max;
  insert into private.job_failures (queue, msg_id, payload, error, dead)
  values (p_queue, p_msg_id, v_payload, left(coalesce(p_error, 'unknown error'), 4000), v_dead);

  if v_dead then
    if private.job_backend() = 'pgmq' then
      execute 'select pgmq.archive($1, $2)' using p_queue, p_msg_id;
    else
      with gone as (
        delete from private.job_queue j where j.queue = p_queue and j.msg_id = p_msg_id
        returning j.*
      )
      insert into private.job_archive (msg_id, queue, payload, enqueued_at, read_ct, outcome)
      select g.msg_id, g.queue, g.payload, g.enqueued_at, g.read_ct, 'dead' from gone g;
    end if;
    return true;
  end if;

  v_backoff := least(30 * (2 ^ greatest(v_read_ct - 1, 0))::integer, 3600);
  if private.job_backend() = 'pgmq' then
    execute 'select pgmq.set_vt($1, $2, $3)' using p_queue, p_msg_id, v_backoff;
  else
    update private.job_queue j
       set visible_at = now() + make_interval(secs => v_backoff)
     where j.queue = p_queue and j.msg_id = p_msg_id;
  end if;
  return true;
end;
$$;

revoke execute on function private.job_enqueue(text, jsonb, integer),
  private.job_claim(text, integer, integer), private.job_complete(text, bigint),
  private.job_fail(text, bigint, text) from public, anon, authenticated;
grant execute on function private.job_enqueue(text, jsonb, integer),
  private.job_claim(text, integer, integer), private.job_complete(text, bigint),
  private.job_fail(text, bigint, text) to service_role;

-- ---------------------------------------------------------------------------
-- Uploads
-- ---------------------------------------------------------------------------
--
-- Lifecycle:
--   awaiting_file --submit_upload--> queued --worker--> processing
--   processing --> ready | duplicate | error | queued (lease expired / requeue)
--   queued --> error (rejected before processing)
--   error --submit_upload--> queued (retry)
--   ready, duplicate: terminal

create table public.uploads (
  id           uuid primary key default gen_random_uuid(),
  owner_id     uuid not null references public.profiles (id) on delete cascade,
  client_uuid  uuid not null,
  format       public.file_format not null check (format <> 'sfstream'),
  byte_size    bigint not null check (byte_size between 1 and 104857600),
  sha256       text check (sha256 ~ '^[0-9a-f]{64}$'),
  storage_path text not null,
  status       public.upload_status not null default 'awaiting_file',
  error        text check (length(error) <= 2000),
  attempts     smallint not null default 0,
  activity_id  uuid references public.activities (id) on delete set null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  processed_at timestamptz,
  constraint uploads_owner_client_uuid_key unique (owner_id, client_uuid)
);

comment on table public.uploads is
  'Upload lifecycle rows. Clients use create_upload/submit_upload RPCs; the worker moves the status on.';

create index uploads_owner_idx on public.uploads (owner_id, created_at desc);
create index uploads_owner_sha_idx on public.uploads (owner_id, sha256) where sha256 is not null;
create index uploads_activity_idx on public.uploads (activity_id) where activity_id is not null;

alter table public.activities
  add constraint activities_upload_fk foreign key (upload_id)
  references public.uploads (id) on delete set null;

create trigger uploads_updated_at before update on public.uploads
  for each row execute function private.set_updated_at();

create or replace function private.uploads_guard_transition()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.status is distinct from old.status and not (
       (old.status = 'awaiting_file' and new.status = 'queued')
    or (old.status = 'queued'        and new.status in ('processing', 'error'))
    or (old.status = 'processing'    and new.status in ('ready', 'duplicate', 'error', 'queued'))
    or (old.status = 'error'         and new.status = 'queued')) then
    raise exception 'invalid upload status transition % -> %', old.status, new.status
      using errcode = 'check_violation';
  end if;
  if new.status in ('ready', 'duplicate', 'error') and new.status is distinct from old.status then
    new.processed_at := now();
  end if;
  return new;
end;
$$;

create trigger uploads_guard_transition before update on public.uploads
  for each row execute function private.uploads_guard_transition();

alter table public.uploads enable row level security;
revoke all on table public.uploads from anon, authenticated;
grant all on table public.uploads to service_role;
grant select on table public.uploads to authenticated;

create policy uploads_owner_select on public.uploads
  for select to authenticated using (owner_id = (select auth.uid()));

-- Idempotent: the same client_uuid always returns the same upload row. If an
-- activity with that client_uuid already exists (e.g. created directly by the
-- app), the upload is born as a duplicate pointing at it.
create or replace function public.create_upload(
  p_client_uuid uuid,
  p_format      public.file_format,
  p_byte_size   bigint,
  p_sha256      text default null
)
returns public.uploads
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_id       uuid := gen_random_uuid();
  v_activity uuid;
  v_row      public.uploads;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;

  select a.id into v_activity from public.activities a
   where a.owner_id = v_uid and a.client_uuid = p_client_uuid;

  -- An upload whose activity already exists is born as a terminal duplicate
  -- and never reaches the queue.
  insert into public.uploads (id, owner_id, client_uuid, format, byte_size, sha256,
                              storage_path, status, activity_id, processed_at)
  values (v_id, v_uid, p_client_uuid, p_format, p_byte_size, lower(p_sha256),
          v_uid::text || '/' || v_id::text || '.' || p_format::text,
          case when v_activity is null then 'awaiting_file' else 'duplicate' end::public.upload_status,
          v_activity,
          case when v_activity is null then null else now() end)
  on conflict on constraint uploads_owner_client_uuid_key do nothing;

  select * into v_row from public.uploads u
   where u.owner_id = v_uid and u.client_uuid = p_client_uuid;
  return v_row;
end;
$$;

comment on function public.create_upload(uuid, public.file_format, bigint, text) is
  'Register an upload (idempotent per client_uuid). Upload the file to originals/<storage_path>, then call submit_upload.';

create or replace function public.submit_upload(p_upload_id uuid)
returns public.uploads
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.uploads;
begin
  select * into v_row from public.uploads u
   where u.id = p_upload_id and u.owner_id = v_uid
   for update;
  if not found then
    raise exception 'upload not found' using errcode = 'no_data_found';
  end if;

  if v_row.status not in ('awaiting_file', 'error') then
    return v_row;  -- already submitted: idempotent no-op
  end if;

  if not exists (select 1 from storage.objects o
                  where o.bucket_id = 'originals' and o.name = v_row.storage_path) then
    raise exception 'file not uploaded yet: originals/%', v_row.storage_path
      using errcode = 'object_not_in_prerequisite_state';
  end if;

  update public.uploads u
     set status = 'queued', error = null, attempts = u.attempts + 1
   where u.id = p_upload_id
  returning * into v_row;

  perform private.job_enqueue('ingest', jsonb_build_object(
    'upload_id', v_row.id, 'owner_id', v_row.owner_id,
    'bucket', 'originals', 'path', v_row.storage_path, 'format', v_row.format));
  return v_row;
end;
$$;

comment on function public.submit_upload(uuid) is
  'Queue an uploaded file for ingest (also retries an upload in error). Idempotent.';

revoke execute on function public.create_upload(uuid, public.file_format, bigint, text),
  public.submit_upload(uuid) from public, anon;
grant execute on function public.create_upload(uuid, public.file_format, bigint, text),
  public.submit_upload(uuid) to authenticated, service_role;

-- Worker-side transitions (service role only).
create or replace function private.worker_upload_started(p_upload_id uuid)
returns public.uploads
language sql
security definer
set search_path = ''
as $$
  update public.uploads set status = 'processing'
   where id = p_upload_id and status = 'queued'
  returning *;
$$;

create or replace function private.worker_upload_finished(
  p_upload_id   uuid,
  p_activity_id uuid,
  p_duplicate   boolean default false
)
returns public.uploads
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row public.uploads;
begin
  update public.uploads
     set status = case when p_duplicate then 'duplicate' else 'ready' end::public.upload_status,
         activity_id = p_activity_id, error = null
   where id = p_upload_id and status = 'processing'
  returning * into v_row;

  if found and not p_duplicate then
    update public.activities set upload_id = p_upload_id
     where id = p_activity_id and upload_id is null;
  end if;
  return v_row;
end;
$$;

create or replace function private.worker_upload_failed(p_upload_id uuid, p_error text)
returns public.uploads
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row public.uploads;
begin
  update public.uploads
     set status = 'error', error = left(p_error, 2000)
   where id = p_upload_id and status in ('queued', 'processing')
  returning * into v_row;

  if found then
    insert into public.notifications (recipient_id, kind, payload)
    values (v_row.owner_id, 'upload_failed',
            jsonb_build_object('upload_id', v_row.id, 'error', v_row.error));
  end if;
  return v_row;
end;
$$;

revoke execute on function private.worker_upload_started(uuid),
  private.worker_upload_finished(uuid, uuid, boolean),
  private.worker_upload_failed(uuid, text) from public, anon, authenticated;
grant execute on function private.worker_upload_started(uuid),
  private.worker_upload_finished(uuid, uuid, boolean),
  private.worker_upload_failed(uuid, text) to service_role;

-- ---------------------------------------------------------------------------
-- Activity events -> jobs
-- ---------------------------------------------------------------------------

create or replace function private.activities_enqueue_jobs()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    perform private.job_enqueue('post_process',
      jsonb_build_object('activity_id', new.id, 'owner_id', new.owner_id, 'reason', 'created'));
  elsif tg_op = 'UPDATE' then
    if (new.visibility, new.hide_map, new.hide_start_m, new.hide_end_m, new.hide_heart_rate,
        new.hide_pace, new.hide_power, new.hide_calories, new.hide_start_time,
        new.hide_elevation, new.muted, new.route_public)
       is distinct from
       (old.visibility, old.hide_map, old.hide_start_m, old.hide_end_m, old.hide_heart_rate,
        old.hide_pace, old.hide_power, old.hide_calories, old.hide_start_time,
        old.hide_elevation, old.muted, old.route_public) then
      perform private.job_enqueue('privacy_reprocess',
        jsonb_build_object('activity_id', new.id, 'owner_id', new.owner_id,
                           'visibility_downgraded', new.visibility > old.visibility));
    end if;
  elsif tg_op = 'DELETE' then
    perform private.job_enqueue('cleanup',
      jsonb_build_object('activity_id', old.id, 'owner_id', old.owner_id));
  end if;
  return null;
end;
$$;

create trigger activities_enqueue_jobs
  after insert or update or delete on public.activities
  for each row execute function private.activities_enqueue_jobs();
