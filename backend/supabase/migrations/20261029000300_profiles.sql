-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Identity: profiles (public card), profile_settings (owner-only), age gate,
-- consent records and staff roles. A profile is created by a trigger when
-- Supabase Auth inserts into auth.users.
--
-- Read path: other users never select from these tables directly. They use the
-- `profile_cards` view (20261029001000_read_models.sql), which applies blocks and
-- the followers-only profile rule.

-- ---------------------------------------------------------------------------
-- Generic helpers
-- ---------------------------------------------------------------------------

create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

comment on function private.set_updated_at() is 'BEFORE UPDATE trigger: stamps updated_at.';

-- True when the current statement runs as a backend role (migrations, worker,
-- service_role key) rather than as an end user through PostgREST.
create or replace function private.is_backend_role()
returns boolean
language sql
stable
set search_path = ''
as $$
  select current_user not in ('anon', 'authenticated');
$$;

-- Whole years between a birthday and today (UTC).
create or replace function private.age_years(p_birthday date)
returns integer
language sql
stable
set search_path = ''
as $$
  select extract(year from age((now() at time zone 'utc')::date, p_birthday))::integer;
$$;

-- ---------------------------------------------------------------------------
-- Age gate rules (privacy-safety.md §H): 13 by default, higher where national
-- law sets a higher digital-consent age. Operators can edit this table.
-- ---------------------------------------------------------------------------

create table public.signup_age_rules (
  country  char(2) primary key check (country ~ '^[A-Z]{2}$'),
  min_age  smallint not null check (min_age between 13 and 21),
  note     text
);

comment on table public.signup_age_rules is
  'Minimum sign-up age per ISO country. Countries not listed use 13.';

insert into public.signup_age_rules (country, min_age, note) values
  ('DE', 16, 'GDPR Art. 8 national age'),
  ('NL', 16, 'GDPR Art. 8 national age'),
  ('IE', 16, 'GDPR Art. 8 national age'),
  ('HU', 16, 'GDPR Art. 8 national age'),
  ('LU', 16, 'GDPR Art. 8 national age'),
  ('SK', 16, 'GDPR Art. 8 national age'),
  ('RO', 16, 'GDPR Art. 8 national age'),
  ('HR', 16, 'GDPR Art. 8 national age'),
  ('PL', 16, 'GDPR Art. 8 national age');

alter table public.signup_age_rules enable row level security;
revoke all on table public.signup_age_rules from anon, authenticated;
grant select on table public.signup_age_rules to anon, authenticated;
grant all on table public.signup_age_rules to service_role;
create policy signup_age_rules_read on public.signup_age_rules
  for select to anon, authenticated using (true);

create or replace function private.min_signup_age(p_country char(2))
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select r.min_age from public.signup_age_rules r where r.country = upper(p_country)),
    13);
$$;

-- ---------------------------------------------------------------------------
-- profiles: the public-facing identity card
-- ---------------------------------------------------------------------------

create table public.profiles (
  id                 uuid primary key references auth.users (id) on delete cascade,
  handle             extensions.citext not null unique
                       check (handle ~ '^[A-Za-z0-9_.]{3,30}$'),
  first_name         text not null default '' check (length(first_name) <= 60),
  last_name          text not null default '' check (length(last_name) <= 60),
  avatar_path        text check (length(avatar_path) <= 512),
  bio                text check (length(bio) <= 500),
  city               text check (length(city) <= 80),
  region             text check (length(region) <= 80),
  country            char(2) check (country ~ '^[A-Z]{2}$'),
  profile_visibility public.profile_visibility not null default 'followers',
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.profiles is
  'Public identity card per auth user. Strangers see a limited card when profile_visibility = followers.';
comment on column public.profiles.profile_visibility is
  'followers: non-followers see name, photo and approximate location only, and follows need approval.';

create trigger profiles_updated_at before update on public.profiles
  for each row execute function private.set_updated_at();

-- ---------------------------------------------------------------------------
-- profile_settings: owner-only private settings and defaults
-- ---------------------------------------------------------------------------

create table public.profile_settings (
  user_id                 uuid primary key references public.profiles (id) on delete cascade,
  birthday                date check (birthday > date '1900-01-01'),
  gender                  text check (gender in ('female', 'male', 'nonbinary', 'prefer_not_to_say')),
  units                   public.unit_system not null default 'metric',
  -- Defaults applied to NEW activities only (settings-privacy-units §6.1, §7.2).
  default_visibility      public.activity_visibility not null default 'followers',
  default_hide_start_m    integer not null default 200 check (default_hide_start_m between 0 and 1600),
  default_hide_end_m      integer not null default 200 check (default_hide_end_m between 0 and 1600),
  default_hide_map        boolean not null default false,
  default_hide_heart_rate boolean not null default false,
  default_hide_pace       boolean not null default false,
  default_hide_power      boolean not null default false,
  default_hide_calories   boolean not null default false,
  default_hide_start_time boolean not null default false,
  default_hide_elevation  boolean not null default false,
  -- Social audiences: stricter than the reference app (privacy-safety §A).
  mentions_audience       public.audience not null default 'followers',
  crossings_audience      public.audience not null default 'followers',
  group_activity_audience public.audience not null default 'followers',
  heatmap_opt_in          boolean not null default false,
  health_data_consent     boolean not null default false,
  feed_order              public.feed_order not null default 'chronological',
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now()
);

comment on table public.profile_settings is
  'Owner-only settings. Never readable by other users. Birthday drives the age gate and minor defaults.';
comment on column public.profile_settings.feed_order is
  'ADR-013: chronological by default; personalized is opt-in.';

create trigger profile_settings_updated_at before update on public.profile_settings
  for each row execute function private.set_updated_at();

-- True when the user is under 18 (or has not given a birthday yet, which we
-- treat as a minor so the stricter defaults apply until onboarding finishes).
create or replace function private.is_minor(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select s.birthday is null or private.age_years(s.birthday) < 18
       from public.profile_settings s where s.user_id = p_user),
    true);
$$;

-- Age gate + minor protections. Runs as the invoking role so that end users
-- cannot rewrite a birthday once set; backend roles may correct it.
create or replace function private.profile_settings_guard()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_country char(2);
  v_min     integer;
begin
  if tg_op = 'UPDATE'
     and old.birthday is not null
     and new.birthday is distinct from old.birthday
     and not private.is_backend_role() then
    raise exception 'birthday can only be set once; contact the instance operator to correct it'
      using errcode = 'check_violation';
  end if;

  if new.birthday is not null then
    select p.country into v_country from public.profiles p where p.id = new.user_id;
    v_min := private.min_signup_age(v_country);
    if private.age_years(new.birthday) < v_min then
      raise exception 'age_gate: users must be at least % years old', v_min
        using errcode = 'check_violation';
    end if;
  end if;

  -- Minors (and users who have not given a birthday) keep stricter defaults:
  -- at least 400 m endpoint trimming and no aggregate contribution.
  if new.birthday is null or private.age_years(new.birthday) < 18 then
    new.default_hide_start_m := greatest(new.default_hide_start_m, 400);
    new.default_hide_end_m   := greatest(new.default_hide_end_m, 400);
    new.heatmap_opt_in       := false;
    if new.default_visibility = 'everyone' then
      new.default_visibility := 'followers';
    end if;
    -- Crossings (fly-by style matching) start at "no one" for minors
    -- (privacy-safety §10.1); they may change it later.
    if tg_op = 'INSERT' then
      new.crossings_audience := 'no_one';
    end if;
  end if;

  return new;
end;
$$;

create trigger profile_settings_guard
  before insert or update on public.profile_settings
  for each row execute function private.profile_settings_guard();

-- ---------------------------------------------------------------------------
-- consent_records: append-only audit of consent changes (GDPR Art. 7 / 9)
-- ---------------------------------------------------------------------------

create table public.consent_records (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  kind       text not null check (kind in ('health_data', 'heatmap', 'terms', 'privacy_policy')),
  granted    boolean not null,
  created_at timestamptz not null default now()
);

create index consent_records_user_idx on public.consent_records (user_id, created_at desc);

create or replace function private.record_consent_changes()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' or new.health_data_consent is distinct from old.health_data_consent then
    insert into public.consent_records (user_id, kind, granted)
    values (new.user_id, 'health_data', new.health_data_consent);
  end if;
  if tg_op = 'INSERT' or new.heatmap_opt_in is distinct from old.heatmap_opt_in then
    insert into public.consent_records (user_id, kind, granted)
    values (new.user_id, 'heatmap', new.heatmap_opt_in);
  end if;
  return null;
end;
$$;

create trigger profile_settings_consent_audit
  after insert or update on public.profile_settings
  for each row execute function private.record_consent_changes();

-- ---------------------------------------------------------------------------
-- staff_roles: moderators and admins (moderation-admin-tooling §5.1)
-- ---------------------------------------------------------------------------

create table public.staff_roles (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  role       public.staff_role not null,
  granted_at timestamptz not null default now(),
  primary key (user_id, role)
);

create or replace function private.has_staff_role(p_role public.staff_role)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.staff_roles s
    where s.user_id = auth.uid() and (s.role = p_role or s.role = 'admin'));
$$;

-- ---------------------------------------------------------------------------
-- New auth user -> profile + settings. Raises (and so rejects the sign-up)
-- when the birthday in the sign-up metadata fails the age gate.
-- ---------------------------------------------------------------------------

create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_meta    jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_handle  text  := nullif(v_meta ->> 'handle', '');
  v_country text  := upper(nullif(v_meta ->> 'country', ''));
begin
  if v_handle is null or v_handle !~ '^[A-Za-z0-9_.]{3,30}$' then
    v_handle := 'user_' || substr(replace(new.id::text, '-', ''), 1, 12);
  end if;

  insert into public.profiles (id, handle, first_name, last_name, country)
  values (
    new.id,
    v_handle,
    coalesce(left(v_meta ->> 'first_name', 60), ''),
    coalesce(left(v_meta ->> 'last_name', 60), ''),
    case when v_country ~ '^[A-Z]{2}$' then v_country end);

  insert into public.profile_settings (user_id, birthday)
  values (new.id, nullif(v_meta ->> 'birthday', '')::date);

  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_auth_user();

-- ---------------------------------------------------------------------------
-- RLS and grants. Base tables are owner-only; other users read profile_cards.
-- ---------------------------------------------------------------------------

alter table public.profiles         enable row level security;
alter table public.profile_settings enable row level security;
alter table public.consent_records  enable row level security;
alter table public.staff_roles      enable row level security;

revoke all on table public.profiles, public.profile_settings, public.consent_records,
  public.staff_roles from anon, authenticated;
grant all on table public.profiles, public.profile_settings, public.consent_records,
  public.staff_roles to service_role;

grant select on table public.profiles to authenticated;
grant update (handle, first_name, last_name, avatar_path, bio, city, region, country,
  profile_visibility) on table public.profiles to authenticated;

create policy profiles_select_self on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_self on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

grant select on table public.profile_settings to authenticated;
grant update (birthday, gender, units, default_visibility, default_hide_start_m,
  default_hide_end_m, default_hide_map, default_hide_heart_rate, default_hide_pace,
  default_hide_power, default_hide_calories, default_hide_start_time,
  default_hide_elevation, mentions_audience, crossings_audience,
  group_activity_audience, heatmap_opt_in, health_data_consent, feed_order)
  on table public.profile_settings to authenticated;

create policy profile_settings_select_self on public.profile_settings
  for select to authenticated using (user_id = (select auth.uid()));
create policy profile_settings_update_self on public.profile_settings
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

grant select on table public.consent_records to authenticated;
create policy consent_records_select_self on public.consent_records
  for select to authenticated using (user_id = (select auth.uid()));

grant select on table public.staff_roles to authenticated;
create policy staff_roles_select_self on public.staff_roles
  for select to authenticated using (user_id = (select auth.uid()));
