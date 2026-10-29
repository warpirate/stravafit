-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Social graph: follows (with requests for followers-only profiles), blocks and
-- mutes, plus the relationship predicates every RLS policy builds on.
--
-- Writes to `follows` go through RPCs (20261029001100_social_rpcs.sql) so that a
-- client can never insert an `accepted` row into a followers-only profile.

create table public.follows (
  follower_id uuid not null references public.profiles (id) on delete cascade,
  followee_id uuid not null references public.profiles (id) on delete cascade,
  status      public.follow_status not null,
  created_at  timestamptz not null default now(),
  accepted_at timestamptz,
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id),
  check ((status = 'accepted') = (accepted_at is not null))
);

comment on table public.follows is
  'Directed follow edges. pending = request awaiting approval by a followers-only profile.';

create index follows_followee_idx on public.follows (followee_id, status);

-- Blocks are silent (the blocked user is never told) and symmetric in effect.
create table public.blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

create index blocks_blocked_idx on public.blocks (blocked_id);

-- Mutes hide a user's activities from the muter's feed. The muted user is not told.
create table public.mutes (
  muter_id   uuid not null references public.profiles (id) on delete cascade,
  muted_id   uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (muter_id, muted_id),
  check (muter_id <> muted_id)
);

create index mutes_muted_idx on public.mutes (muted_id);

-- ---------------------------------------------------------------------------
-- Relationship predicates (SECURITY DEFINER so they can read rows the caller
-- cannot, e.g. a block placed *on* the caller). They only return booleans.
-- ---------------------------------------------------------------------------

create or replace function private.is_blocked_between(p_a uuid, p_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_a is not null and p_b is not null and exists (
    select 1 from public.blocks b
    where (b.blocker_id = p_a and b.blocked_id = p_b)
       or (b.blocker_id = p_b and b.blocked_id = p_a));
$$;

create or replace function private.is_accepted_follower(p_follower uuid, p_followee uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_follower is not null and exists (
    select 1 from public.follows f
    where f.follower_id = p_follower and f.followee_id = p_followee
      and f.status = 'accepted');
$$;

-- Relation of the current viewer (auth.uid()) to a content owner:
--   self | follower | stranger | blocked | anonymous
create or replace function private.viewer_relation(p_owner uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when auth.uid() is null then 'anonymous'
    when auth.uid() = p_owner then 'self'
    when private.is_blocked_between(auth.uid(), p_owner) then 'blocked'
    when private.is_accepted_follower(auth.uid(), p_owner) then 'follower'
    else 'stranger'
  end;
$$;

comment on function private.viewer_relation(uuid) is
  'Single source of truth for viewer-to-owner relation used by RLS and read models.';

-- Full profile page (bio, follower lists, counts): owner, accepted followers,
-- or anyone when the profile is public. Never across a block.
create or replace function private.can_view_full_profile(p_owner uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case private.viewer_relation(p_owner)
    when 'self'     then true
    when 'follower' then true
    when 'blocked'  then false
    else coalesce((select p.profile_visibility = 'everyone'
                     from public.profiles p where p.id = p_owner), false)
  end;
$$;

-- A block removes follow edges in both directions (social.md §12.1).
create or replace function private.on_block_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.follows f
  where (f.follower_id = new.blocker_id and f.followee_id = new.blocked_id)
     or (f.follower_id = new.blocked_id and f.followee_id = new.blocker_id);
  return null;
end;
$$;

create trigger blocks_remove_follows
  after insert on public.blocks
  for each row execute function private.on_block_created();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.follows enable row level security;
alter table public.blocks  enable row level security;
alter table public.mutes   enable row level security;

revoke all on table public.follows, public.blocks, public.mutes from anon, authenticated;
grant all on table public.follows, public.blocks, public.mutes to service_role;

-- follows: both parties see their own edges (incoming requests included).
-- Other users see an accepted edge only when they may see the full profile on
-- both ends (so followers-only profiles keep their lists private) and are not
-- blocked with either party.
grant select on table public.follows to authenticated;

create policy follows_select_parties on public.follows
  for select to authenticated
  using ((select auth.uid()) in (follower_id, followee_id));

create policy follows_select_accepted_visible on public.follows
  for select to authenticated
  using (
    status = 'accepted'
    and private.can_view_full_profile(follower_id)
    and private.can_view_full_profile(followee_id));

-- blocks / mutes: strictly owner-only; the other party can never see them.
grant select, insert, delete on table public.blocks to authenticated;

create policy blocks_select_own on public.blocks
  for select to authenticated using (blocker_id = (select auth.uid()));
create policy blocks_insert_own on public.blocks
  for insert to authenticated with check (blocker_id = (select auth.uid()));
create policy blocks_delete_own on public.blocks
  for delete to authenticated using (blocker_id = (select auth.uid()));

grant select, insert, delete on table public.mutes to authenticated;

create policy mutes_select_own on public.mutes
  for select to authenticated using (muter_id = (select auth.uid()));
create policy mutes_insert_own on public.mutes
  for insert to authenticated with check (muter_id = (select auth.uid()));
create policy mutes_delete_own on public.mutes
  for delete to authenticated using (muter_id = (select auth.uid()));
