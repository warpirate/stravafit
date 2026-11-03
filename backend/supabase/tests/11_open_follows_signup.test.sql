-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Open follows by default, private-profile requests, sign-up metadata edge
-- cases (taken handle, malformed birthday, minors) and handle availability.
begin;
select plan(16);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('open',  tests.create_user('of_open')),
  ('fan',   tests.create_user('of_fan')),
  ('teen',  tests.create_user('of_teen', (current_date - interval '15 years')::date, 'US'));

select is((select profile_visibility from public.profiles where id = (select id from ids where k = 'open')),
  'everyone'::public.profile_visibility, 'an adult''s new profile is open');
select is((select profile_visibility from public.profiles where id = (select id from ids where k = 'teen')),
  'followers'::public.profile_visibility, 'a minor''s new profile starts private');
select is((select default_visibility from public.profile_settings where user_id = (select id from ids where k = 'open')),
  'followers'::public.activity_visibility, 'activities still default to followers');

-- A taken handle (any case) no longer fails the sign-up; it gets a generated one.
insert into auth.users (id, email, raw_user_meta_data)
values ('00000000-0000-4000-8000-0000000000a1', 'of_dupe@test.invalid',
        '{"handle": "OF_OPEN", "first_name": "Dupe", "birthday": "1990-01-01"}');
select is((select handle::text from public.profiles where id = '00000000-0000-4000-8000-0000000000a1'),
  'user_000000000000', 'a taken handle falls back to a generated one');

-- A malformed birthday is ignored instead of failing the sign-up.
insert into auth.users (id, email, raw_user_meta_data)
values ('00000000-0000-4000-8000-0000000000a2', 'of_bad@test.invalid',
        '{"handle": "of_badbirthday", "birthday": "1990-02-31"}');
select is((select birthday from public.profile_settings where user_id = '00000000-0000-4000-8000-0000000000a2'),
  null::date, 'a malformed birthday is stored as missing');

select tests.authenticate_anon();
select ok(not public.is_handle_available('Of_Open'), 'taken handles are unavailable, case-insensitively');
select ok(public.is_handle_available('of_brand_new'), 'free handles are available');
select ok(not public.is_handle_available('no spaces!'), 'malformed handles are unavailable');

-- Following an open profile is accepted at once and notifies with `follow`.
select tests.authenticate_as((select id from ids where k = 'fan'));
select is(public.follow_user((select id from ids where k = 'open')), 'accepted'::public.follow_status,
  'following an open profile is accepted at once');

select tests.authenticate_as((select id from ids where k = 'open'));
select is((select kind from public.notifications), 'follow'::public.notification_kind,
  'the followed user is notified of a new follower');

-- Turning on "private profile" makes new follows requests.
update public.profiles set profile_visibility = 'followers' where id = (select id from ids where k = 'open');
select tests.authenticate_as((select id from ids where k = 'teen'));
select is(public.follow_user((select id from ids where k = 'open')), 'pending'::public.follow_status,
  'following a private profile sends a request');
select is((select viewer_follow_status from public.profile_cards where id = (select id from ids where k = 'open')),
  'pending'::public.follow_status, 'the requester sees the request as pending');

select tests.authenticate_as((select id from ids where k = 'open'));
select is((select count(*)::int from public.notifications where kind = 'follow_request'), 1,
  'the private user got a follow_request');

-- Cancelling a request clears the request notification too.
select tests.authenticate_as((select id from ids where k = 'teen'));
select ok(public.unfollow_user((select id from ids where k = 'open')), 'request cancelled');
select tests.authenticate_as((select id from ids where k = 'open'));
select is((select count(*)::int from public.notifications where kind = 'follow_request'), 0,
  'a cancelled request leaves no stale notification');

-- The earlier open follow survives the switch to private.
select is((select status from public.follows where follower_id = (select id from ids where k = 'fan')),
  'accepted'::public.follow_status, 'existing followers stay followers');

select * from finish();
rollback;
