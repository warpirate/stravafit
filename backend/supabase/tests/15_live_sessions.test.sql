-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Live location sessions: owner-only REST access, token reads for the service
-- role only, rate limit, trail cap, end and expiry.
begin;
select plan(14);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select, insert on ids to anon, authenticated;
create temp table s (k text primary key, v text) on commit drop;
grant select, insert on s to anon, authenticated, service_role;

insert into ids values
  ('owner',    tests.create_user('ls_owner')),
  ('stranger', tests.create_user('ls_stranger'));

select tests.authenticate_as((select id from ids where k = 'owner'));
insert into s select 'id', id::text from public.start_live_session('Ride', 120);
insert into s select 'token', token from public.live_sessions;

select ok((select v ~ '^[A-Za-z0-9_-]{32}$' from s where k = 'token'), 'the share token is 32 url-safe characters');
-- 250 points over the last three hours: the trail keeps the newest 240.
select count(*) from generate_series(1, 250) i,
  lateral (select public.update_live_position((select v::uuid from s where k = 'id'),
             52.52 + i * 0.0001, 13.405, 5, 5, 80::smallint,
             now() - interval '3 hours' + make_interval(secs => 30 * i)) as ok) x;
select ok(public.update_live_position((select v::uuid from s where k = 'id'), 52.52, 13.405, 8, 5.5, 81::smallint,
                                      now() - interval '10 minutes'),
  'the owner records a position');
select ok(not public.update_live_position((select v::uuid from s where k = 'id'), 52.53, 13.41, 8, 5.5, 80::smallint,
                                          now() - interval '10 minutes' + interval '2 seconds'),
  'updates closer than 5 s apart are ignored');
select is((select jsonb_array_length(trail) from public.live_sessions), 240, 'the trail is capped at 240 points');

select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select count(*)::int from public.live_sessions), 0, 'others cannot read the session over REST');
select throws_ok($$select public.get_live_session_by_token((select v from s where k = 'token'))$$,
  '42501', null, 'only the service role may read a session by token');
select throws_ok($$select public.update_live_position((select v::uuid from s where k = 'id'), 0, 0)$$,
  'P0002', null, 'others cannot write positions');

-- The live page's view of it (service role).
select tests.as_backend();
set local role service_role;
select is((public.get_live_session_by_token((select v from s where k = 'token')) ->> 'state'), 'live',
  'the session reads as live');
select is((public.get_live_session_by_token((select v from s where k = 'token')) -> 'position' ->> 'battery_pct'), '81',
  'the newest position (by time) is the one shown');
select is(public.get_live_session_by_token('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'), null::jsonb,
  'an unknown token reads nothing');
reset role;

-- Ending: no more position, no more updates.
select tests.authenticate_as((select id from ids where k = 'owner'));
select ok(public.end_live_session((select v::uuid from s where k = 'id')), 'the owner ends the session');
select throws_ok($$select public.update_live_position((select v::uuid from s where k = 'id'), 52.5, 13.4)$$,
  '55000', null, 'an ended session takes no positions');
select tests.as_backend();
select results_eq(
  $$select x ->> 'state', jsonb_typeof(x -> 'position'), jsonb_array_length(x -> 'trail')
      from (select public.get_live_session_by_token((select v from s where k = 'token')) x) q$$,
  $$values ('ended'::text, 'null'::text, 0)$$, 'an ended session shows its state only');

-- Expiry: an old session reads as expired.
update public.live_sessions set ended_at = null, started_at = now() - interval '3 hours',
  expires_at = now() - interval '1 hour'
 where token = (select v from s where k = 'token');
select is(public.get_live_session_by_token((select v from s where k = 'token')) ->> 'state', 'expired',
  'a session past its expiry reads as expired');

select * from finish();
rollback;
