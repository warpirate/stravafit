-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Fix for 20261103000100: the handle checks compared citext values with a bare
-- `=` under `search_path = ''`, which resolves to text equality (the citext
-- operator lives in the extensions schema). A handle differing only in case
-- then slipped past the check and the unique index rejected the sign-up. The
-- citext operator is now named explicitly, so the comparison is
-- case-insensitive and can use the unique index.

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
     or exists (select 1 from public.profiles p
                 where p.handle operator(extensions.=) v_handle::extensions.citext) then
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

create or replace function public.is_handle_available(p_handle text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_handle ~ '^[A-Za-z0-9_.]{3,30}$'
     and not exists (select 1 from public.profiles p
                      where p.handle operator(extensions.=) p_handle::extensions.citext);
$$;
