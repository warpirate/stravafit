-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Groups: creation, members-only visibility, invite token join, rotate and
-- revoke, roles, and leaving.
begin;
select plan(20);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;
create temp table g (k text primary key, v text) on commit drop;
grant select, insert, update on g to anon, authenticated;

insert into ids values
  ('owner',    tests.create_user('gm_owner')),
  ('member',   tests.create_user('gm_member')),
  ('outsider', tests.create_user('gm_outsider')),
  ('blocked',  tests.create_user('gm_blocked'));
insert into public.blocks (blocker_id, blocked_id)
select (select id from ids where k = 'owner'), (select id from ids where k = 'blocked');

select tests.authenticate_as((select id from ids where k = 'owner'));
insert into g select 'id', (public.create_group('Sunrise Runners', 'Run', 'Easy miles', 'Asia/Kolkata')).id::text;
insert into g select 'token', invite_token from public.groups;

select is((select member_count from public.groups), 1, 'the creator is the only member');
select is((select role from public.group_members), 'owner'::public.group_role, 'the creator is the owner');
select ok((select v ~ '^[A-Za-z0-9_-]{24}$' from g where k = 'token'), 'the invite token is 24 url-safe characters');
select throws_ok($$select public.create_group('x', null, null, 'Mars/Olympus_Mons')$$,
  '22023', null, 'unknown timezones are rejected');

-- Outsiders see nothing, but the invite preview works with the token.
select tests.authenticate_as((select id from ids where k = 'outsider'));
select is((select count(*)::int from public.groups), 0, 'non-members cannot see the group');
select tests.authenticate_anon();
select is((select name from public.get_group_invite((select v from g where k = 'token'))), 'Sunrise Runners',
  'the invite preview shows the group name, even signed out');
select is((select count(*)::int from public.get_group_invite('AAAAAAAAAAAAAAAAAAAAAAAA')), 0,
  'a wrong token previews nothing');

-- Joining with the token.
select tests.authenticate_as((select id from ids where k = 'member'));
select is(public.join_group((select v from g where k = 'token'))::text, (select v from g where k = 'id'),
  'join_group returns the group id');
select is(public.join_group((select v from g where k = 'token'))::text, (select v from g where k = 'id'),
  'joining twice is a no-op');
select is((select member_count from public.groups), 2, 'member count follows joins');
select is((select count(*)::int from public.get_group_members((select v::uuid from g where k = 'id'))), 2,
  'members see the member list');
select throws_ok($$select public.rotate_group_invite((select v::uuid from g where k = 'id'))$$,
  'P0002', null, 'plain members cannot rotate the invite');

select tests.authenticate_as((select id from ids where k = 'blocked'));
select throws_ok($$select public.join_group((select v from g where k = 'token'))$$,
  'P0002', null, 'someone blocked by the owner cannot join');

-- Rotating kills the old link; revoking turns invites off.
select tests.authenticate_as((select id from ids where k = 'owner'));
insert into g select 'token2', public.rotate_group_invite((select v::uuid from g where k = 'id'));
select tests.authenticate_as((select id from ids where k = 'outsider'));
select throws_ok($$select public.join_group((select v from g where k = 'token'))$$,
  'P0002', null, 'the old link stops working after a rotation');
select tests.authenticate_as((select id from ids where k = 'owner'));
select ok(public.revoke_group_invite((select v::uuid from g where k = 'id')), 'invites revoked');
select tests.authenticate_as((select id from ids where k = 'outsider'));
select throws_ok($$select public.join_group((select v from g where k = 'token2'))$$,
  'P0002', null, 'no link works while invites are off');

-- Roles: only the owner promotes; ownership can be handed over.
select tests.authenticate_as((select id from ids where k = 'member'));
select throws_ok($$select public.set_group_member_role((select v::uuid from g where k = 'id'),
                     (select id from ids where k = 'owner'), 'member')$$,
  '42501', null, 'members cannot change roles');
select tests.authenticate_as((select id from ids where k = 'owner'));
select ok(public.set_group_member_role((select v::uuid from g where k = 'id'),
            (select id from ids where k = 'member'), 'admin'), 'the owner makes a member admin');

-- The owner cannot leave (the delete policy filters the row out).
delete from public.group_members where user_id = (select id from ids where k = 'owner');
select is((select role from public.group_members where user_id = (select id from ids where k = 'owner')),
  'owner'::public.group_role, 'the owner cannot leave without handing over');

-- Admins may edit the group profile. (Leaving is tested in 14_groups_leaderboard.)
select tests.authenticate_as((select id from ids where k = 'member'));
select lives_ok($$update public.groups set description = 'Admins can edit' where id = (select v::uuid from g where k = 'id')$$, 'admins edit the group profile');

select * from finish();
rollback;
