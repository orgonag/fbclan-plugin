-- Run once after 001_v3.sql in the SAME Supabase TEST project first.
-- Supabase-specific services; not part of the portable PostgreSQL fixture.
BEGIN;
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 VALUES('drop-screenshots','drop-screenshots',true,8388608,ARRAY['image/png'])
 ON CONFLICT(id) DO UPDATE SET public=true,file_size_limit=8388608,allowed_mime_types=ARRAY['image/png'];
-- Existing permissive INSERT policies combine with OR. Remove policies scoped to
-- this bucket, leaving policies for other buckets alone. Inspect the result below.
DO $$ DECLARE p record; BEGIN
 FOR p IN SELECT policyname FROM pg_policies WHERE schemaname='storage' AND tablename='objects'
   AND cmd='INSERT' AND with_check LIKE '%drop-screenshots%' LOOP
  EXECUTE format('DROP POLICY %I ON storage.objects',p.policyname);
 END LOOP;
END $$;
CREATE POLICY fb_screenshot_insert ON storage.objects FOR INSERT TO anon
 WITH CHECK(bucket_id='drop-screenshots' AND name ~ '^[0-9a-f-]{36}[.]png$');
COMMIT;
-- Enable pg_cron in Supabase Database > Extensions before this section if absent.
CREATE EXTENSION IF NOT EXISTS pg_cron;
SELECT cron.schedule('fb-v3-cleanup','*/5 * * * *','SELECT public.fb_cleanup()');
SELECT policyname,cmd,roles,qual,with_check FROM pg_policies WHERE schemaname='storage' AND tablename='objects';
SELECT jobid,jobname,schedule,command,active FROM cron.job;
-- Disable an old LFG cleanup job if it targets these tables using different TTLs.
-- Do not disable unrelated WOM/content jobs.
