-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Lap replacement for upsert_activity_from_device (20261103000200). The
-- device's lap list is the truth, so the activity's laps are replaced as a
-- whole. Runs as the caller: RLS on laps applies (owner only), and the laps
-- triggers copy the owner and strip heart rate without health-data consent.
-- Unknown lap triggers fall back to 'manual' so a newer app never fails sync.

create or replace function private.device_replace_laps(p_activity uuid, p_laps jsonb)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_item    jsonb;
  v_trigger text;
begin
  delete from public.laps l where l.activity_id = p_activity;
  for v_item in select * from jsonb_array_elements(coalesce(p_laps, '[]')) loop
    v_trigger := lower(coalesce(v_item ->> 'trigger', 'manual'));
    if v_trigger not in ('manual', 'distance', 'time', 'position', 'session_end') then
      v_trigger := 'manual';
    end if;
    insert into public.laps (activity_id, owner_id, lap_index, name, trigger, start_offset_s,
      elapsed_s, moving_s, distance_m, elev_gain_m, avg_speed_mps, max_speed_mps, avg_hr,
      max_hr, avg_cadence, avg_watts, max_watts)
    values (p_activity, auth.uid(), round(private.j_num(v_item, 'lap_index'))::smallint,
      nullif(left(v_item ->> 'name', 80), ''), v_trigger::public.lap_trigger,
      round(coalesce(private.j_num(v_item, 'start_offset_s'), 0))::integer,
      round(coalesce(private.j_num(v_item, 'elapsed_s'), 0))::integer,
      round(coalesce(private.j_num(v_item, 'moving_s'), 0))::integer,
      coalesce(private.j_num(v_item, 'distance_m'), 0),
      private.j_num(v_item, 'elev_gain_m'), private.j_num(v_item, 'avg_speed_mps'),
      private.j_num(v_item, 'max_speed_mps'), private.j_num(v_item, 'avg_hr'),
      round(private.j_num(v_item, 'max_hr'))::smallint, private.j_num(v_item, 'avg_cadence'),
      private.j_num(v_item, 'avg_watts'), round(private.j_num(v_item, 'max_watts'))::smallint);
  end loop;
end;
$$;

revoke execute on function private.device_replace_laps(uuid, jsonb) from public, anon;
grant execute on function private.device_replace_laps(uuid, jsonb) to authenticated, service_role;
