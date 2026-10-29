-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Read models: the ONLY way a user reads another user's profile or activities.
--
-- The views are deliberately owner-privileged (security_invoker = false) so
-- that they can read the owner-only base tables, and security barriers so
-- that caller-supplied filters cannot observe rows before the privacy filter
-- runs. Each view:
--   1. filters rows through private.viewer_relation / private.can_view_activity
--      (visibility, accepted follows, blocks);
--   2. replaces hidden stats with NULL for anyone but the owner, so hidden
--      values never leave the database;
--   3. serves the privacy-trimmed geometry to anyone but the owner.
--
-- Field rules (settings-privacy-units §6.2, §8.1):
--   start time / timezone : owner, or follower when not hidden; never strangers
--   gear                  : owner and followers only
--   heart rate (+effort)  : hidden by hide_heart_rate
--   speed / pace          : hidden by hide_pace
--   power / energy        : hidden by hide_power
--   calories              : hidden by hide_calories
--   elevation             : hidden by hide_elevation

create or replace function private.has_blocked(p_blocker uuid, p_blocked uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.blocks b
                  where b.blocker_id = p_blocker and b.blocked_id = p_blocked);
$$;

-- ---------------------------------------------------------------------------
-- profile_cards
-- ---------------------------------------------------------------------------

create view public.profile_cards
with (security_barrier = true, security_invoker = false)
as
select
  p.id,
  p.handle,
  p.first_name,
  p.last_name,
  p.avatar_path,
  p.city,
  p.region,
  p.country,
  p.profile_visibility,
  case when v.full_profile then p.bio end                         as bio,
  v.full_profile,
  v.relation                                                      as viewer_relation,
  (select f.status from public.follows f
    where f.follower_id = auth.uid() and f.followee_id = p.id)    as viewer_follow_status,
  case when v.full_profile then
    (select count(*) from public.follows f
      where f.followee_id = p.id and f.status = 'accepted') end   as follower_count,
  case when v.full_profile then
    (select count(*) from public.follows f
      where f.follower_id = p.id and f.status = 'accepted') end   as following_count
from public.profiles p
cross join lateral (
  select r.relation,
         r.relation in ('self', 'follower')
           or (r.relation in ('stranger', 'anonymous') and p.profile_visibility = 'everyone')
           as full_profile
    from (select private.viewer_relation(p.id) as relation) r
) v
where (v.relation <> 'blocked' or private.has_blocked(auth.uid(), p.id))
  and (v.relation <> 'anonymous' or p.profile_visibility = 'everyone');

comment on view public.profile_cards is
  'Profile as seen by the caller. Followers-only profiles show strangers name, photo and approximate location only. Blocked users are invisible (the blocker still sees a minimal card).';

-- ---------------------------------------------------------------------------
-- activity_cards
-- ---------------------------------------------------------------------------

create view public.activity_cards
with (security_barrier = true, security_invoker = false)
as
select
  a.id,
  a.owner_id,
  a.name,
  a.description,
  a.sport_type,
  a.workout_kind,
  a.visibility,
  a.muted,
  a.is_commute,
  a.is_indoor,
  a.is_manual,
  a.start_date_local,
  case when v.is_owner or (v.is_follower and not a.hide_start_time) then a.start_time end   as start_time,
  case when v.is_owner or (v.is_follower and not a.hide_start_time) then a.tz_name end      as tz_name,
  case when v.is_owner or (v.is_follower and not a.hide_start_time) then a.utc_offset_s end as utc_offset_s,
  a.published_at,
  case when v.is_owner or v.is_follower then a.gear_id end                                  as gear_id,
  a.elapsed_s,
  a.moving_s,
  a.distance_m,
  case when v.is_owner or not a.hide_elevation then a.elev_gain_m end        as elev_gain_m,
  case when v.is_owner or not a.hide_elevation then a.elev_loss_m end        as elev_loss_m,
  case when v.is_owner or not a.hide_elevation then a.elev_high_m end        as elev_high_m,
  case when v.is_owner or not a.hide_elevation then a.elev_low_m end         as elev_low_m,
  case when v.is_owner or not a.hide_pace then a.avg_speed_mps end           as avg_speed_mps,
  case when v.is_owner or not a.hide_pace then a.max_speed_mps end           as max_speed_mps,
  case when v.is_owner or not a.hide_heart_rate then a.avg_hr end            as avg_hr,
  case when v.is_owner or not a.hide_heart_rate then a.max_hr end            as max_hr,
  case when v.is_owner or not a.hide_heart_rate then a.effort_score end      as effort_score,
  a.avg_cadence,
  a.max_cadence,
  case when v.is_owner or not a.hide_power then a.avg_watts end              as avg_watts,
  case when v.is_owner or not a.hide_power then a.max_watts end              as max_watts,
  case when v.is_owner or not a.hide_power then a.weighted_avg_watts end     as weighted_avg_watts,
  case when v.is_owner or not a.hide_power then a.kilojoules end             as kilojoules,
  case when v.is_owner or not a.hide_calories then a.calories end            as calories,
  a.metrics_version,
  case when v.is_owner then a.route_simplified else a.route_public end              as route,
  case when v.is_owner then a.start_point else a.start_point_public end             as start_point,
  case when v.is_owner then a.end_point else a.end_point_public end                 as end_point,
  case when v.is_owner then a.summary_polyline else a.summary_polyline_public end   as summary_polyline,
  a.hide_map,
  a.hide_heart_rate,
  a.hide_pace,
  a.hide_power,
  a.hide_calories,
  a.hide_start_time,
  a.hide_elevation,
  a.high_five_count,
  a.comment_count,
  v.is_owner,
  exists (select 1 from public.high_fives h
           where h.activity_id = a.id and h.user_id = auth.uid())   as viewer_high_fived,
  a.created_at,
  a.updated_at
from public.activities a
cross join lateral (
  select r.relation = 'self'     as is_owner,
         r.relation = 'follower' as is_follower
    from (select private.viewer_relation(a.owner_id) as relation) r
) v
where a.deleted_at is null
  and private.can_view_activity(a.owner_id, a.visibility);

comment on view public.activity_cards is
  'Activities visible to the caller with hidden stats nulled and privacy-trimmed geometry for non-owners.';

-- ---------------------------------------------------------------------------
-- Laps, splits, streams
-- ---------------------------------------------------------------------------

create view public.activity_laps
with (security_barrier = true, security_invoker = false)
as
select
  l.activity_id,
  l.lap_index,
  l.name,
  l.trigger,
  l.start_offset_s,
  l.elapsed_s,
  l.moving_s,
  l.distance_m,
  case when o.is_owner or not a.hide_elevation  then l.elev_gain_m end   as elev_gain_m,
  case when o.is_owner or not a.hide_pace       then l.avg_speed_mps end as avg_speed_mps,
  case when o.is_owner or not a.hide_pace       then l.max_speed_mps end as max_speed_mps,
  case when o.is_owner or not a.hide_heart_rate then l.avg_hr end        as avg_hr,
  case when o.is_owner or not a.hide_heart_rate then l.max_hr end        as max_hr,
  l.avg_cadence,
  case when o.is_owner or not a.hide_power      then l.avg_watts end     as avg_watts,
  case when o.is_owner or not a.hide_power      then l.max_watts end     as max_watts
from public.laps l
join public.activities a on a.id = l.activity_id
cross join lateral (select a.owner_id = auth.uid() as is_owner) o
where a.deleted_at is null
  and private.can_view_activity(a.owner_id, a.visibility);

create view public.activity_splits
with (security_barrier = true, security_invoker = false)
as
select
  s.activity_id,
  s.unit,
  s.split_index,
  s.distance_m,
  s.elapsed_s,
  s.moving_s,
  case when o.is_owner or not a.hide_elevation  then s.elev_diff_m end   as elev_diff_m,
  case when o.is_owner or not a.hide_pace       then s.avg_speed_mps end as avg_speed_mps,
  case when o.is_owner or not a.hide_pace       then s.gap_speed_mps end as gap_speed_mps,
  case when o.is_owner or not a.hide_heart_rate then s.avg_hr end        as avg_hr,
  case when o.is_owner or not a.hide_power      then s.avg_watts end     as avg_watts
from public.splits s
join public.activities a on a.id = s.activity_id
cross join lateral (select a.owner_id = auth.uid() as is_owner) o
where a.deleted_at is null
  and private.can_view_activity(a.owner_id, a.visibility);

-- Non-owners get the worker-built public derivative and a channel list with
-- hidden channels removed; the owner's full-resolution path is never exposed.
create view public.activity_stream_files
with (security_barrier = true, security_invoker = false)
as
select
  st.activity_id,
  st.bucket,
  case when o.is_owner then st.owner_path else st.public_path end as path,
  st.formats,
  case when o.is_owner then st.stream_types else array(
    select t from unnest(st.stream_types) t
     where not (t = 'heartrate' and a.hide_heart_rate)
       and not (t = 'watts' and a.hide_power)
       and not (t in ('velocity_smooth') and a.hide_pace)
       and not (t in ('altitude', 'grade_smooth') and a.hide_elevation)
       and not (t = 'latlng' and a.hide_map)) end                  as stream_types,
  st.sample_count,
  st.metrics_version,
  st.updated_at
from public.activity_streams st
join public.activities a on a.id = st.activity_id
cross join lateral (select a.owner_id = auth.uid() as is_owner) o
where a.deleted_at is null
  and private.can_view_activity(a.owner_id, a.visibility)
  and (o.is_owner or st.public_path is not null);

revoke all on public.profile_cards, public.activity_cards, public.activity_laps,
  public.activity_splits, public.activity_stream_files from anon, authenticated;
grant select on public.profile_cards, public.activity_cards, public.activity_laps,
  public.activity_splits, public.activity_stream_files to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Home feed (ADR-013: chronological by default)
-- ---------------------------------------------------------------------------

-- Activities of the caller and of accounts they follow (accepted), newest
-- first by publish time, keyset-paginated on (published_at, id). Muted
-- activities and muted accounts never appear. Visibility, blocks and hidden
-- stats are enforced by activity_cards.
create or replace function public.get_home_feed(
  p_before_published_at timestamptz default null,
  p_before_id           uuid default null,
  p_limit               integer default 20
)
returns setof public.activity_cards
language plpgsql
stable
set search_path = ''
as $$
declare
  v_uid    uuid := auth.uid();
  v_owners uuid[];
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;

  -- Resolve the audience first so the owner filter is a plain array match the
  -- planner can push into the security-barrier view (and use the feed index).
  select array_agg(x.id) into v_owners
    from (
      select v_uid as id
      union
      select f.followee_id from public.follows f
       where f.follower_id = v_uid and f.status = 'accepted'
    ) x
   where not exists (select 1 from public.mutes m
                      where m.muter_id = v_uid and m.muted_id = x.id);

  return query
  select c.*
    from public.activity_cards c
   where c.owner_id = any (v_owners)
     and not c.muted
     and (p_before_published_at is null
          or (c.published_at, c.id) < (p_before_published_at,
                                       coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
   order by c.published_at desc, c.id desc
   limit least(greatest(coalesce(p_limit, 20), 1), 100);
end;
$$;

comment on function public.get_home_feed(timestamptz, uuid, integer) is
  'Chronological home feed (ADR-013). Pass the last row''s published_at and id to page.';

revoke execute on function public.get_home_feed(timestamptz, uuid, integer) from public, anon;
grant execute on function public.get_home_feed(timestamptz, uuid, integer) to authenticated;

-- Activities on one athlete's profile page (respects visibility, includes muted ones).
create or replace function public.get_profile_activities(
  p_owner               uuid,
  p_before_published_at timestamptz default null,
  p_before_id           uuid default null,
  p_limit               integer default 20
)
returns setof public.activity_cards
language sql
stable
set search_path = ''
as $$
  select c.*
    from public.activity_cards c
   where c.owner_id = p_owner
     and (p_before_published_at is null
          or (c.published_at, c.id) < (p_before_published_at,
                                       coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
   order by c.published_at desc, c.id desc
   limit least(greatest(coalesce(p_limit, 20), 1), 100);
$$;

grant execute on function public.get_profile_activities(uuid, timestamptz, uuid, integer)
  to anon, authenticated;
