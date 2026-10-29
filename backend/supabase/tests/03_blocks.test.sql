-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Blocks are silent and symmetric: neither side can see or reach the other.
begin;
select plan(14);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('alice', tests.create_user('bl_alice')),
  ('bob',   tests.create_user('bl_bob')),
  ('carol', tests.create_user('bl_carol'));

update public.profiles set profile_visibility = 'everyone'
 where id in (select id from ids);

insert into ids values
  ('alice_run', tests.create_activity((select id from ids where k = 'alice'), 'everyone', 'Alice run')),
  ('carol_run', tests.create_activity((select id from ids where k = 'carol'), 'everyone', 'Carol run'));

-- Bob follows Alice and comments on Carol's public run before the block.
insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'bob'), (select id from ids where k = 'alice'), 'accepted', now();
insert into public.comments (activity_id, author_id, body)
select (select id from ids where k = 'carol_run'), (select id from ids where k = 'bob'), 'Bob was here';
insert into public.notifications (recipient_id, kind, actor_id)
select (select id from ids where k = 'alice'), 'follow', (select id from ids where k = 'bob');

-- Alice blocks Bob through the API.
select tests.authenticate_as((select id from ids where k = 'alice'));
select lives_ok(
  $$insert into public.blocks (blocker_id, blocked_id)
    values ((select id from ids where k = 'alice'), (select id from ids where k = 'bob'))$$,
  'a user can block another user');
select is((select count(*)::int from public.blocks), 1, 'the blocker sees their block');
select is((select count(*)::int from public.notifications
            where actor_id = (select id from ids where k = 'bob')), 0,
  'the block clears inbox rows from the blocked user');
select isnt_empty(
  $$select 1 from public.profile_cards where id = (select id from ids where k = 'bob')$$,
  'the blocker still sees a minimal card of the person they blocked (to unblock)');

select tests.authenticate_as((select id from ids where k = 'bob'));
select is((select count(*)::int from public.blocks), 0, 'the blocked user cannot see the block');
select is((select count(*)::int from public.follows
            where follower_id = (select id from ids where k = 'bob')
              and followee_id = (select id from ids where k = 'alice')), 0,
  'the block removed Bob''s follow');
select is_empty(
  $$select 1 from public.activity_cards where owner_id = (select id from ids where k = 'alice')$$,
  'Bob cannot see Alice''s public activity');
select is_empty(
  $$select 1 from public.profile_cards where id = (select id from ids where k = 'alice')$$,
  'Bob cannot find Alice''s profile');
select throws_ok(
  $$select public.follow_user((select id from ids where k = 'alice'))$$,
  'P0002', 'profile not found',
  'Bob cannot follow Alice again (and the error does not reveal the block)');
select throws_ok(
  $$insert into public.high_fives (activity_id, user_id)
    values ((select id from ids where k = 'alice_run'), (select id from ids where k = 'bob'))$$,
  '42501', null, 'Bob cannot high-five Alice''s activity');
select throws_ok(
  $$insert into public.comments (activity_id, author_id, body)
    values ((select id from ids where k = 'alice_run'), (select id from ids where k = 'bob'), 'hi')$$,
  '42501', null, 'Bob cannot comment on Alice''s activity');

-- Comments by a blocked user vanish for the blocker on third-party activities.
select tests.authenticate_as((select id from ids where k = 'alice'));
select is((select count(*)::int from public.comments
            where activity_id = (select id from ids where k = 'carol_run')), 0,
  'Alice does not see Bob''s comment on Carol''s activity');
select tests.authenticate_as((select id from ids where k = 'carol'));
select is((select count(*)::int from public.comments
            where activity_id = (select id from ids where k = 'carol_run')), 1,
  'Carol (not involved in the block) still sees Bob''s comment');

-- Unblocking restores visibility.
select tests.authenticate_as((select id from ids where k = 'alice'));
delete from public.blocks where blocked_id = (select id from ids where k = 'bob');
select tests.authenticate_as((select id from ids where k = 'bob'));
select isnt_empty(
  $$select 1 from public.activity_cards where owner_id = (select id from ids where k = 'alice')$$,
  'after unblocking, Bob sees Alice''s public activity again');

select * from finish();
rollback;
