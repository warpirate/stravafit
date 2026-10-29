-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Storage buckets and object policies.
--
-- Path conventions (first folder is always the owner's user id):
--   originals/<owner>/<upload_id>.<fit|gpx|tcx|json>     raw uploads, owner + worker only
--   streams/<owner>/<activity_id>/owner.sfstream          full-resolution streams, owner only
--   streams/<owner>/<activity_id>/public.sfstream         privacy-masked derivative, viewers
--   photo-uploads/<owner>/<activity_id>/<photo>.<ext>     staging; the worker strips EXIF/GPS,
--                                                         resizes, writes photos/, deletes this
--   photos/<owner>/<activity_id>/<photo>.webp             cleaned photos, activity viewers
--   avatars/<owner>/<file>                                public bucket
--   exports/<owner>/<archive>.zip                         account archives, owner only
--
-- Writes to streams/, photos/ and exports/ are done by the worker with the
-- service role, which bypasses RLS. Photos are never served from the staging
-- bucket, so original EXIF (including GPS) never reaches another user.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
  ('originals', 'originals', false, 104857600,
     array['application/octet-stream', 'application/vnd.ant.fit', 'application/gpx+xml',
           'application/vnd.garmin.tcx+xml', 'application/xml', 'text/xml',
           'application/json', 'application/gzip']),
  ('streams', 'streams', false, 104857600, array['application/octet-stream']),
  ('photo-uploads', 'photo-uploads', false, 26214400,
     array['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']),
  ('photos', 'photos', false, 10485760, array['image/webp', 'image/jpeg']),
  ('avatars', 'avatars', true, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
  ('exports', 'exports', false, null, array['application/zip'])
on conflict (id) do nothing;

-- Parses a path segment as uuid, or NULL when it is not one (never raises).
create or replace function private.try_uuid(p_text text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_text ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    then p_text::uuid end;
$$;

-- Photos follow the activity, except that strangers only see them when the
-- owner's profile page is public (settings-privacy-units §6.2).
create or replace function private.can_view_activity_photos(p_activity uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.can_view_activity(p_activity)
     and (private.viewer_relation(a.owner_id) in ('self', 'follower')
          or p.profile_visibility = 'everyone')
    from public.activities a
    join public.profiles p on p.id = a.owner_id
   where a.id = p_activity;
$$;

-- originals: upload only to the path reserved by create_upload, while the
-- upload still waits for its file.
create policy originals_owner_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'originals'
    and exists (select 1 from public.uploads u
                 where u.storage_path = name
                   and u.owner_id = (select auth.uid())
                   and u.status in ('awaiting_file', 'error')));

create policy originals_owner_select on storage.objects
  for select to authenticated
  using (bucket_id = 'originals'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- streams: owner reads everything in their folder; viewers read public.* only.
create policy streams_owner_select on storage.objects
  for select to authenticated
  using (bucket_id = 'streams'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy streams_public_select on storage.objects
  for select to anon, authenticated
  using (bucket_id = 'streams'
         and storage.filename(name) like 'public.%'
         and private.can_view_activity(private.try_uuid((storage.foldername(name))[2])));

-- photo staging: owner only, into a folder of one of their own activities.
create policy photo_uploads_owner_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'photo-uploads'
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and private.activity_owner(private.try_uuid((storage.foldername(name))[2])) = (select auth.uid()));

create policy photo_uploads_owner_select on storage.objects
  for select to authenticated
  using (bucket_id = 'photo-uploads'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy photo_uploads_owner_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'photo-uploads'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- cleaned photos: whoever may see the activity's photos.
create policy photos_viewer_select on storage.objects
  for select to anon, authenticated
  using (bucket_id = 'photos'
         and coalesce(private.can_view_activity_photos(
               private.try_uuid((storage.foldername(name))[2])), false));

-- avatars: public read (bucket is public), owner writes in own folder.
create policy avatars_owner_insert on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars'
              and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_owner_update on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars'
         and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'avatars'
              and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_owner_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'avatars'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_public_select on storage.objects
  for select to anon, authenticated
  using (bucket_id = 'avatars');

-- exports: owner downloads their own archives.
create policy exports_owner_select on storage.objects
  for select to authenticated
  using (bucket_id = 'exports'
         and (storage.foldername(name))[1] = (select auth.uid())::text);
