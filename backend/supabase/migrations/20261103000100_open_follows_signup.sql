-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Product decision (2026-11-03): follows are open by default.
--
--   * New profiles are 'everyone', so following someone is accepted at once.
--     A user who turns on "private profile" (profile_visibility = 'followers')
--     gets follow requests instead (follow_user already does this).
--   * Activities still default to 'followers' (profile_settings.default_visibility),
--     so an open profile does not make activities public.
--   * Minors (a birthday under 18 at sign-up) start with a private profile, in
--     line with the stricter minor defaults in profile_settings_guard.
--
-- Sign-up is email + password without confirmation; the auth trigger reads the
-- profile fields from the sign-up metadata (handle, first_name, last_name,
-- country, birthday). Two sign-up failures used to surface only as GoTrue's
-- opaque "Database error saving new user": a taken handle and a malformed
-- birthday. A taken handle now falls back to a generated one (the app checks
-- is_handle_available first, so this only happens on a race), and a malformed
-- birthday is ignored like a missing one. The age gate still rejects sign-ups
-- under the minimum age.

alter table public.profiles alter column profile_visibility set default 'everyone';

comment on column public.profiles.profile_visibility is
  'everyone (default): anyone may follow at once. followers ("private profile"): non-followers see name, photo and approximate location only, and follows need approval.';

create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_meta     jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_handle   text  := nullif(btrim(v_meta ->> 'handle'), '');
  v_country  text  := upper(nullif(v_meta ->> 'country', ''));
  v_birthday date;
begin
  if v_handle is null or v_handle !~ '^[A-Za-z0-9_.]{3,30}$'
     or exists (select 1 from public.profiles p where p.handle = v_handle::extensions.citext) then
    v_handle := 'user_' || substr(replace(new.id::text, '-', ''), 1, 12);
  end if;

  if (v_meta ->> 'birthday') ~ '^\d{4}-\d{2}-\d{2}$' then
    begin
      v_birthday := (v_meta ->> 'birthday')::date;
    exception when others then
      v_birthday := null;
    end;
  end if;

  insert into public.profiles (id, handle, first_name, last_name, country, profile_visibility)
  values (
    new.id,
    v_handle,
    coalesce(left(btrim(v_meta ->> 'first_name'), 60), ''),
    coalesce(left(btrim(v_meta ->> 'last_name'), 60), ''),
    case when v_country ~ '^[A-Z]{2}$' then v_country end,
    case when v_birthday is not null and private.age_years(v_birthday) < 18
         then 'followers' else 'everyone' end::public.profile_visibility);

  -- The age gate in profile_settings_guard raises here, rejecting the sign-up.
  insert into public.profile_settings (user_id, birthday)
  values (new.id, v_birthday);

  return new;
end;
$$;

-- True when nobody uses this handle yet and it has a valid shape. Handles are
-- public on every profile card, so answering this reveals nothing new.
create or replace function public.is_handle_available(p_handle text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_handle ~ '^[A-Za-z0-9_.]{3,30}$'
     and not exists (select 1 from public.profiles p
                      where p.handle = p_handle::extensions.citext);
$$;

comment on function public.is_handle_available(text) is
  'Sign-up helper: true when the handle is well formed and free (case-insensitive).';

revoke execute on function public.is_handle_available(text) from public;
grant execute on function public.is_handle_available(text) to anon, authenticated, service_role;
