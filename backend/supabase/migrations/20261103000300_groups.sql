-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Groups: small private clubs joined by invite link, with a weekly leaderboard
-- and group challenges.
--
-- Membership
--   * Anyone signed in can create a group (name, optional sport, description,
--     avatar, timezone). The creator is its owner.
--   * People join with an invite link carrying a random, unguessable token
--     (144 bits). Every member can see the current token to share it; owners
--     and admins can rotate it (old links stop working) or turn invites off.
--   * Roles: owner (exactly one), admin, member. Members can leave; owners can
--     remove anyone, admins can remove members. The owner cannot leave without
--     handing ownership on first, or deletes the group.
--   * Groups and their members are visible to members only.
--
-- Leaderboard and challenge visibility rule (decided 2026-11-03)
--   A member's activity counts in what viewer V sees only when V may see that
--   activity anyway: private.can_view_activity() as V. So 'everyone'
--   activities count for all members, 'followers' activities only for members
--   who follow the athlete, 'only_me' activities only for the athlete. Each
--   viewer can therefore see different totals for the same athlete, but no
--   leaderboard ever reveals an activity its viewer could not open. Elevation
--   is counted only when not hidden (hide_elevation) or for the athlete.
--   Members blocked with the viewer (either way) are left out entirely.
--   When the group has a sport, only activities of that sport count.

create type public.group_role as enum ('owner', 'admin', 'member');

create type public.challenge_metric as enum ('distance', 'moving_time', 'elevation', 'activity_count');

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.groups (
  id                uuid primary key default gen_random_uuid(),
  owner_id          uuid not null references public.profiles (id) on delete cascade,
  name              text not null check (length(btrim(name)) between 1 and 80),
  sport             public.sport_type,  -- null: all sports count
  description       text check (length(description) <= 1000),
  avatar_path       text check (length(avatar_path) <= 512),
  timezone          text not null default 'UTC' check (length(timezone) <= 64),
  invite_token      text unique check (invite_token ~ '^[A-Za-z0-9_-]{24}$'),  -- null: invites off
  invite_rotated_at timestamptz,
  member_count      integer not null default 0 check (member_count >= 0),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.groups is
  'Private groups joined by invite token. Visible to members only.';
comment on column public.groups.timezone is
  'IANA zone used for leaderboard weeks and challenge dates.';

create index groups_owner_idx on public.groups (owner_id);

create table public.group_members (
  group_id  uuid not null references public.groups (id) on delete cascade,
  user_id   uuid not null references public.profiles (id) on delete cascade,
  role      public.group_role not null default 'member',
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

create index group_members_user_idx on public.group_members (user_id);
create unique index group_members_one_owner on public.group_members (group_id) where role = 'owner';

create table public.group_challenges (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups (id) on delete cascade,
  created_by  uuid references public.profiles (id) on delete set null,
  title       text not null check (length(btrim(title)) between 1 and 80),
  description text check (length(description) <= 1000),
  metric      public.challenge_metric not null,
  -- Metres for distance and elevation, seconds for moving_time, a count for activity_count.
  target      double precision not null check (target > 0 and target < 1e9),
  sport       public.sport_type,  -- null: the group's sport (or all sports)
  starts_on   date not null,
  ends_on     date not null,
  created_at  timestamptz not null default now(),
  check (ends_on >= starts_on and ends_on - starts_on <= 366)
);

comment on table public.group_challenges is
  'A collective target over a date range (inclusive, in the group timezone). Progress sums members'' qualifying activities.';

create index group_challenges_group_idx on public.group_challenges (group_id, ends_on desc);
create index group_challenges_created_by_idx on public.group_challenges (created_by);

-- ---------------------------------------------------------------------------
-- Helpers and triggers
-- ---------------------------------------------------------------------------

-- The caller's role in a group, or NULL when not a member. SECURITY DEFINER so
-- policies on group_members can use it without recursing into themselves.
create or replace function private.group_role(p_group uuid)
returns public.group_role
language sql
stable
security definer
set search_path = ''
as $$
  select m.role from public.group_members m
   where m.group_id = p_group and m.user_id = auth.uid();
$$;

create or replace function private.new_invite_token()
returns text
language sql
volatile
set search_path = ''
as $$
  select translate(encode(extensions.gen_random_bytes(18), 'base64'), '+/', '-_');
$$;

-- Validates the timezone (it must be one Postgres knows) and stamps updated_at.
create or replace function private.groups_before_write()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not exists (select 1 from pg_catalog.pg_timezone_names t where t.name = new.timezone) then
    raise exception 'unknown timezone %', new.timezone using errcode = 'invalid_parameter_value';
  end if;
  new.name := btrim(new.name);
  if tg_op = 'UPDATE' then
    new.updated_at := now();
  end if;
  return new;
end;
$$;

create trigger groups_before_write before insert or update on public.groups
  for each row execute function private.groups_before_write();

create or replace function private.group_members_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.groups g
     set member_count = (select count(*) from public.group_members m where m.group_id = g.id)
   where g.id = coalesce(new.group_id, old.group_id);
  return null;
end;
$$;

create trigger group_members_count
  after insert or delete on public.group_members
  for each row execute function private.group_members_count();

-- Challenges: only owners and admins create them; created_by is the caller.
create or replace function private.group_challenges_before_insert()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.created_by := coalesce(auth.uid(), new.created_by);
  new.title := btrim(new.title);
  return new;
end;
$$;

create trigger group_challenges_before_insert before insert on public.group_challenges
  for each row execute function private.group_challenges_before_insert();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.groups           enable row level security;
alter table public.group_members    enable row level security;
alter table public.group_challenges enable row level security;

revoke all on table public.groups, public.group_members, public.group_challenges
  from anon, authenticated;
grant all on table public.groups, public.group_members, public.group_challenges to service_role;

-- groups: members read; owner/admin edit the profile fields; owner deletes.
-- Created only through create_group (it also adds the owner membership).
grant select, delete on table public.groups to authenticated;
grant update (name, sport, description, avatar_path, timezone) on table public.groups to authenticated;

create policy groups_select_members on public.groups
  for select to authenticated using (private.group_role(id) is not null);
create policy groups_update_admins on public.groups
  for update to authenticated
  using (private.group_role(id) in ('owner', 'admin'))
  with check (private.group_role(id) in ('owner', 'admin'));
create policy groups_delete_owner on public.groups
  for delete to authenticated using (private.group_role(id) = 'owner');

-- group_members: members see each other. Removal by row delete:
--   a member or admin may remove themselves (leave), never the owner;
--   the owner may remove anyone else; an admin may remove plain members.
-- Joining and role changes go through RPCs.
grant select, delete on table public.group_members to authenticated;

create policy group_members_select on public.group_members
  for select to authenticated using (private.group_role(group_id) is not null);
create policy group_members_delete on public.group_members
  for delete to authenticated
  using (
    (user_id = (select auth.uid()) and role <> 'owner')
    or (user_id <> (select auth.uid()) and private.group_role(group_id) = 'owner')
    or (role = 'member' and private.group_role(group_id) = 'admin'));

-- group_challenges: members read; owner/admin create, edit and delete.
grant select, delete on table public.group_challenges to authenticated;
grant insert (group_id, title, description, metric, target, sport, starts_on, ends_on)
  on table public.group_challenges to authenticated;
grant update (title, description, metric, target, sport, starts_on, ends_on)
  on table public.group_challenges to authenticated;

create policy group_challenges_select on public.group_challenges
  for select to authenticated using (private.group_role(group_id) is not null);
create policy group_challenges_insert on public.group_challenges
  for insert to authenticated with check (private.group_role(group_id) in ('owner', 'admin'));
create policy group_challenges_update on public.group_challenges
  for update to authenticated
  using (private.group_role(group_id) in ('owner', 'admin'))
  with check (private.group_role(group_id) in ('owner', 'admin'));
create policy group_challenges_delete on public.group_challenges
  for delete to authenticated using (private.group_role(group_id) in ('owner', 'admin'));

-- ---------------------------------------------------------------------------
-- RPCs: create, invite, join, roles
-- ---------------------------------------------------------------------------

create or replace function public.create_group(
  p_name        text,
  p_sport       public.sport_type default null,
  p_description text default null,
  p_timezone    text default 'UTC',
  p_avatar_path text default null
)
returns public.groups
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.groups;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if (select count(*) from public.groups g where g.owner_id = v_uid) >= 50 then
    raise exception 'you can own at most 50 groups' using errcode = 'program_limit_exceeded';
  end if;

  insert into public.groups (owner_id, name, sport, description, avatar_path, timezone,
                             invite_token, invite_rotated_at)
  values (v_uid, p_name, p_sport, nullif(btrim(p_description), ''), nullif(p_avatar_path, ''),
          coalesce(nullif(p_timezone, ''), 'UTC'), private.new_invite_token(), now())
  returning * into v_row;

  insert into public.group_members (group_id, user_id, role) values (v_row.id, v_uid, 'owner');

  select * into v_row from public.groups g where g.id = v_row.id;
  return v_row;
end;
$$;

comment on function public.create_group(text, public.sport_type, text, text, text) is
  'Create a group owned by the caller, with an invite token. Returns the group row.';

-- New token (old links stop working). Owner or admin.
create or replace function public.rotate_group_invite(p_group uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_token text := private.new_invite_token();
begin
  if private.group_role(p_group) is distinct from 'owner'
     and private.group_role(p_group) is distinct from 'admin' then
    raise exception 'group not found' using errcode = 'no_data_found';
  end if;
  update public.groups set invite_token = v_token, invite_rotated_at = now() where id = p_group;
  return v_token;
end;
$$;

-- Turn invites off until the next rotate. Owner or admin.
create or replace function public.revoke_group_invite(p_group uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  if private.group_role(p_group) is distinct from 'owner'
     and private.group_role(p_group) is distinct from 'admin' then
    raise exception 'group not found' using errcode = 'no_data_found';
  end if;
  update public.groups set invite_token = null, invite_rotated_at = now() where id = p_group;
  return true;
end;
$$;

-- What an invite link shows before joining. Works signed out (the join web
-- page uses it). Holding the token is the permission to see this much.
create or replace function public.get_group_invite(p_token text)
returns table (
  group_id     uuid,
  name         text,
  sport        public.sport_type,
  description  text,
  avatar_path  text,
  member_count integer,
  is_member    boolean
)
language sql
stable
security definer
set search_path = ''
as $$
  select g.id, g.name, g.sport, g.description, g.avatar_path, g.member_count,
         exists (select 1 from public.group_members m
                  where m.group_id = g.id and m.user_id = auth.uid())
    from public.groups g
   where p_token ~ '^[A-Za-z0-9_-]{24}$'
     and g.invite_token = p_token;
$$;

-- Join by invite token. Idempotent: joining again returns the same group.
-- Someone blocked with the owner cannot join (same error as a bad token).
create or replace function public.join_group(p_token text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := auth.uid();
  v_group public.groups;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;

  select * into v_group from public.groups g
   where p_token ~ '^[A-Za-z0-9_-]{24}$' and g.invite_token = p_token;
  if v_group.id is null or private.is_blocked_between(v_uid, v_group.owner_id) then
    raise exception 'invite not found or no longer valid' using errcode = 'no_data_found';
  end if;
  if v_group.member_count >= 1000
     and not exists (select 1 from public.group_members m
                      where m.group_id = v_group.id and m.user_id = v_uid) then
    raise exception 'this group is full (1000 members)' using errcode = 'program_limit_exceeded';
  end if;

  insert into public.group_members (group_id, user_id, role)
  values (v_group.id, v_uid, 'member')
  on conflict (group_id, user_id) do nothing;
  return v_group.id;
end;
$$;

-- Owner only. 'admin' / 'member' change a role; 'owner' hands ownership over
-- (the previous owner becomes an admin).
create or replace function public.set_group_member_role(
  p_group uuid,
  p_user  uuid,
  p_role  public.group_role
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if private.group_role(p_group) is distinct from 'owner' then
    raise exception 'only the group owner can change roles' using errcode = 'insufficient_privilege';
  end if;
  if p_user = v_uid then
    raise exception 'hand ownership to another member instead' using errcode = 'check_violation';
  end if;
  if not exists (select 1 from public.group_members m
                  where m.group_id = p_group and m.user_id = p_user) then
    raise exception 'member not found' using errcode = 'no_data_found';
  end if;

  if p_role = 'owner' then
    update public.group_members set role = 'admin'
     where group_id = p_group and user_id = v_uid;
    update public.group_members set role = 'owner'
     where group_id = p_group and user_id = p_user;
    update public.groups set owner_id = p_user where id = p_group;
  else
    update public.group_members set role = p_role
     where group_id = p_group and user_id = p_user;
  end if;
  return true;
end;
$$;

-- Members with their public card basics, for the member list. Members blocked
-- with the caller are left out.
create or replace function public.get_group_members(p_group uuid)
returns table (
  user_id     uuid,
  role        public.group_role,
  joined_at   timestamptz,
  handle      text,
  first_name  text,
  last_name   text,
  avatar_path text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.group_role(p_group) is null then
    raise exception 'group not found' using errcode = 'no_data_found';
  end if;
  return query
  select m.user_id, m.role, m.joined_at, p.handle::text, p.first_name, p.last_name, p.avatar_path
    from public.group_members m
    join public.profiles p on p.id = m.user_id
   where m.group_id = p_group
     and not private.is_blocked_between(auth.uid(), m.user_id)
   order by case m.role when 'owner' then 0 when 'admin' then 1 else 2 end, m.joined_at;
end;
$$;

-- ---------------------------------------------------------------------------
-- Leaderboard and challenges
-- ---------------------------------------------------------------------------

-- Per-member totals over a local-date range [p_from, p_to] (inclusive, in the
-- group timezone), counting only what the caller may see (rule at the top).
create or replace function private.group_member_totals(
  p_group uuid,
  p_from  date,
  p_to    date,
  p_sport public.sport_type
)
returns table (
  user_id        uuid,
  distance_m     double precision,
  moving_s       bigint,
  elev_gain_m    double precision,
  activity_count bigint
)
language sql
stable
security definer
set search_path = ''
as $$
  select m.user_id,
         coalesce(sum(a.distance_m), 0),
         coalesce(sum(a.moving_s), 0)::bigint,
         coalesce(sum(case when a.owner_id = auth.uid() or not a.hide_elevation
                           then a.elev_gain_m end), 0),
         count(a.id)
    from public.group_members m
    join public.groups g on g.id = m.group_id
    left join public.activities a
           on a.owner_id = m.user_id
          and a.deleted_at is null
          and (p_sport is null or a.sport_type = p_sport)
          and (a.start_time at time zone g.timezone)::date between p_from and p_to
          and private.can_view_activity(a.owner_id, a.visibility)
   where m.group_id = p_group
     and not private.is_blocked_between(auth.uid(), m.user_id)
   group by m.user_id;
$$;

revoke execute on function private.group_member_totals(uuid, date, date, public.sport_type)
  from public, anon, authenticated;

-- Weekly leaderboard: the ISO week (Monday to Sunday) containing p_day in the
-- group's timezone; today when p_day is NULL. Every visible member is listed,
-- with zero totals when they have no counted activity, ranked three ways.
create or replace function public.get_group_leaderboard(p_group uuid, p_day date default null)
returns table (
  user_id          uuid,
  handle           text,
  first_name       text,
  last_name        text,
  avatar_path      text,
  distance_m       double precision,
  moving_s         bigint,
  elev_gain_m      double precision,
  activity_count   bigint,
  rank_distance    bigint,
  rank_moving_time bigint,
  rank_elevation   bigint,
  week_start       date,
  week_end         date
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_tz    text;
  v_sport public.sport_type;
  v_start date;
begin
  if private.group_role(p_group) is null then
    raise exception 'group not found' using errcode = 'no_data_found';
  end if;
  select g.timezone, g.sport into v_tz, v_sport from public.groups g where g.id = p_group;
  v_start := date_trunc('week', coalesce(p_day, (now() at time zone v_tz)::date))::date;

  return query
  select t.user_id, p.handle::text, p.first_name, p.last_name, p.avatar_path,
         t.distance_m, t.moving_s, t.elev_gain_m, t.activity_count,
         rank() over (order by t.distance_m desc),
         rank() over (order by t.moving_s desc),
         rank() over (order by t.elev_gain_m desc),
         v_start, v_start + 6
    from private.group_member_totals(p_group, v_start, v_start + 6, v_sport) t
    join public.profiles p on p.id = t.user_id
   order by t.distance_m desc, t.moving_s desc, p.handle;
end;
$$;

comment on function public.get_group_leaderboard(uuid, date) is
  'Members ranked by distance, moving time and elevation for the ISO week (group timezone). Counts only activities the caller may see.';

-- A group's challenges with the collective progress the caller can see.
create or replace function public.get_group_challenges(p_group uuid)
returns table (
  id          uuid,
  title       text,
  description text,
  metric      public.challenge_metric,
  target      double precision,
  sport       public.sport_type,
  starts_on   date,
  ends_on     date,
  created_by  uuid,
  progress    double precision,
  completed   boolean,
  status      text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_today date;
begin
  if private.group_role(p_group) is null then
    raise exception 'group not found' using errcode = 'no_data_found';
  end if;
  select (now() at time zone g.timezone)::date into v_today from public.groups g where g.id = p_group;

  return query
  select c.id, c.title, c.description, c.metric, c.target, c.sport, c.starts_on, c.ends_on,
         c.created_by, pr.total, pr.total >= c.target,
         case when v_today < c.starts_on then 'upcoming'
              when v_today > c.ends_on then 'finished'
              else 'active' end
    from public.group_challenges c
    join public.groups g on g.id = c.group_id
    cross join lateral (
      select coalesce(sum(case c.metric
                            when 'distance'       then t.distance_m
                            when 'moving_time'    then t.moving_s::double precision
                            when 'elevation'      then t.elev_gain_m
                            when 'activity_count' then t.activity_count::double precision
                          end), 0) as total
        from private.group_member_totals(c.group_id, c.starts_on, c.ends_on,
                                         coalesce(c.sport, g.sport)) t
    ) pr
   where c.group_id = p_group
   order by (v_today > c.ends_on), c.ends_on, c.created_at;
end;
$$;

-- One challenge's progress per member (what the caller may see), largest first.
create or replace function public.get_challenge_progress(p_challenge uuid)
returns table (
  user_id      uuid,
  handle       text,
  first_name   text,
  last_name    text,
  avatar_path  text,
  value        double precision,
  share        double precision,
  rank         bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  c public.group_challenges;
  v_sport public.sport_type;
begin
  select * into c from public.group_challenges where id = p_challenge;
  if c.id is null or private.group_role(c.group_id) is null then
    raise exception 'challenge not found' using errcode = 'no_data_found';
  end if;
  select coalesce(c.sport, g.sport) into v_sport from public.groups g where g.id = c.group_id;

  return query
  with v as (
    select t.user_id,
           case c.metric
             when 'distance'       then t.distance_m
             when 'moving_time'    then t.moving_s::double precision
             when 'elevation'      then t.elev_gain_m
             when 'activity_count' then t.activity_count::double precision
           end as value
      from private.group_member_totals(c.group_id, c.starts_on, c.ends_on, v_sport) t
  )
  select v.user_id, p.handle::text, p.first_name, p.last_name, p.avatar_path, v.value,
         v.value / c.target, rank() over (order by v.value desc)
    from v join public.profiles p on p.id = v.user_id
   order by v.value desc, p.handle;
end;
$$;

revoke execute on function
  public.create_group(text, public.sport_type, text, text, text),
  public.rotate_group_invite(uuid), public.revoke_group_invite(uuid),
  public.join_group(text), public.set_group_member_role(uuid, uuid, public.group_role),
  public.get_group_members(uuid), public.get_group_leaderboard(uuid, date),
  public.get_group_challenges(uuid), public.get_challenge_progress(uuid)
  from public, anon;
grant execute on function
  public.create_group(text, public.sport_type, text, text, text),
  public.rotate_group_invite(uuid), public.revoke_group_invite(uuid),
  public.join_group(text), public.set_group_member_role(uuid, uuid, public.group_role),
  public.get_group_members(uuid), public.get_group_leaderboard(uuid, date),
  public.get_group_challenges(uuid), public.get_challenge_progress(uuid)
  to authenticated, service_role;

revoke execute on function public.get_group_invite(text) from public;
grant execute on function public.get_group_invite(text) to anon, authenticated, service_role;

do $$ begin perform private.assert_rls_everywhere(); end $$;
