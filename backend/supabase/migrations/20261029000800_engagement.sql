-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Engagement: high-fives, comments, notification inbox and user reports.

-- ---------------------------------------------------------------------------
-- Activity visibility predicate
-- ---------------------------------------------------------------------------

-- Rule (settings-privacy-units §6, privacy-safety §F):
--   owner: always; blocked either way: never;
--   everyone: anyone (including anonymous); followers: accepted followers;
--   only_me: owner only. Soft-deleted activities are visible to nobody else.
create or replace function private.can_view_activity(
  p_owner      uuid,
  p_visibility public.activity_visibility
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case private.viewer_relation(p_owner)
    when 'self'      then true
    when 'blocked'   then false
    when 'follower'  then p_visibility in ('everyone', 'followers')
    else p_visibility = 'everyone'
  end;
$$;

create or replace function private.can_view_activity(p_activity uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select (a.deleted_at is null or a.owner_id = auth.uid())
           and private.can_view_activity(a.owner_id, a.visibility)
      from public.activities a where a.id = p_activity), false);
$$;

create or replace function private.activity_owner(p_activity uuid)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
  select a.owner_id from public.activities a where a.id = p_activity;
$$;

-- Inserts an inbox row unless the actor is the recipient or the two are blocked.
create or replace function private.notify(
  p_recipient uuid,
  p_kind      public.notification_kind,
  p_actor     uuid,
  p_activity  uuid default null,
  p_comment   uuid default null,
  p_payload   jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_recipient is null
     or p_recipient = p_actor
     or private.is_blocked_between(p_recipient, p_actor) then
    return;
  end if;
  insert into public.notifications (recipient_id, kind, actor_id, activity_id, comment_id, payload)
  values (p_recipient, p_kind, p_actor, p_activity, p_comment, coalesce(p_payload, '{}'::jsonb));
end;
$$;

revoke execute on function private.notify(uuid, public.notification_kind, uuid, uuid, uuid, jsonb)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- High-fives (our neutral name for appreciation on an activity)
-- ---------------------------------------------------------------------------

create table public.high_fives (
  activity_id uuid not null references public.activities (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (activity_id, user_id)
);

create index high_fives_user_idx on public.high_fives (user_id);

create or replace function private.high_fives_after_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_owner uuid;
begin
  if tg_op = 'INSERT' then
    update public.activities set high_five_count = high_five_count + 1
     where id = new.activity_id
    returning owner_id into v_owner;
    perform private.notify(v_owner, 'high_five', new.user_id, new.activity_id);
  else
    update public.activities set high_five_count = greatest(high_five_count - 1, 0)
     where id = old.activity_id;
  end if;
  return null;
end;
$$;

create trigger high_fives_after_change
  after insert or delete on public.high_fives
  for each row execute function private.high_fives_after_change();

-- ---------------------------------------------------------------------------
-- Comments (flat thread per activity)
-- ---------------------------------------------------------------------------

create table public.comments (
  id          uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.activities (id) on delete cascade,
  author_id   uuid not null references public.profiles (id) on delete cascade,
  body        text not null check (length(btrim(body)) between 1 and 2000),
  created_at  timestamptz not null default now(),
  edited_at   timestamptz
);

create index comments_activity_idx on public.comments (activity_id, created_at);
create index comments_author_idx on public.comments (author_id);

create or replace function private.comments_before_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.body is distinct from old.body then
    new.edited_at := now();
  end if;
  return new;
end;
$$;

create trigger comments_before_update before update on public.comments
  for each row execute function private.comments_before_update();

create or replace function private.comments_after_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_owner uuid;
begin
  if tg_op = 'INSERT' then
    update public.activities set comment_count = comment_count + 1
     where id = new.activity_id
    returning owner_id into v_owner;
    perform private.notify(v_owner, 'comment', new.author_id, new.activity_id, new.id);
  else
    update public.activities set comment_count = greatest(comment_count - 1, 0)
     where id = old.activity_id;
  end if;
  return null;
end;
$$;

create trigger comments_after_change
  after insert or delete on public.comments
  for each row execute function private.comments_after_change();

-- ---------------------------------------------------------------------------
-- Notifications inbox
-- ---------------------------------------------------------------------------

create table public.notifications (
  id           uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  kind         public.notification_kind not null,
  actor_id     uuid references public.profiles (id) on delete cascade,
  activity_id  uuid references public.activities (id) on delete cascade,
  comment_id   uuid references public.comments (id) on delete cascade,
  payload      jsonb not null default '{}'::jsonb,
  created_at   timestamptz not null default now(),
  read_at      timestamptz
);

comment on table public.notifications is
  'Per-user inbox. Rows are written by triggers/worker only; push fan-out is done by the worker.';

create index notifications_inbox_idx on public.notifications (recipient_id, created_at desc);
create index notifications_unread_idx on public.notifications (recipient_id) where read_at is null;
create index notifications_actor_idx on public.notifications (actor_id) where actor_id is not null;
create index notifications_activity_idx on public.notifications (activity_id) where activity_id is not null;
create index notifications_comment_idx on public.notifications (comment_id) where comment_id is not null;

-- A block also clears the inbox rows each side received from the other.
create or replace function private.on_block_clear_notifications()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.notifications n
   where (n.recipient_id = new.blocker_id and n.actor_id = new.blocked_id)
      or (n.recipient_id = new.blocked_id and n.actor_id = new.blocker_id);
  return null;
end;
$$;

create trigger blocks_clear_notifications
  after insert on public.blocks
  for each row execute function private.on_block_clear_notifications();

-- ---------------------------------------------------------------------------
-- Reports (moderation-admin-tooling §4, v0 subset: one row per user report)
-- ---------------------------------------------------------------------------

create table public.reports (
  id              uuid primary key default gen_random_uuid(),
  reporter_id     uuid not null references public.profiles (id) on delete cascade,
  target_type     public.report_target not null,
  target_id       uuid not null,
  target_owner_id uuid references public.profiles (id) on delete set null,
  reason          public.report_reason not null,
  details         text check (length(details) <= 2000),
  status          public.report_status not null default 'open',
  resolution_note text check (length(resolution_note) <= 2000),
  resolved_by     uuid references public.profiles (id) on delete set null,
  resolved_at     timestamptz,
  created_at      timestamptz not null default now(),
  constraint reports_one_per_reporter unique (reporter_id, target_type, target_id)
);

create index reports_queue_idx on public.reports (status, created_at);
create index reports_target_idx on public.reports (target_type, target_id);
create index reports_target_owner_idx on public.reports (target_owner_id);
create index reports_resolved_by_idx on public.reports (resolved_by) where resolved_by is not null;

-- Validates that the reporter can actually see the target, fills the owner and
-- applies a per-user daily cap (moderation-admin-tooling §3.4 flag abuse).
create or replace function private.reports_before_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_owner   uuid;
  v_visible boolean;
begin
  case new.target_type
    when 'activity' then
      v_owner := private.activity_owner(new.target_id);
      v_visible := private.can_view_activity(new.target_id);
    when 'comment' then
      select c.author_id, private.can_view_activity(c.activity_id)
        into v_owner, v_visible
        from public.comments c where c.id = new.target_id;
    when 'profile' then
      select p.id, not private.is_blocked_between(auth.uid(), p.id)
        into v_owner, v_visible
        from public.profiles p where p.id = new.target_id;
    else
      -- Photos arrive with the media package; accept the report as-is.
      v_visible := true;
  end case;

  if not coalesce(v_visible, false) then
    raise exception 'report target not found' using errcode = 'no_data_found';
  end if;

  if (select count(*) from public.reports r
       where r.reporter_id = new.reporter_id
         and r.created_at > now() - interval '24 hours') >= 20 then
    raise exception 'report limit reached, try again tomorrow' using errcode = 'program_limit_exceeded';
  end if;

  new.target_owner_id := v_owner;
  new.status := 'open';
  return new;
end;
$$;

create trigger reports_before_insert before insert on public.reports
  for each row execute function private.reports_before_insert();

create or replace function private.reports_before_update()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status in ('actioned', 'dismissed') and old.status not in ('actioned', 'dismissed') then
    new.resolved_at := now();
    new.resolved_by := coalesce(new.resolved_by, auth.uid());
  end if;
  return new;
end;
$$;

create trigger reports_before_update before update on public.reports
  for each row execute function private.reports_before_update();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.high_fives    enable row level security;
alter table public.comments      enable row level security;
alter table public.notifications enable row level security;
alter table public.reports       enable row level security;

revoke all on table public.high_fives, public.comments, public.notifications, public.reports
  from anon, authenticated;
grant all on table public.high_fives, public.comments, public.notifications, public.reports
  to service_role;

-- high_fives: visible with the activity, minus people blocked with the viewer.
grant select on table public.high_fives to anon, authenticated;
grant insert (activity_id, user_id), delete on table public.high_fives to authenticated;

create policy high_fives_select on public.high_fives
  for select to anon, authenticated
  using (private.can_view_activity(activity_id)
         and not private.is_blocked_between((select auth.uid()), user_id));
create policy high_fives_insert_own on public.high_fives
  for insert to authenticated
  with check (user_id = (select auth.uid())
              and private.can_view_activity(activity_id)
              and private.activity_owner(activity_id) <> (select auth.uid()));
create policy high_fives_delete_own on public.high_fives
  for delete to authenticated using (user_id = (select auth.uid()));

-- comments
grant select on table public.comments to anon, authenticated;
grant insert (activity_id, author_id, body) on table public.comments to authenticated;
grant update (body) on table public.comments to authenticated;
grant delete on table public.comments to authenticated;

create policy comments_select on public.comments
  for select to anon, authenticated
  using (private.can_view_activity(activity_id)
         and not private.is_blocked_between((select auth.uid()), author_id));
create policy comments_insert_own on public.comments
  for insert to authenticated
  with check (author_id = (select auth.uid()) and private.can_view_activity(activity_id));
create policy comments_update_own on public.comments
  for update to authenticated
  using (author_id = (select auth.uid())) with check (author_id = (select auth.uid()));
create policy comments_delete_author_or_owner on public.comments
  for delete to authenticated
  using (author_id = (select auth.uid())
         or private.activity_owner(activity_id) = (select auth.uid()));

-- notifications: recipient-only; only read_at may be changed.
grant select, delete on table public.notifications to authenticated;
grant update (read_at) on table public.notifications to authenticated;

create policy notifications_select_own on public.notifications
  for select to authenticated using (recipient_id = (select auth.uid()));
create policy notifications_update_own on public.notifications
  for update to authenticated
  using (recipient_id = (select auth.uid())) with check (recipient_id = (select auth.uid()));
create policy notifications_delete_own on public.notifications
  for delete to authenticated using (recipient_id = (select auth.uid()));

-- reports: reporters see their own; moderators see and resolve all.
grant select on table public.reports to authenticated;
grant insert (reporter_id, target_type, target_id, reason, details) on table public.reports
  to authenticated;
grant update (status, resolution_note) on table public.reports to authenticated;

create policy reports_select_own_or_staff on public.reports
  for select to authenticated
  using (reporter_id = (select auth.uid()) or private.has_staff_role('moderator'));
create policy reports_insert_own on public.reports
  for insert to authenticated with check (reporter_id = (select auth.uid()));
create policy reports_update_staff on public.reports
  for update to authenticated
  using (private.has_staff_role('moderator')) with check (private.has_staff_role('moderator'));
