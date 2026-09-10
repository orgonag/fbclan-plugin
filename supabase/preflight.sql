-- Read-only. Export these results and make a full database backup before cutover.
SELECT version();
SELECT lower(btrim(replace(host_rsn,chr(160),' '))) normalized_host,count(*)
 FROM public.lfg_parties GROUP BY 1 HAVING count(*)>1;
SELECT p.id,p.host_rsn,a.party_id,a.rsn,a.status FROM public.lfg_parties p
 JOIN public.lfg_applicants a ON lower(btrim(replace(p.host_rsn,chr(160),' ')))=lower(btrim(replace(a.rsn,chr(160),' ')));
SELECT 'drops' relation,count(*) FROM public.drops UNION ALL
 SELECT 'personal_bests',count(*) FROM public.personal_bests UNION ALL
 SELECT 'member_stats',count(*) FROM public.member_stats UNION ALL
 SELECT 'lfg_parties',count(*) FROM public.lfg_parties UNION ALL
 SELECT 'lfg_formed_parties',count(*) FROM public.lfg_formed_parties;
SELECT schemaname,tablename,policyname,roles,cmd,qual,with_check FROM pg_policies
 WHERE schemaname IN('public','storage');
SELECT id,name,public,file_size_limit,allowed_mime_types FROM storage.buckets WHERE id='drop-screenshots';
-- If pg_cron is enabled: SELECT jobid,jobname,schedule,command,active FROM cron.job;
