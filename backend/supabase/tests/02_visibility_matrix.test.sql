-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Activity visibility matrix: viewer relation x activity visibility.
--
--                 everyone  followers  only_me
--   owner            y          y         y
--   follower         y          y         -
--   pending req.     y          -         -
--   stranger         y          -         -
--   blocked          -          -         -
--   anonymous        y          -         -
begin;
select plan(19);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('owner',    tests.create_user('vm_owner')),
  ('follower', tests.create_user('vm_follower')),
  ('pending',  tests.create_user('vm_pending')),
  ('stranger', tests.create_user('vm_stranger')),
  ('blocked',  tests.create_user('vm_blocked'));

insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'follower'), (select id from ids where k = 'owner'), 'accepted', now();
insert into public.follows (follower_id, followee_id, status)
select (select id from ids where k = 'pending'), (select id from ids where k = 'owner'), 'pending';
-- The blocked user followed the owner before being blocked; the block removes it.
insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'blocked'), (select id from ids where k = 'owner'), 'accepted', now();
insert into public.blocks (blocker_id, blocked_id)
select (select id from ids where k = 'owner'), (select id from ids where k = 'blocked');

insert into ids values
  ('a_everyone',  tests.create_activity((select id from ids where k = 'owner'), 'everyone',  'A everyone')),
  ('a_followers', tests.create_activity((select id from ids where k = 'owner'), 'followers', 'B followers')),
  ('a_only_me',   tests.create_activity((select id from ids where k = 'owner'), 'only_me',   'C only me'));

insert into public.laps (activity_id, lap_index, start_offset_s, elapsed_s, moving_s, distance_m)
select id, 0, 0, 600, 590, 1000 from ids where k like 'a\_%';

-- owner
select tests.authenticate_as((select id from ids where k = 'owner'));
select results_eq($$select name from public.activity_cards where owner_id = (select id from ids where k = 'owner') order by name$$,
  array['A everyone', 'B followers', 'C only me'], 'owner sees all three');
select is((select count(*)::int from public.activities), 3, 'owner reads own base rows');
select is((select count(*)::int from public.activity_laps where activity_id in (select id from ids)), 3, 'owner sees all laps');

-- follower
select tests.authenticate_as((select id from ids where k = 'follower'));
select results_eq($$select name from public.activity_cards where owner_id = (select id from ids where k = 'owner') order by name$$,
  array['A everyone', 'B followers'], 'follower sees everyone + followers');
select is((select count(*)::int from public.activities), 0, 'follower cannot read the base table');
select is((select count(*)::int from public.activity_laps where activity_id in (select id from ids)), 2, 'follower sees laps of visible activities');

-- pending request
select tests.authenticate_as((select id from ids where k = 'pending'));
select results_eq($$select name from public.activity_cards where owner_id = (select id from ids where k = 'owner') order by name$$,
  array['A everyone'], 'pending requester sees only everyone');

-- stranger
select tests.authenticate_as((select id from ids where k = 'stranger'));
select results_eq($$select name from public.activity_cards where owner_id = (select id from ids where k = 'owner') order by name$$,
  array['A everyone'], 'stranger sees only everyone');
select is((select count(*)::int from public.activities), 0, 'stranger cannot read the base table');
select is((select count(*)::int from public.activity_laps where activity_id in (select id from ids)), 1, 'stranger sees laps of the public activity only');

-- blocked
select tests.authenticate_as((select id from ids where k = 'blocked'));
select is((select count(*)::int from public.activity_cards where owner_id = (select id from ids where k = 'owner')), 0, 'blocked user sees nothing');
select is((select count(*)::int from public.activity_laps where activity_id in (select id from ids)), 0, 'blocked user sees no laps');
select is_empty(
  $$select 1 from public.profile_cards where id = (select id from ids where k = 'owner')$$,
  'blocked user cannot see the blocker''s profile');

-- anonymous
select tests.authenticate_anon();
select results_eq($$select name from public.activity_cards where owner_id = (select id from ids where k = 'owner') order by name$$,
  array['A everyone'], 'anonymous sees only everyone');

-- visibility change takes effect immediately
select tests.as_backend();
update public.activities set visibility = 'only_me'
 where id = (select id from ids where k = 'a_everyone');
select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select count(*)::int from public.activity_cards where owner_id = (select id from ids where k = 'owner')), 0,
  'downgrading to only_me hides the activity from strangers at once');

-- soft-deleted activities disappear for everyone, owner included
select tests.as_backend();
update public.activities set deleted_at = now()
 where id = (select id from ids where k = 'a_followers');
select tests.authenticate_as((select id from ids where k = 'follower'));
select is((select count(*)::int from public.activity_cards where owner_id = (select id from ids where k = 'owner')), 0, 'soft-deleted activity is gone for followers');
select tests.authenticate_as((select id from ids where k = 'owner'));
select is((select count(*)::int from public.activity_cards where owner_id = (select id from ids where k = 'owner')), 2, 'soft-deleted activity is gone from owner cards');

-- a privacy downgrade queued a reprocess job for the worker
select tests.as_backend();
select ok(exists (select 1 from tests.job_payloads('privacy_reprocess') p
                   where p ->> 'activity_id' = (select id::text from ids where k = 'a_everyone')
                     and (p ->> 'visibility_downgraded')::boolean),
  'visibility downgrade enqueues a privacy_reprocess job');

-- the owner -> blocked follow edge was removed by the block
select is((select count(*)::int from public.follows
            where follower_id = (select id from ids where k = 'blocked')), 0,
  'block removed the existing follow');

select * from finish();
rollback;
