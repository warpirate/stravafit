-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Storage object policies and user reports.
begin;
select plan(14);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('owner',    tests.create_user('st_owner')),
  ('follower', tests.create_user('st_follower')),
  ('stranger', tests.create_user('st_stranger')),
  ('mod',      tests.create_user('st_mod'));

update public.profiles set profile_visibility = 'followers' where id = (select id from ids where k = 'owner');
insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'follower'), (select id from ids where k = 'owner'), 'accepted', now();
insert into public.staff_roles (user_id, role) select id, 'moderator' from ids where k = 'mod';

insert into ids values
  ('pub', tests.create_activity((select id from ids where k = 'owner'), 'everyone', 'Public run')),
  ('fol', tests.create_activity((select id from ids where k = 'owner'), 'followers', 'Followers run'));

-- Objects written by the worker (service role).
insert into storage.objects (bucket_id, name)
select 'streams', o.id::text || '/' || a.id::text || '/' || f
  from ids o, ids a, unnest(array['owner.sfstream', 'public.sfstream']) f
 where o.k = 'owner' and a.k in ('pub', 'fol');
insert into storage.objects (bucket_id, name)
select 'photos', o.id::text || '/' || a.id::text || '/p1.webp'
  from ids o, ids a where o.k = 'owner' and a.k = 'pub';

select tests.authenticate_as((select id from ids where k = 'owner'));
select is((select count(*)::int from storage.objects where bucket_id = 'streams'), 4,
  'owner reads all own stream files');

select tests.authenticate_as((select id from ids where k = 'follower'));
select results_eq(
  $$select storage.filename(name) from storage.objects where bucket_id = 'streams' order by name$$,
  array['public.sfstream', 'public.sfstream'],
  'follower reads only the public stream derivatives');
select is((select count(*)::int from storage.objects where bucket_id = 'photos'), 1,
  'follower sees photos of a followers-only profile');

select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select count(*)::int from storage.objects where bucket_id = 'streams'), 1,
  'stranger reads the public stream of the public activity only');
select is((select count(*)::int from storage.objects where bucket_id = 'photos'), 0,
  'stranger cannot see photos when the profile page is followers-only');
select throws_ok(
  $$insert into storage.objects (bucket_id, name)
    values ('photo-uploads', (select id::text from ids where k = 'stranger') || '/' ||
            (select id::text from ids where k = 'pub') || '/x.jpg')$$,
  '42501', null, 'photos can only be staged on your own activities');
select throws_ok(
  $$insert into storage.objects (bucket_id, name)
    values ('avatars', (select id::text from ids where k = 'owner') || '/a.png')$$,
  '42501', null, 'avatars can only be written in your own folder');
select lives_ok(
  $$insert into storage.objects (bucket_id, name)
    values ('avatars', (select id::text from ids where k = 'stranger') || '/a.png')$$,
  'own avatar upload works');

select tests.authenticate_anon();
select is((select count(*)::int from storage.objects where bucket_id = 'avatars'), 1,
  'avatars are publicly readable');
select is((select count(*)::int from storage.objects where bucket_id = 'originals'), 0,
  'originals are never public');

-- Reports.
select tests.authenticate_as((select id from ids where k = 'stranger'));
select lives_ok(
  $$insert into public.reports (reporter_id, target_type, target_id, reason)
    values ((select id from ids where k = 'stranger'), 'activity', (select id from ids where k = 'pub'), 'spam')$$,
  'a visible activity can be reported');
select throws_ok(
  $$insert into public.reports (reporter_id, target_type, target_id, reason)
    values ((select id from ids where k = 'stranger'), 'activity', (select id from ids where k = 'fol'), 'spam')$$,
  'P0002', null, 'an invisible activity cannot be reported (no existence leak)');

select tests.authenticate_as((select id from ids where k = 'mod'));
select is((select target_owner_id from public.reports), (select id from ids where k = 'owner'),
  'moderators see the report with its target owner');
update public.reports set status = 'dismissed', resolution_note = 'not spam';
select isnt((select resolved_at from public.reports), null, 'resolving stamps resolved_at');

select * from finish();
rollback;
