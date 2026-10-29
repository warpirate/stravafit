-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Chronological home feed (ADR-013), mutes, high-fives, comments, inbox.
begin;
select plan(17);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('me',     tests.create_user('fd_me')),
  ('friend', tests.create_user('fd_friend')),
  ('noisy',  tests.create_user('fd_noisy')),
  ('stranger', tests.create_user('fd_stranger'));

insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'me'), id, 'accepted', now() from ids where k in ('friend', 'noisy');

insert into ids values
  ('f1', tests.create_activity((select id from ids where k = 'friend'), 'followers', 'friend old',
     jsonb_build_object('published_at', now() - interval '3 days'))),
  ('f2', tests.create_activity((select id from ids where k = 'friend'), 'everyone', 'friend new',
     jsonb_build_object('published_at', now() - interval '1 hour'))),
  ('f3', tests.create_activity((select id from ids where k = 'friend'), 'everyone', 'friend muted commute',
     jsonb_build_object('published_at', now() - interval '2 hours', 'muted', true))),
  ('f4', tests.create_activity((select id from ids where k = 'friend'), 'only_me', 'friend private',
     jsonb_build_object('published_at', now() - interval '30 minutes'))),
  ('m1', tests.create_activity((select id from ids where k = 'me'), 'only_me', 'my own',
     jsonb_build_object('published_at', now() - interval '2 days'))),
  ('n1', tests.create_activity((select id from ids where k = 'noisy'), 'everyone', 'noisy post',
     jsonb_build_object('published_at', now() - interval '1 day'))),
  ('s1', tests.create_activity((select id from ids where k = 'stranger'), 'everyone', 'stranger post',
     jsonb_build_object('published_at', now())));

select tests.authenticate_as((select id from ids where k = 'me'));
select results_eq($$select name from public.get_home_feed()$$,
  array['friend new', 'noisy post', 'my own', 'friend old'],
  'feed is newest-first, own + followed, excludes muted/private/strangers');

-- Keyset pagination.
select results_eq(
  $$select name from public.get_home_feed(null, null, 2)$$,
  array['friend new', 'noisy post'], 'first page');
select results_eq(
  $$select name from public.get_home_feed(
      (select published_at from public.activity_cards where name = 'noisy post'),
      (select id from public.activity_cards where name = 'noisy post'), 2)$$,
  array['my own', 'friend old'], 'second page continues after the cursor');

-- Muting an account removes it from the feed but not from its profile page.
insert into public.mutes (muter_id, muted_id) select (select id from ids where k = 'me'), id from ids where k = 'noisy';
select results_eq($$select name from public.get_home_feed()$$,
  array['friend new', 'my own', 'friend old'], 'muted account disappears from the feed');
select results_eq(
  $$select name from public.get_profile_activities((select id from ids where k = 'noisy'))$$,
  array['noisy post'], 'muted account is still visible on its profile');
select results_eq(
  $$select name from public.get_profile_activities((select id from ids where k = 'friend'))$$,
  array['friend new', 'friend muted commute', 'friend old'],
  'a muted activity stays on the owner profile');

-- High-fives and comments.
select lives_ok(
  $$insert into public.high_fives (activity_id, user_id)
    values ((select id from ids where k = 'f2'), (select id from ids where k = 'me'))$$,
  'high-five a followed activity');
select throws_ok(
  $$insert into public.high_fives (activity_id, user_id)
    values ((select id from ids where k = 'm1'), (select id from ids where k = 'me'))$$,
  '42501', null, 'no high-fiving your own activity');
select throws_ok(
  $$insert into public.high_fives (activity_id, user_id)
    values ((select id from ids where k = 'f4'), (select id from ids where k = 'me'))$$,
  '42501', null, 'no high-fiving an activity you cannot see');
select lives_ok(
  $$insert into public.comments (activity_id, author_id, body)
    values ((select id from ids where k = 'f2'), (select id from ids where k = 'me'), 'Strong finish!')$$,
  'comment on a followed activity');
select results_eq(
  $$select high_five_count, comment_count, viewer_high_fived from public.activity_cards
     where id = (select id from ids where k = 'f2')$$,
  $$values (1, 1, true)$$, 'counters and viewer flag are maintained');
select throws_ok(
  $$insert into public.comments (activity_id, author_id, body)
    values ((select id from ids where k = 'f2'), (select id from ids where k = 'friend'), 'forged')$$,
  '42501', null, 'cannot comment as someone else');

select tests.authenticate_as((select id from ids where k = 'friend'));
select results_eq(
  $$select kind::text from public.notifications order by kind::text$$,
  array['comment', 'high_five'], 'the owner received both notifications');
select is(public.mark_notifications_read(), 2, 'mark all as read');
select lives_ok(
  $$delete from public.comments where activity_id = (select id from ids where k = 'f2')$$,
  'the activity owner can delete comments on their activity');
select is((select comment_count from public.activity_cards where id = (select id from ids where k = 'f2')),
  0, 'comment counter follows deletes');

select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select count(*)::int from public.notifications), 0, 'inboxes are private');

select * from finish();
rollback;
