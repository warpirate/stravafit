-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Group weekly leaderboard and challenges respect each viewer's activity
-- visibility (everyone / followers / only_me), hidden elevation, blocks, the
-- group sport and the week bounds; members can leave.
begin;
select plan(16);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select, insert on ids to anon, authenticated;

insert into ids values
  ('a', tests.create_user('gl_alice')),
  ('b', tests.create_user('gl_bob')),
  ('c', tests.create_user('gl_carol')),
  ('d', tests.create_user('gl_dave'));

-- Bob follows Alice; Carol does not. Bob has blocked Dave.
insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'b'), (select id from ids where k = 'a'), 'accepted', now();
insert into public.blocks (blocker_id, blocked_id)
select (select id from ids where k = 'b'), (select id from ids where k = 'd');

create temp table tok (t text) on commit drop;
grant select, insert on tok to authenticated;

select tests.authenticate_as((select id from ids where k = 'a'));
insert into ids select 'g', (public.create_group('Track Club', 'Run', null, 'UTC')).id;
insert into tok select invite_token from public.groups;
select tests.authenticate_as((select id from ids where k = 'b'));
select public.join_group((select t from tok));
select tests.authenticate_as((select id from ids where k = 'c'));
select public.join_group((select t from tok));
select tests.authenticate_as((select id from ids where k = 'd'));
select public.join_group((select t from tok));

-- Alice's week (Monday 01:00 UTC of the current ISO week).
select tests.as_backend();
create temp table wk (s timestamptz) on commit drop;
grant select on wk to anon, authenticated;
insert into wk select (date_trunc('week', now() at time zone 'UTC') + interval '1 hour') at time zone 'UTC';

select tests.create_activity((select id from ids where k = 'a'), 'everyone', 'Public 5k',
  jsonb_build_object('start_time', (select s from wk), 'distance_m', 5000, 'elev_gain_m', 50));
select tests.create_activity((select id from ids where k = 'a'), 'followers', 'Followers 3k',
  jsonb_build_object('start_time', (select s from wk), 'distance_m', 3000, 'elev_gain_m', 30,
                     'hide_elevation', true));
select tests.create_activity((select id from ids where k = 'a'), 'only_me', 'Private 1k',
  jsonb_build_object('start_time', (select s from wk), 'distance_m', 1000, 'elev_gain_m', 10));
select tests.create_activity((select id from ids where k = 'a'), 'everyone', 'Last week 7k',
  jsonb_build_object('start_time', (select s from wk) - interval '8 days', 'distance_m', 7000));
select tests.create_activity((select id from ids where k = 'a'), 'everyone', 'A ride',
  jsonb_build_object('start_time', (select s from wk), 'distance_m', 20000, 'sport_type', 'Ride'));

-- What each member sees of Alice.
select tests.authenticate_as((select id from ids where k = 'a'));
select results_eq(
  $$select distance_m, elev_gain_m, activity_count from public.get_group_leaderboard((select id from ids where k = 'g'))
     where user_id = (select id from ids where k = 'a')$$,
  $$values (9000::double precision, 90::double precision, 3::bigint)$$,
  'Alice sees all her own runs of this week');

select tests.authenticate_as((select id from ids where k = 'b'));
select results_eq(
  $$select distance_m, elev_gain_m, rank_distance from public.get_group_leaderboard((select id from ids where k = 'g'))
     where user_id = (select id from ids where k = 'a')$$,
  $$values (8000::double precision, 50::double precision, 1::bigint)$$,
  'a follower sees everyone + followers runs, without hidden elevation');
select is((select count(*)::int from public.get_group_leaderboard((select id from ids where k = 'g'))), 3,
  'a member blocked by the viewer is left out of the leaderboard');

select tests.authenticate_as((select id from ids where k = 'c'));
select results_eq(
  $$select distance_m, activity_count from public.get_group_leaderboard((select id from ids where k = 'g'))
     where user_id = (select id from ids where k = 'a')$$,
  $$values (5000::double precision, 1::bigint)$$,
  'a non-follower sees only the public run (only_me and followers runs do not leak)');
select is((select count(*)::int from public.get_group_leaderboard((select id from ids where k = 'g'))), 4,
  'every member is listed, with zero totals when nothing counts');
select is((select distinct week_start from public.get_group_leaderboard((select id from ids where k = 'g'))),
  (date_trunc('week', now() at time zone 'UTC'))::date, 'the week starts on Monday in the group timezone');
select is((select distance_m from public.get_group_leaderboard((select id from ids where k = 'g'),
             ((select s from wk) - interval '8 days')::date)
            where user_id = (select id from ids where k = 'a')), 7000::double precision,
  'a past week can be asked for');

select tests.authenticate_as((select id from ids where k = 'd'));
select throws_ok($$select * from public.get_group_leaderboard(gen_random_uuid())$$,
  'P0002', null, 'non-members get no leaderboard');

-- Challenges: owners/admins create them; progress follows the same rule.
select tests.authenticate_as((select id from ids where k = 'c'));
select throws_ok(format($$insert into public.group_challenges (group_id, title, metric, target, starts_on, ends_on)
                          values (%L, 'Mine', 'distance', 1000, current_date, current_date)$$,
                        (select id from ids where k = 'g')),
  '42501', null, 'plain members cannot create challenges');

select tests.authenticate_as((select id from ids where k = 'a'));
insert into public.group_challenges (group_id, title, metric, target, starts_on, ends_on)
select (select id from ids where k = 'g'), '10 km this week', 'distance', 10000,
       (select s from wk)::date, (select s from wk)::date + 6;
select results_eq($$select progress, completed, status from public.get_group_challenges((select id from ids where k = 'g'))$$,
  $$values (9000::double precision, false, 'active'::text)$$, 'the owner sees full progress');

select tests.authenticate_as((select id from ids where k = 'b'));
select is((select progress from public.get_group_challenges((select id from ids where k = 'g'))), 8000::double precision,
  'a follower''s progress counts followers runs');

select tests.authenticate_as((select id from ids where k = 'c'));
select is((select progress from public.get_group_challenges((select id from ids where k = 'g'))), 5000::double precision,
  'a non-follower''s progress counts public runs only');
select results_eq(
  $$select value, share, rank from public.get_challenge_progress((select id from public.group_challenges))
     where user_id = (select id from ids where k = 'a')$$,
  $$values (5000::double precision, 0.5::double precision, 1::bigint)$$,
  'per-member progress shows each member''s share of the target');

-- Leaving: a member deletes their own membership row.
delete from public.group_members where user_id = (select id from ids where k = 'c');
select is((select count(*)::int from public.groups), 0, 'after leaving, the group is no longer visible');
select tests.authenticate_as((select id from ids where k = 'a'));
select is((select member_count from public.groups), 3, 'member count follows leaves');
select is((select count(*)::int from public.group_members where user_id = (select id from ids where k = 'c')), 0,
  'the member is gone');

select * from finish();
rollback;
