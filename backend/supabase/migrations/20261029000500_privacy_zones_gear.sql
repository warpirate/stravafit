-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Privacy zones (hidden places) and gear.

-- A hidden place hides activity starts/ends that fall within `radius_m` of it
-- (settings-privacy-units §7.3). Only the centre point is stored, never the
-- street address the user typed; the label is free text chosen by the owner.
create table public.privacy_zones (
  id         uuid primary key default gen_random_uuid(),
  owner_id   uuid not null references public.profiles (id) on delete cascade,
  label      text check (length(label) <= 60),
  center     extensions.geography(Point, 4326) not null,
  radius_m   integer not null default 1600 check (radius_m between 200 and 1600),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.privacy_zones is
  'Owner-only hidden places. Applied retroactively to activities that were not edited individually.';

create index privacy_zones_owner_idx on public.privacy_zones (owner_id);
create index privacy_zones_center_gix on public.privacy_zones using gist (center);

create trigger privacy_zones_updated_at before update on public.privacy_zones
  for each row execute function private.set_updated_at();

create table public.gear (
  id                 uuid primary key default gen_random_uuid(),
  owner_id           uuid not null references public.profiles (id) on delete cascade,
  kind               public.gear_kind not null,
  name               text not null check (length(name) between 1 and 80),
  brand              text check (length(brand) <= 80),
  model              text check (length(model) <= 80),
  default_for        public.sport_type[] not null default '{}',
  initial_distance_m double precision not null default 0 check (initial_distance_m >= 0),
  distance_m         double precision not null default 0 check (distance_m >= 0),
  reminder_at_m      double precision check (reminder_at_m > 0),
  retired            boolean not null default false,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on column public.gear.distance_m is
  'Accumulated distance, maintained by the worker post-process job (not by clients).';

create index gear_owner_idx on public.gear (owner_id) where not retired;

create trigger gear_updated_at before update on public.gear
  for each row execute function private.set_updated_at();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.privacy_zones enable row level security;
alter table public.gear          enable row level security;

revoke all on table public.privacy_zones, public.gear from anon, authenticated;
grant all on table public.privacy_zones, public.gear to service_role;

grant select, delete on table public.privacy_zones to authenticated;
grant insert (owner_id, label, center, radius_m) on table public.privacy_zones to authenticated;
grant update (label, center, radius_m) on table public.privacy_zones to authenticated;

create policy privacy_zones_owner_all on public.privacy_zones
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

-- Gear: owner manages it; accepted followers may read it. Strangers never see
-- gear, even on activities visible to everyone (settings-privacy-units §6.2).
grant select, delete on table public.gear to authenticated;
grant insert (owner_id, kind, name, brand, model, default_for, initial_distance_m,
  reminder_at_m, retired) on table public.gear to authenticated;
grant update (kind, name, brand, model, default_for, initial_distance_m, reminder_at_m,
  retired) on table public.gear to authenticated;

create policy gear_owner_all on public.gear
  for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

create policy gear_select_followers on public.gear
  for select to authenticated
  using (private.viewer_relation(owner_id) = 'follower');
