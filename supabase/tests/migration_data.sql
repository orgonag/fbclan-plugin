DO $$ BEGIN
 IF (SELECT count(*) FROM fb_private.legacy_personal_bests)<>2 THEN RAISE EXCEPTION 'PB archive incomplete'; END IF;
 IF (SELECT count(*) FROM public.personal_bests)<>1 OR (SELECT seconds FROM public.personal_bests WHERE rsn='legacy')<>70 THEN RAISE EXCEPTION 'PB normalization lost best'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.member_stats WHERE rsn='legacy' AND cl_obtained=100 AND cl_total=1500 AND ca_points=200 AND ca_tier='Medium') THEN RAISE EXCEPTION 'stats merge lost independent maxima'; END IF;
 IF (SELECT count(*) FROM public.lfg_formed_parties)<>2 OR (SELECT count(party_id) FROM public.lfg_formed_parties)<>1 THEN RAISE EXCEPTION 'formed migration lost archive or uniqueness'; END IF;
END $$;
