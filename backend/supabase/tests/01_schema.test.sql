-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Schema shape, RLS coverage and privilege boundaries.
begin;
select plan(31);

select has_table('public', t, 'table public.' || t || ' exists')
  from unnest(array['profiles', 'profile_settings', 'consent_records', 'staff_roles',
    'signup_age_rules', 'follows', 'blocks', 'mutes', 'privacy_zones', 'gear', 'activities',
    'laps', 'splits', 'activity_streams', 'activity_private_notes', 'uploads', 'high_fives',
    'comments', 'notifications', 'reports']) as t;

select is(
  (select count(*)::integer
     from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname in ('public', 'private') and c.relkind = 'r'
      and not c.relrowsecurity and c.relname <> 'spatial_ref_sys'),
  0, 'every table in public and private has row level security enabled');

select has_view('public', 'activity_cards', 'activity_cards read model exists');
select has_view('public', 'profile_cards', 'profile_cards read model exists');

select ok(not has_table_privilege('anon', 'public.activities', 'SELECT'),
  'anon cannot read the activities base table');
select ok(not has_column_privilege('authenticated', 'public.activities', 'route_public', 'UPDATE'),
  'clients cannot write the privacy-trimmed route');
select ok(not has_column_privilege('authenticated', 'public.activities', 'high_five_count', 'UPDATE'),
  'clients cannot write denormalised counters');
select ok(not has_table_privilege('authenticated', 'public.follows', 'INSERT'),
  'follows can only be written through RPCs');
select ok(not has_function_privilege('authenticated', 'private.job_claim(text, integer, integer)', 'EXECUTE'),
  'end users cannot claim worker jobs');
select ok(has_function_privilege('service_role', 'private.job_claim(text, integer, integer)', 'EXECUTE'),
  'the worker role can claim jobs');
select ok(not has_function_privilege('anon', 'public.get_home_feed(timestamptz, uuid, integer)', 'EXECUTE'),
  'anonymous users have no home feed');

select results_eq(
  $$select id, public from storage.buckets
     where id in ('originals', 'streams', 'photo-uploads', 'photos', 'avatars', 'exports')
     order by id collate "C"$$,
  $$values ('avatars', true), ('exports', false), ('originals', false),
           ('photo-uploads', false), ('photos', false), ('streams', false)$$,
  'storage buckets exist and only avatars are public');

select * from finish();
rollback;
