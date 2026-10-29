-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Local development seed: three demo athletes in Berlin with a small social
-- graph and a handful of activities. Loaded by `supabase db reset` and by
-- backend/scripts/verify-db.sh. All demo accounts use the password
-- `demo-password-123` (local use only).
--
--   asha  public profile, everyone/followers/only_me activities, a hidden place
--   ben   public profile, follows asha and chen
--   chen  followers-only profile; asha's follow request is still pending

-- ---------------------------------------------------------------------------
-- Users (the auth trigger creates profiles + profile_settings)
-- ---------------------------------------------------------------------------
insert into auth.users (instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, confirmation_token, recovery_token, email_change_token_new,
  email_change, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated',
       u.email, extensions.crypt('demo-password-123', extensions.gen_salt('bf')),
       now(), '', '', '', '',
       '{"provider":"email","providers":["email"]}'::jsonb, u.meta, now(), now()
  from (values
    ('a5a5a5a5-0000-4000-8000-000000000001'::uuid, 'asha@example.test',
     '{"handle":"asha","first_name":"Asha","last_name":"Runner","country":"DE","birthday":"1991-04-12"}'::jsonb),
    ('b0b0b0b0-0000-4000-8000-000000000002'::uuid, 'ben@example.test',
     '{"handle":"ben","first_name":"Ben","last_name":"Rider","country":"DE","birthday":"1985-09-30"}'::jsonb),
    ('c4c4c4c4-0000-4000-8000-000000000003'::uuid, 'chen@example.test',
     '{"handle":"chen","first_name":"Chen","last_name":"Hiker","country":"NL","birthday":"1994-01-05"}'::jsonb)
  ) as u(id, email, meta);

insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at,
  created_at, updated_at)
select u.id::text, u.id, jsonb_build_object('sub', u.id::text, 'email', u.email),
       'email', now(), now(), now()
  from auth.users u
 where u.email like '%@example.test';

update public.profiles set profile_visibility = 'everyone', city = 'Berlin',
  bio = 'Morning runs around the park.'
 where handle = 'asha';
update public.profiles set profile_visibility = 'everyone', city = 'Berlin',
  bio = 'Gravel and coffee.'
 where handle = 'ben';
update public.profiles set profile_visibility = 'followers', city = 'Berlin',
  bio = 'Weekend hikes.'
 where handle = 'chen';

update public.profile_settings set health_data_consent = true, default_hide_start_m = 200
 where user_id in ('a5a5a5a5-0000-4000-8000-000000000001', 'b0b0b0b0-0000-4000-8000-000000000002');

-- ---------------------------------------------------------------------------
-- Social graph
-- ---------------------------------------------------------------------------
insert into public.follows (follower_id, followee_id, status, accepted_at) values
  ('b0b0b0b0-0000-4000-8000-000000000002', 'a5a5a5a5-0000-4000-8000-000000000001', 'accepted', now()),
  ('a5a5a5a5-0000-4000-8000-000000000001', 'b0b0b0b0-0000-4000-8000-000000000002', 'accepted', now()),
  ('b0b0b0b0-0000-4000-8000-000000000002', 'c4c4c4c4-0000-4000-8000-000000000003', 'accepted', now()),
  ('a5a5a5a5-0000-4000-8000-000000000001', 'c4c4c4c4-0000-4000-8000-000000000003', 'pending', null);

insert into public.notifications (recipient_id, kind, actor_id) values
  ('c4c4c4c4-0000-4000-8000-000000000003', 'follow_request', 'a5a5a5a5-0000-4000-8000-000000000001');

-- Asha's hidden place around her front door (start/end of her loop).
insert into public.privacy_zones (id, owner_id, label, center, radius_m) values
  ('d0d0d0d0-0000-4000-8000-000000000001', 'a5a5a5a5-0000-4000-8000-000000000001', 'Home',
   'SRID=4326;POINT(13.3501 52.5145)', 300);

insert into public.gear (id, owner_id, kind, name, brand, default_for) values
  ('e0e0e0e0-0000-4000-8000-000000000001', 'a5a5a5a5-0000-4000-8000-000000000001', 'shoe',
   'Daily trainers', 'Generic', '{Run}'),
  ('e0e0e0e0-0000-4000-8000-000000000002', 'b0b0b0b0-0000-4000-8000-000000000002', 'bike',
   'Gravel bike', 'Generic', '{GravelRide,Ride}');

-- ---------------------------------------------------------------------------
-- Activities
-- ---------------------------------------------------------------------------
insert into public.activities (id, owner_id, client_uuid, name, description, sport_type,
  visibility, gear_id, start_time, tz_name, utc_offset_s, elapsed_s, moving_s, distance_m,
  elev_gain_m, avg_speed_mps, max_speed_mps, avg_hr, max_hr, calories, route_simplified,
  hide_heart_rate, published_at)
values
  ('f0000000-0000-4000-8000-000000000001', 'a5a5a5a5-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000001', 'Park loop', 'Easy loop before work.', 'Run',
   'everyone', 'e0e0e0e0-0000-4000-8000-000000000001',
   '2026-10-26 06:30:00+00', 'Europe/Berlin', 3600, 1980, 1920, 5120,
   18, 2.67, 4.1, 148, 171, 412,
   'SRID=4326;LINESTRING(13.3501 52.5145,13.3560 52.5150,13.3620 52.5155,13.3680 52.5158,13.3740 52.5161,13.3777 52.5163,13.3770 52.5135,13.3700 52.5120,13.3600 52.5118,13.3510 52.5125,13.3501 52.5140)',
   false, '2026-10-26 07:10:00+00'),
  ('f0000000-0000-4000-8000-000000000002', 'a5a5a5a5-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000002', 'Recovery jog', null, 'Run',
   'followers', 'e0e0e0e0-0000-4000-8000-000000000001',
   '2026-10-27 17:45:00+00', 'Europe/Berlin', 3600, 1500, 1460, 3400,
   6, 2.33, 3.2, 131, 150, 260,
   'SRID=4326;LINESTRING(13.3501 52.5145,13.3560 52.5150,13.3620 52.5155,13.3680 52.5158,13.3620 52.5135,13.3540 52.5130,13.3501 52.5140)',
   true, '2026-10-27 18:20:00+00'),
  ('f0000000-0000-4000-8000-000000000003', 'a5a5a5a5-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000003', 'Commute', null, 'Walk',
   'only_me', null,
   '2026-10-28 07:50:00+00', 'Europe/Berlin', 3600, 1260, 1200, 1650,
   2, 1.38, 1.9, null, null, 90,
   'SRID=4326;LINESTRING(13.3501 52.5145,13.3580 52.5160,13.3650 52.5170)',
   false, '2026-10-28 08:15:00+00'),
  ('f0000000-0000-4000-8000-000000000004', 'b0b0b0b0-0000-4000-8000-000000000002',
   '20000000-0000-4000-8000-000000000001', 'Forest gravel', 'Dusty but fast.', 'GravelRide',
   'everyone', 'e0e0e0e0-0000-4000-8000-000000000002',
   '2026-10-25 09:00:00+00', 'Europe/Berlin', 3600, 7400, 6900, 48200,
   310, 6.99, 14.2, 139, 176, 1650,
   'SRID=4326;LINESTRING(13.2600 52.4800,13.2400 52.4700,13.2200 52.4650,13.2100 52.4800,13.2300 52.4950,13.2600 52.4900)',
   false, '2026-10-25 11:20:00+00'),
  ('f0000000-0000-4000-8000-000000000005', 'c4c4c4c4-0000-4000-8000-000000000003',
   '30000000-0000-4000-8000-000000000001', 'Lakeside hike', null, 'Hike',
   'followers', null,
   '2026-10-24 10:00:00+00', 'Europe/Berlin', 3600, 10800, 9600, 14100,
   120, 1.47, 2.1, null, null, 780,
   'SRID=4326;LINESTRING(13.1800 52.4300,13.1700 52.4200,13.1600 52.4250,13.1650 52.4350)',
   false, '2026-10-24 14:00:00+00');

insert into public.laps (activity_id, lap_index, trigger, start_offset_s, elapsed_s, moving_s,
  distance_m, elev_gain_m, avg_speed_mps, max_speed_mps, avg_hr, max_hr) values
  ('f0000000-0000-4000-8000-000000000001', 0, 'distance', 0, 1010, 980, 2560, 9, 2.61, 3.9, 144, 160),
  ('f0000000-0000-4000-8000-000000000001', 1, 'distance', 1010, 970, 940, 2560, 9, 2.72, 4.1, 152, 171);

insert into public.splits (activity_id, unit, split_index, distance_m, elapsed_s, moving_s,
  elev_diff_m, avg_speed_mps, avg_hr) values
  ('f0000000-0000-4000-8000-000000000001', 'km', 0, 1000, 395, 384, 2, 2.60, 141),
  ('f0000000-0000-4000-8000-000000000001', 'km', 1, 1000, 388, 377, 4, 2.65, 146),
  ('f0000000-0000-4000-8000-000000000001', 'km', 2, 1000, 384, 373, 3, 2.68, 149),
  ('f0000000-0000-4000-8000-000000000001', 'km', 3, 1000, 381, 371, 5, 2.70, 152),
  ('f0000000-0000-4000-8000-000000000001', 'km', 4, 1000, 378, 368, 4, 2.72, 155);

insert into public.activity_private_notes (activity_id, body) values
  ('f0000000-0000-4000-8000-000000000001', 'Left knee felt tight after 4 km.');

insert into public.high_fives (activity_id, user_id) values
  ('f0000000-0000-4000-8000-000000000001', 'b0b0b0b0-0000-4000-8000-000000000002'),
  ('f0000000-0000-4000-8000-000000000004', 'a5a5a5a5-0000-4000-8000-000000000001');

insert into public.comments (activity_id, author_id, body) values
  ('f0000000-0000-4000-8000-000000000001', 'b0b0b0b0-0000-4000-8000-000000000002', 'Nice pace!'),
  ('f0000000-0000-4000-8000-000000000004', 'a5a5a5a5-0000-4000-8000-000000000001', 'Those climbs look brutal.');
