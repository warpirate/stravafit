-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Realtime publication and a schema-wide safety net.

-- Realtime: the app listens for its own upload status and inbox rows. Realtime
-- applies RLS, so each user only receives their own changes.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.uploads, public.notifications;
  else
    raise notice 'supabase_realtime publication missing: skipping realtime setup';
  end if;
end
$$;

-- Guard: fail the migration if any table in an API-exposed schema lacks RLS.
-- New tables added by later migrations are caught by re-running this check.
create or replace function private.assert_rls_everywhere()
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_missing text;
begin
  select string_agg(format('%I.%I', n.nspname, c.relname), ', ' order by c.relname)
    into v_missing
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
   where c.relkind in ('r', 'p')
     and n.nspname in ('public', 'private')
     and not c.relrowsecurity
     -- PostGIS metadata table living in public on some installs.
     and c.relname <> 'spatial_ref_sys';
  if v_missing is not null then
    raise exception 'tables without row level security: %', v_missing;
  end if;
end;
$$;

do $$ begin perform private.assert_rls_everywhere(); end $$;
