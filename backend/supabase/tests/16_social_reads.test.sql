-- SPDX-License-Identifier: AGPL-3.0-or-later
-- People search, follower/following lists and counts, follow requests and
-- the notifications inbox with mark-read.
begin;
select plan(14);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('me',      tests.create_user('sr_maria')),
  ('runner',  tests.create_user('sr_runner_jo')),
  ('priv',    tests.create_user('sr_private_pat')),
  ('blocker', tests.create_user('sr_blocker'));

update public.profiles set first_name = 'Joanna', last_name = 'Runwell' where id = (select id from ids where k = 'runner');
update public.profiles set profile_visibility = 'followers' where id = (select id from ids where k = 'priv');
insert into public.blocks (blocker_id, blocked_id)
select (select id from ids where k = 'blocker'), (select id from ids where k = 'me');
-- The private profile has one accepted follower already.
insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'blocker'), (select id from ids where k = 'priv'), 'accepted', now();

-- Search.
select tests.authenticate_as((select id from ids where k = 'me'));
select results_eq($$select handle::text from public.search_profiles('sr_runner')$$, array['sr_runner_jo'],
  'search finds a handle by prefix');
select results_eq($$select handle::text from public.search_profiles('runwell')$$, array['sr_runner_jo'],
  'search finds a last name');
select is((select count(*)::int from public.search_profiles('sr_blocker')), 0,
  'someone who blocked the searcher is not found');
select is((select count(*)::int from public.search_profiles('s')), 0, 'one character is too short to search');
select is((select count(*)::int from public.search_profiles('sr\_%')), 0, 'LIKE wildcards are matched literally');

-- Following and lists.
select public.follow_user((select id from ids where k = 'runner'));
select public.follow_user((select id from ids where k = 'priv'));
select results_eq($$select (profile).handle::text from public.get_following((select id from ids where k = 'me'))$$,
  array['sr_runner_jo'], 'following lists accepted follows only');
select results_eq($$select following_count from public.profile_cards where id = (select id from ids where k = 'me')$$,
  array[1::bigint], 'the following count matches');

select tests.authenticate_as((select id from ids where k = 'runner'));
select results_eq($$select (profile).handle::text from public.get_followers((select id from ids where k = 'runner'))$$,
  array['sr_maria'], 'followers list');
select results_eq($$select kind::text, actor_handle from public.get_notifications()$$,
  $$values ('follow'::text, 'sr_maria'::text)$$, 'the inbox shows who followed, with their handle');
select is(public.unread_notification_count(), 1, 'one unread notification');
select is(public.mark_notifications_read(), 1, 'mark all read');
select is(public.unread_notification_count(), 0, 'nothing unread afterwards');

-- A private profile's lists stay private; its owner sees the request.
select is((select count(*)::int from public.get_followers((select id from ids where k = 'priv'))), 0,
  'a stranger cannot list a private profile''s followers');
select tests.authenticate_as((select id from ids where k = 'priv'));
select results_eq($$select (profile).handle::text from public.get_follow_requests()$$, array['sr_maria'],
  'the private user sees the pending request');

select * from finish();
rollback;
