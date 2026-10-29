-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Social RPCs. Follow edges are only written here so the server decides
-- between `accepted` and `pending` (followers-only profiles need approval).

create or replace function public.follow_user(p_target uuid)
returns public.follow_status
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := auth.uid();
  v_vis    public.profile_visibility;
  v_status public.follow_status;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if p_target = v_uid then
    raise exception 'you cannot follow yourself' using errcode = 'check_violation';
  end if;

  select p.profile_visibility into v_vis from public.profiles p where p.id = p_target;
  -- Same error for "missing" and "blocked" so a block is never revealed.
  if v_vis is null or private.is_blocked_between(v_uid, p_target) then
    raise exception 'profile not found' using errcode = 'no_data_found';
  end if;

  insert into public.follows (follower_id, followee_id, status, accepted_at)
  values (v_uid, p_target,
          case when v_vis = 'followers' then 'pending' else 'accepted' end::public.follow_status,
          case when v_vis = 'followers' then null else now() end)
  on conflict (follower_id, followee_id) do nothing
  returning status into v_status;

  if v_status is null then
    select f.status into v_status from public.follows f
     where f.follower_id = v_uid and f.followee_id = p_target;
    return v_status;  -- idempotent repeat
  end if;

  perform private.notify(p_target,
    case when v_status = 'pending' then 'follow_request' else 'follow' end::public.notification_kind,
    v_uid);
  return v_status;
end;
$$;

comment on function public.follow_user(uuid) is
  'Follow an athlete. Returns accepted, or pending when their profile is followers-only. Idempotent.';

-- Unfollow, or cancel a pending request.
create or replace function public.unfollow_user(p_target uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_found boolean;
begin
  delete from public.follows f
   where f.follower_id = auth.uid() and f.followee_id = p_target;
  get diagnostics v_found = row_count;
  return v_found;
end;
$$;

-- Approve or decline an incoming request.
create or replace function public.respond_follow_request(p_follower uuid, p_approve boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := auth.uid();
  v_found boolean;
begin
  if p_approve then
    update public.follows f
       set status = 'accepted', accepted_at = now()
     where f.follower_id = p_follower and f.followee_id = v_uid and f.status = 'pending';
    get diagnostics v_found = row_count;
    if v_found then
      perform private.notify(p_follower, 'follow_accepted', v_uid);
    end if;
  else
    delete from public.follows f
     where f.follower_id = p_follower and f.followee_id = v_uid and f.status = 'pending';
    get diagnostics v_found = row_count;
  end if;

  -- The request notification has served its purpose either way.
  delete from public.notifications n
   where n.recipient_id = v_uid and n.actor_id = p_follower and n.kind = 'follow_request';
  return v_found;
end;
$$;

-- Remove someone from your followers (they may follow again, unlike a block).
create or replace function public.remove_follower(p_follower uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_found boolean;
begin
  delete from public.follows f
   where f.follower_id = p_follower and f.followee_id = auth.uid();
  get diagnostics v_found = row_count;
  return v_found;
end;
$$;

-- Mark some (or, with NULL, all) of the caller's notifications as read.
create or replace function public.mark_notifications_read(p_ids uuid[] default null)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  update public.notifications n
     set read_at = now()
   where n.recipient_id = auth.uid()
     and n.read_at is null
     and (p_ids is null or n.id = any (p_ids));
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke execute on function public.follow_user(uuid), public.unfollow_user(uuid),
  public.respond_follow_request(uuid, boolean), public.remove_follower(uuid),
  public.mark_notifications_read(uuid[]) from public, anon;
grant execute on function public.follow_user(uuid), public.unfollow_user(uuid),
  public.respond_follow_request(uuid, boolean), public.remove_follower(uuid),
  public.mark_notifications_read(uuid[]) to authenticated, service_role;

-- Switching a profile from followers-only to everyone accepts waiting requests.
create or replace function private.profiles_accept_pending_on_public()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if old.profile_visibility = 'followers' and new.profile_visibility = 'everyone' then
    update public.follows f
       set status = 'accepted', accepted_at = now()
     where f.followee_id = new.id and f.status = 'pending';
  end if;
  return null;
end;
$$;

create trigger profiles_accept_pending_on_public
  after update of profile_visibility on public.profiles
  for each row execute function private.profiles_accept_pending_on_public();
