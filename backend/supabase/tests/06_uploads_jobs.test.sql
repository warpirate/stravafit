-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Upload lifecycle, idempotency keys and the job queue (table fallback or pgmq).
begin;
select plan(30);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select, insert, update on ids to anon, authenticated;

insert into ids values ('u', tests.create_user('up_user')), ('x', tests.create_user('up_other'));

-- Registering an upload is idempotent per client_uuid.
select tests.authenticate_as((select id from ids where k = 'u'));
insert into ids
select 'up1', (public.create_upload('11111111-1111-4111-8111-111111111111', 'fit', 2048,
               repeat('ab', 32))).id;
select is((select status from public.uploads where id = (select id from ids where k = 'up1')),
  'awaiting_file'::public.upload_status, 'new upload waits for its file');
select is((public.create_upload('11111111-1111-4111-8111-111111111111', 'fit', 2048)).id,
  (select id from ids where k = 'up1'), 'same client_uuid returns the same upload');
select is((select storage_path from public.uploads where id = (select id from ids where k = 'up1')),
  (select id from ids where k = 'u')::text || '/' || (select id from ids where k = 'up1')::text || '.fit',
  'storage path is reserved under the owner folder');

select throws_ok($$select public.submit_upload((select id from ids where k = 'up1'))$$,
  '55000', null, 'submitting before the file exists is rejected');

-- Storage: only the reserved path is writable.
select throws_ok(
  $$insert into storage.objects (bucket_id, name, owner)
    values ('originals', (select id from ids where k = 'u')::text || '/random.fit', (select id from ids where k = 'u'))$$,
  '42501', null, 'arbitrary paths in originals are rejected');
select lives_ok(
  $$insert into storage.objects (bucket_id, name, owner)
    select 'originals', storage_path, owner_id from public.uploads where id = (select id from ids where k = 'up1')$$,
  'the reserved path accepts the file');

select is((public.submit_upload((select id from ids where k = 'up1'))).status,
  'queued'::public.upload_status, 'submit queues the upload');
select is((public.submit_upload((select id from ids where k = 'up1'))).attempts,
  1::smallint, 'submitting twice is a no-op');
select throws_ok(
  $$update public.uploads set status = 'ready' where id = (select id from ids where k = 'up1')$$,
  '42501', null, 'clients cannot change upload status directly');

-- Someone else cannot see or submit the upload.
select tests.authenticate_as((select id from ids where k = 'x'));
select is((select count(*)::int from public.uploads), 0, 'uploads are owner-only');
select throws_ok($$select public.submit_upload((select id from ids where k = 'up1'))$$,
  'P0002', null, 'another user cannot submit it');

select tests.authenticate_anon();
select throws_ok($$select public.create_upload(gen_random_uuid(), 'gpx', 10)$$,
  '42501', null, 'anonymous users cannot register uploads');

-- Worker side.
select tests.as_backend();
select is(tests.job_count('ingest'), 1,
  'exactly one ingest job was enqueued');
create temp table claimed on commit drop as select * from private.job_claim('ingest', 10, 60);
select is((select count(*)::int from claimed), 1, 'worker claims the job');
select is((select payload ->> 'upload_id' from claimed), (select id::text from ids where k = 'up1'),
  'job payload carries the upload id');
select is((select count(*)::int from private.job_claim('ingest', 10, 60)), 0,
  'a leased job is invisible to other workers');

select is((private.worker_upload_started((select id from ids where k = 'up1'))).status,
  'processing'::public.upload_status, 'worker marks the upload processing');
select is((private.worker_upload_failed((select id from ids where k = 'up1'), 'corrupt FIT header')).status,
  'error'::public.upload_status, 'worker records a parse error');
select ok(private.job_fail('ingest', (select msg_id from claimed), 'corrupt FIT header'),
  'job_fail schedules a retry');
select ok(tests.job_visible_at('ingest', (select msg_id from claimed)) > now(),
  'the failed job is backed off');
select is((select count(*)::int from public.notifications
            where recipient_id = (select id from ids where k = 'u') and kind = 'upload_failed'), 1,
  'the owner is notified about the failed upload');

select throws_ok(
  $$update public.uploads set status = 'ready' where id = (select id from ids where k = 'up1')$$,
  '23514', null, 'invalid status transitions are rejected even for the backend');

-- Retry by the user, then success.
select tests.authenticate_as((select id from ids where k = 'u'));
select is((public.submit_upload((select id from ids where k = 'up1'))).attempts, 2::smallint,
  'resubmitting an errored upload counts an attempt');
select tests.as_backend();
insert into ids values ('act', tests.create_activity((select id from ids where k = 'u'), 'followers', 'Uploaded run'));
select private.worker_upload_started((select id from ids where k = 'up1'));
select is((private.worker_upload_finished((select id from ids where k = 'up1'),
                                          (select id from ids where k = 'act'))).status,
  'ready'::public.upload_status, 'worker finishes the upload');
select is((select upload_id from public.activities where id = (select id from ids where k = 'act')),
  (select id from ids where k = 'up1'), 'the activity links back to its upload');
select ok(private.job_complete('ingest', (select msg_id from claimed)), 'job completed');

-- Dead-lettering after max attempts.
do $$
declare
  v_id bigint := private.job_enqueue('media', '{"photo": "p1"}');
  i int;
begin
  for i in 1..5 loop
    perform tests.job_make_visible('media', v_id);
    perform private.job_claim('media', 1, 30);
    perform private.job_fail('media', v_id, 'resize failed');
  end loop;
end
$$;
select is((select count(*)::int from private.job_failures where queue = 'media' and dead), 1,
  'a job that keeps failing is dead-lettered');

-- Idempotency: an existing activity with the same client_uuid wins.
select tests.authenticate_as((select id from ids where k = 'u'));
select is(
  (public.create_upload((select client_uuid from public.activities where id = (select id from ids where k = 'act')),
                        'gpx', 512)).status,
  'duplicate'::public.upload_status, 'an upload for an existing recording is a duplicate');
select is_empty(
  $$insert into public.activities (owner_id, client_uuid, name, sport_type, start_time, elapsed_s, moving_s)
    select owner_id, client_uuid, 'again', 'Run', now(), 1, 1 from public.activities
     where id = (select id from ids where k = 'act')
    on conflict on constraint activities_owner_client_uuid_key do nothing
    returning id$$,
  're-sending a recording with the same client_uuid is a no-op');

-- Deleting an activity enqueues storage cleanup.
delete from public.activities where id = (select id from ids where k = 'act');
select tests.as_backend();
select is(tests.job_count('cleanup', 'activity_id', (select id::text from ids where k = 'act')), 1,
  'deleting an activity enqueues a cleanup job');

select * from finish();
rollback;
