-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Followers-only profiles: limited card for strangers, follow requests with
-- approve/decline, and request handling when the profile becomes public.
begin;
select plan(23);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('priv',  tests.create_user('pp_private')),
  ('fan',   tests.create_user('pp_fan')),
  ('other', tests.create_user('pp_other')),
  ('third', tests.create_user('pp_third'));

update public.profiles set profile_visibility = 'followers', bio = 'private bio', city = 'Berlin'
 where id = (select id from ids where k = 'priv');
update public.profiles set profile_visibility = 'everyone' where id = (select id from ids where k = 'other');

insert into ids values ('run', tests.create_activity((select id from ids where k = 'priv'), 'followers', 'Private run'));

-- Strangers get a limited card.
select tests.authenticate_as((select id from ids where k = 'fan'));
select results_eq(
  $$select handle::text, city, bio, full_profile, follower_count
      from public.profile_cards where id = (select id from ids where k = 'priv')$$,
  $$values ('pp_private'::text, 'Berlin'::text, null::text, false, null::bigint)$$,
  'stranger sees name and approximate location only');

-- Following a followers-only profile creates a request.
select is(public.follow_user((select id from ids where k = 'priv')), 'pending'::public.follow_status,
  'follow request is pending');
select is(public.follow_user((select id from ids where k = 'priv')), 'pending'::public.follow_status,
  'repeating the request is idempotent');
select is((select viewer_follow_status from public.profile_cards where id = (select id from ids where k = 'priv')),
  'pending'::public.follow_status, 'requester sees the pending state');
select is_empty($$select 1 from public.activity_cards where owner_id = (select id from ids where k = 'priv')$$,
  'pending requester cannot see followers activities');
select throws_ok(
  $$insert into public.follows (follower_id, followee_id, status, accepted_at)
    values ((select id from ids where k = 'fan'), (select id from ids where k = 'priv'), 'accepted', now())$$,
  '42501', null, 'clients cannot forge an accepted follow');

-- Following a public profile is accepted immediately.
select is(public.follow_user((select id from ids where k = 'other')), 'accepted'::public.follow_status,
  'following a public profile is accepted at once');
select throws_ok($$select public.follow_user((select id from ids where k = 'fan'))$$,
  '23514', null, 'nobody can follow themselves');

-- The private user sees and approves the request.
select tests.authenticate_as((select id from ids where k = 'priv'));
select is((select count(*)::int from public.notifications where kind = 'follow_request'), 1,
  'the private user got a follow_request notification');
select is((select count(*)::int from public.follows where status = 'pending'), 1,
  'the private user sees the incoming request');
select ok(public.respond_follow_request((select id from ids where k = 'fan'), true), 'request approved');
select is((select count(*)::int from public.notifications where kind = 'follow_request'), 0,
  'the request notification is cleared once handled');

select tests.authenticate_as((select id from ids where k = 'fan'));
select is((select count(*)::int from public.notifications where kind = 'follow_accepted'), 1,
  'the requester is told the request was accepted');
select isnt_empty($$select 1 from public.activity_cards where owner_id = (select id from ids where k = 'priv')$$,
  'approved follower sees followers activities');
select is((select bio from public.profile_cards where id = (select id from ids where k = 'priv')),
  'private bio', 'approved follower sees the full profile');

select is((select count(*)::int from public.follows
            where followee_id = (select id from ids where k = 'priv') and status = 'accepted'), 1,
  'an approved follower can see the follower list');
select tests.authenticate_as((select id from ids where k = 'third'));
select is((select count(*)::int from public.follows
            where followee_id = (select id from ids where k = 'priv')), 0,
  'strangers cannot list followers of a followers-only profile');

-- Declining a request removes it.
select tests.authenticate_as((select id from ids where k = 'other'));
select is(public.follow_user((select id from ids where k = 'priv')), 'pending'::public.follow_status,
  'second requester is pending');
select tests.authenticate_as((select id from ids where k = 'priv'));
select ok(public.respond_follow_request((select id from ids where k = 'other'), false), 'request declined');
select is((select count(*)::int from public.follows where follower_id = (select id from ids where k = 'other')), 0,
  'declined request is removed');

-- Switching to a public profile accepts waiting requests.
select tests.authenticate_as((select id from ids where k = 'third'));
select is(public.follow_user((select id from ids where k = 'priv')), 'pending'::public.follow_status, 'third requester is pending');
select tests.authenticate_as((select id from ids where k = 'priv'));
update public.profiles set profile_visibility = 'everyone' where id = (select id from ids where k = 'priv');
select is((select status from public.follows where follower_id = (select id from ids where k = 'third')),
  'accepted'::public.follow_status, 'making the profile public accepts pending requests');

-- remove_follower drops an accepted follower.
select ok(public.remove_follower((select id from ids where k = 'fan')), 'follower removed');

select * from finish();
rollback;
