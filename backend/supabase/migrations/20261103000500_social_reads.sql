-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Social reads the app needs beyond the feed: people search, follower and
-- following lists, incoming follow requests and the notifications inbox.
--
-- All of them return public.profile_cards rows (or embed their fields), so the
-- profile privacy rules live in one place: blocked users never appear, and a
-- private profile shows strangers its name, photo and approximate location
-- only. Follower lists follow the follows RLS rule: visible when the caller
-- may see the full profile (owner, accepted follower, or an open profile).

create extension if not exists pg_trgm with schema extensions;

-- One trigram index over handle and name, matched with ILIKE.
create or replace function private.profile_search_text(p_handle extensions.citext, p_first text, p_last text)
returns text
language sql
immutable
set search_path = ''
as $$
  select lower(p_handle::text || ' ' || coalesce(p_first, '') || ' ' || coalesce(p_last, ''));
$$;

create index profiles_search_trgm_idx on public.profiles
  using gin (private.profile_search_text(handle, first_name, last_name) extensions.gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Search
-- ---------------------------------------------------------------------------

-- People whose handle or name contains the query (at least 2 characters).
-- Exact handle first, then handle prefix, then name prefix, then the rest.
-- Users blocked either way are left out. SECURITY DEFINER so the match can use
-- the trigram index on the owner-only profiles table; what each hit shows is
-- still decided by profile_cards for the calling user.
create or replace function public.search_profiles(p_query text, p_limit integer default 20)
returns setof public.profile_cards
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_q    text := lower(btrim(coalesce(p_query, '')));
  v_like text;
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if length(v_q) < 2 then
    return;
  end if;
  v_q := left(v_q, 60);
  v_like := '%' || replace(replace(replace(v_q, '\', '\\'), '%', '\%'), '_', '\_') || '%';

  return query
  select c.*
    from public.profiles p
    join public.profile_cards c on c.id = p.id
   where private.profile_search_text(p.handle, p.first_name, p.last_name) like v_like
     and c.viewer_relation <> 'blocked'
   order by (lower(p.handle::text) = v_q) desc,
            (lower(p.handle::text) like substr(v_like, 2)) desc,
            (lower(p.first_name || ' ' || p.last_name) like substr(v_like, 2)) desc,
            p.handle
   limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

comment on function public.search_profiles(text, integer) is
  'Search people by handle or name (min 2 characters). Returns profile_cards rows, blocked users excluded.';

-- ---------------------------------------------------------------------------
-- Follower / following lists
-- ---------------------------------------------------------------------------

-- Accepted followers of p_user, newest first, keyset-paginated on followed_at.
create or replace function public.get_followers(
  p_user   uuid,
  p_before timestamptz default null,
  p_limit  integer default 50
)
returns table (followed_at timestamptz, profile public.profile_cards)
language plpgsql
stable
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  return query
  select f.accepted_at, c
    from public.follows f
    join public.profile_cards c on c.id = f.follower_id
   where f.followee_id = p_user
     and f.status = 'accepted'
     and (p_before is null or f.accepted_at < p_before)
     and c.viewer_relation <> 'blocked'
   order by f.accepted_at desc
   limit least(greatest(coalesce(p_limit, 50), 1), 200);
end;
$$;

-- Accounts p_user follows (accepted), newest first.
create or replace function public.get_following(
  p_user   uuid,
  p_before timestamptz default null,
  p_limit  integer default 50
)
returns table (followed_at timestamptz, profile public.profile_cards)
language plpgsql
stable
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  return query
  select f.accepted_at, c
    from public.follows f
    join public.profile_cards c on c.id = f.followee_id
   where f.follower_id = p_user
     and f.status = 'accepted'
     and (p_before is null or f.accepted_at < p_before)
     and c.viewer_relation <> 'blocked'
   order by f.accepted_at desc
   limit least(greatest(coalesce(p_limit, 50), 1), 200);
end;
$$;

-- Incoming follow requests of the caller (private profile), oldest first.
create or replace function public.get_follow_requests(p_limit integer default 100)
returns table (requested_at timestamptz, profile public.profile_cards)
language sql
stable
set search_path = ''
as $$
  select f.created_at, c
    from public.follows f
    join public.profile_cards c on c.id = f.follower_id
   where f.followee_id = auth.uid()
     and f.status = 'pending'
   order by f.created_at
   limit least(greatest(coalesce(p_limit, 100), 1), 500);
$$;

-- ---------------------------------------------------------------------------
-- Notifications inbox
-- ---------------------------------------------------------------------------

-- The caller's inbox, newest first, with the actor's card basics and the
-- activity name (only when the caller can still see that activity).
create or replace function public.get_notifications(
  p_before timestamptz default null,
  p_limit  integer default 30
)
returns table (
  id                uuid,
  kind              public.notification_kind,
  created_at        timestamptz,
  read_at           timestamptz,
  payload           jsonb,
  actor_id          uuid,
  actor_handle      text,
  actor_first_name  text,
  actor_last_name   text,
  actor_avatar_path text,
  activity_id       uuid,
  activity_name     text,
  comment_id        uuid,
  comment_body      text
)
language sql
stable
security definer
set search_path = ''
as $$
  select n.id, n.kind, n.created_at, n.read_at, n.payload,
         n.actor_id, p.handle::text, p.first_name, p.last_name, p.avatar_path,
         case when private.can_view_activity(n.activity_id) then n.activity_id end,
         case when private.can_view_activity(n.activity_id) then a.name end,
         n.comment_id,
         case when private.can_view_activity(n.activity_id) then left(c.body, 280) end
    from public.notifications n
    left join public.profiles p on p.id = n.actor_id
    left join public.activities a on a.id = n.activity_id
    left join public.comments c on c.id = n.comment_id
   where n.recipient_id = auth.uid()
     and (p_before is null or n.created_at < p_before)
   order by n.created_at desc
   limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;

create or replace function public.unread_notification_count()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select count(*)::integer from public.notifications n
   where n.recipient_id = auth.uid() and n.read_at is null;
$$;

revoke execute on function public.search_profiles(text, integer),
  public.get_followers(uuid, timestamptz, integer), public.get_following(uuid, timestamptz, integer),
  public.get_follow_requests(integer), public.get_notifications(timestamptz, integer),
  public.unread_notification_count() from public, anon;
grant execute on function public.search_profiles(text, integer),
  public.get_followers(uuid, timestamptz, integer), public.get_following(uuid, timestamptz, integer),
  public.get_follow_requests(integer), public.get_notifications(timestamptz, integer),
  public.unread_notification_count() to authenticated, service_role;
