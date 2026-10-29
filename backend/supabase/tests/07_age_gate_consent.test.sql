-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Age gate, minor defaults, birthday immutability and health-data consent.
begin;
select plan(13);

select throws_ok(
  $$select tests.create_user('ag_child', (current_date - interval '12 years')::date, 'US')$$,
  '23514', null, 'sign-up under 13 is rejected');
select throws_ok(
  $$select tests.create_user('ag_teen_de', (current_date - interval '15 years')::date, 'DE')$$,
  '23514', null, 'sign-up under 16 is rejected where national law says 16');

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;
insert into ids values
  ('teen',  tests.create_user('ag_teen_us', (current_date - interval '15 years')::date, 'US')),
  ('adult', tests.create_user('ag_adult', date '1980-02-02', 'US', false)),
  ('later', tests.create_user('ag_later', null, 'US'));

select results_eq(
  $$select default_hide_start_m, default_hide_end_m, default_visibility, heatmap_opt_in
      from public.profile_settings where user_id = (select id from ids where k = 'teen')$$,
  $$values (400, 400, 'followers'::public.activity_visibility, false)$$,
  'a 15-year-old gets minor defaults');

select tests.authenticate_as((select id from ids where k = 'teen'));
update public.profile_settings set default_hide_start_m = 0, heatmap_opt_in = true,
  default_visibility = 'everyone'
 where user_id = (select id from ids where k = 'teen');
select results_eq(
  $$select default_hide_start_m, heatmap_opt_in, default_visibility from public.profile_settings$$,
  $$values (400, false, 'followers'::public.activity_visibility)$$,
  'minors cannot weaken their privacy defaults');
select throws_ok(
  $$update public.profile_settings set birthday = date '1990-01-01'$$,
  '23514', null, 'users cannot rewrite a birthday once set');

insert into public.activities (owner_id, client_uuid, name, sport_type, start_time, elapsed_s,
  moving_s, hide_start_m, hide_end_m, route_simplified)
values ((select id from ids where k = 'teen'), gen_random_uuid(), 'Teen run', 'Run', now(), 600, 600,
        0, 0, tests.park_loop());
select results_eq(
  $$select hide_start_m, hide_end_m, visibility from public.activities$$,
  $$values (400, 400, 'followers'::public.activity_visibility)$$,
  'minor activities keep at least 400 m trimming and default to followers');

-- A user who has not given a birthday yet is treated as a minor until they do.
select tests.authenticate_as((select id from ids where k = 'later'));
select is((select default_hide_start_m from public.profile_settings), 400,
  'no birthday yet: minor defaults apply');
select throws_ok(
  $$update public.profile_settings set birthday = (current_date - interval '10 years')::date$$,
  '23514', null, 'setting an under-age birthday later is rejected');
select lives_ok(
  $$update public.profile_settings set birthday = date '1995-05-05', default_hide_start_m = 200$$,
  'an adult birthday can be set once');
select is((select default_hide_start_m from public.profile_settings), 200,
  'adults may lower their trimming');

-- Health data: without consent, heart rate is not stored at all.
select tests.authenticate_as((select id from ids where k = 'adult'));
insert into public.activities (owner_id, client_uuid, name, sport_type, start_time, elapsed_s,
  moving_s, avg_hr, max_hr)
values ((select id from ids where k = 'adult'), gen_random_uuid(), 'No consent', 'Run', now(), 60, 60, 150, 170);
select results_eq($$select avg_hr, max_hr from public.activities$$,
  $$values (null::real, null::smallint)$$, 'heart rate is dropped without health-data consent');

update public.profile_settings set health_data_consent = true;
select is((select count(*)::int from public.consent_records where kind = 'health_data' and granted), 1,
  'granting consent is recorded');
select is((select count(*)::int from public.consent_records), 3,
  'initial consent states plus the change are all recorded');

select * from finish();
rollback;
