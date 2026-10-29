-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Enumerated types. Values are append-only: add new ones with
-- `alter type ... add value` in a later migration, never rename or drop.

-- Sport types (blueprint data model §5.1) plus a forward-compatible `Other`.
create type public.sport_type as enum (
  'Run', 'TrailRun', 'VirtualRun', 'Walk', 'Hike', 'Snowshoe',
  'Ride', 'MountainBikeRide', 'GravelRide', 'EBikeRide', 'EMountainBikeRide',
  'VirtualRide', 'Velomobile', 'Handcycle',
  'Swim', 'Rowing', 'VirtualRow', 'Canoeing', 'Kayaking', 'StandUpPaddling',
  'Surfing', 'Kitesurf', 'Windsurf', 'Sail',
  'AlpineSki', 'BackcountrySki', 'NordicSki', 'Snowboard', 'IceSkate',
  'InlineSkate', 'RollerSki', 'Skateboard', 'Wheelchair',
  'Workout', 'WeightTraining', 'Crossfit', 'HighIntensityIntervalTraining',
  'Elliptical', 'StairStepper', 'Yoga', 'Pilates', 'PhysicalTherapy', 'Dance',
  'Badminton', 'Pickleball', 'Padel', 'Racquetball', 'Squash', 'TableTennis',
  'Tennis', 'Basketball', 'Cricket', 'Soccer', 'Volleyball', 'Golf', 'RockClimbing',
  'Other'
);

-- Who may see an activity.
create type public.activity_visibility as enum ('everyone', 'followers', 'only_me');

-- Who may see the full profile page. `followers` also turns follows into requests.
create type public.profile_visibility as enum ('everyone', 'followers');

-- Audience pickers for social features (mentions, crossings, group activities).
create type public.audience as enum ('everyone', 'followers', 'no_one');

create type public.unit_system as enum ('metric', 'imperial');

create type public.feed_order as enum ('chronological', 'personalized');

create type public.workout_kind as enum ('default', 'race', 'long', 'workout', 'commute');

create type public.gear_kind as enum ('bike', 'shoe', 'other');

create type public.follow_status as enum ('pending', 'accepted');

-- File formats accepted for upload and produced as stream artifacts.
create type public.file_format as enum ('fit', 'gpx', 'tcx', 'json', 'sfstream');

-- Upload lifecycle, see uploads table comment for the transition graph.
create type public.upload_status as enum (
  'awaiting_file', 'queued', 'processing', 'ready', 'error', 'duplicate'
);

create type public.lap_trigger as enum ('manual', 'distance', 'time', 'position', 'session_end');

create type public.split_unit as enum ('km', 'mi');

create type public.notification_kind as enum (
  'high_five', 'comment', 'follow', 'follow_request', 'follow_accepted',
  'upload_ready', 'upload_failed'
);

-- Moderation (docs/99-gaps/moderation-admin-tooling.md §4.1, v0 subset).
create type public.report_target as enum ('activity', 'comment', 'profile', 'photo');

create type public.report_reason as enum (
  'cheating_vehicle', 'cheating_ebike', 'wrong_sport', 'gps_error',
  'spam', 'scam', 'harassment', 'hate', 'sexual_content', 'csam',
  'violence_self_harm', 'impersonation', 'privacy_doxxing',
  'ip_infringement', 'illegal_content', 'other'
);

create type public.report_status as enum ('open', 'in_review', 'actioned', 'dismissed');

create type public.staff_role as enum ('moderator', 'admin');
