-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Split replacement for upsert_activity_from_device (20261103000200), the same
-- way as laps. The device stores splits per unit system (METRIC / IMPERIAL);
-- the server stores km / mi. Both spellings are accepted.

create or replace function private.device_replace_splits(p_activity uuid, p_splits jsonb)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_item jsonb;
  v_unit text;
begin
  delete from public.splits s where s.activity_id = p_activity;
  for v_item in select * from jsonb_array_elements(coalesce(p_splits, '[]')) loop
    v_unit := case lower(coalesce(v_item ->> 'unit', ''))
                when 'km' then 'km' when 'metric' then 'km'
                when 'mi' then 'mi' when 'imperial' then 'mi' end;
    if v_unit is null then
      raise exception 'split unit must be km or mi' using errcode = 'invalid_parameter_value';
    end if;
    insert into public.splits (activity_id, owner_id, unit, split_index, distance_m, elapsed_s,
      moving_s, elev_diff_m, avg_speed_mps, gap_speed_mps, avg_hr, avg_watts)
    values (p_activity, auth.uid(), v_unit::public.split_unit,
      round(private.j_num(v_item, 'split_index'))::smallint,
      coalesce(private.j_num(v_item, 'distance_m'), 0),
      round(coalesce(private.j_num(v_item, 'elapsed_s'), 0))::integer,
      round(coalesce(private.j_num(v_item, 'moving_s'), 0))::integer,
      private.j_num(v_item, 'elev_diff_m'), private.j_num(v_item, 'avg_speed_mps'),
      private.j_num(v_item, 'gap_speed_mps'), private.j_num(v_item, 'avg_hr'),
      private.j_num(v_item, 'avg_watts'));
  end loop;
end;
$$;

revoke execute on function private.device_replace_splits(uuid, jsonb) from public, anon;
grant execute on function private.device_replace_splits(uuid, jsonb) to authenticated, service_role;
