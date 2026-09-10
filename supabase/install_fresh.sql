-- EMPTY SUPABASE PROJECT ONLY. Existing project: use migrations/001_v3.sql instead.
BEGIN;
-- EXPORTED BASELINE: disposable test database only; never run over production.

CREATE TABLE public."announcements" (
  "id" bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  "posted_at" date NOT NULL,
  "title" text NOT NULL,
  "body" text NOT NULL,
  "sort_order" integer DEFAULT 0 NOT NULL
);

CREATE TABLE public."drops" (
  "id" uuid DEFAULT gen_random_uuid() NOT NULL,
  "rsn" text NOT NULL,
  "npc_name" text NOT NULL,
  "item_name" text NOT NULL,
  "item_id" integer NOT NULL,
  "ge_value" integer NOT NULL,
  "quantity" integer DEFAULT 1 NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "screenshot_url" text,
  "rarity" double precision
);

CREATE TABLE public."lfg_applicants" (
  "party_id" uuid NOT NULL,
  "rsn" text NOT NULL,
  "role" text,
  "learner" boolean DEFAULT false NOT NULL,
  "status" text DEFAULT 'PENDING'::text NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL,
  "kc" integer,
  "kc_source" text,
  "added_by_host" boolean DEFAULT false NOT NULL
);

CREATE TABLE public."lfg_entries" (
  "id" uuid DEFAULT gen_random_uuid() NOT NULL,
  "rsn" text NOT NULL,
  "activity" text NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL,
  "party_id" text,
  "party_size" smallint,
  "note" text,
  "ttl_minutes" integer
);

CREATE TABLE public."lfg_formed_parties" (
  "id" uuid DEFAULT gen_random_uuid() NOT NULL,
  "party_id" uuid,
  "host_rsn" text NOT NULL,
  "activity" text NOT NULL,
  "hard_mode" boolean DEFAULT false NOT NULL,
  "invocation" integer DEFAULT 0 NOT NULL,
  "capacity" integer NOT NULL,
  "world" integer,
  "members" jsonb DEFAULT '[]'::jsonb NOT NULL,
  "formed_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public."lfg_parties" (
  "id" uuid DEFAULT gen_random_uuid() NOT NULL,
  "host_rsn" text NOT NULL,
  "activity" text NOT NULL,
  "hard_mode" boolean DEFAULT false NOT NULL,
  "invocation" integer DEFAULT 0 NOT NULL,
  "capacity" integer NOT NULL,
  "description" text,
  "world" integer,
  "min_kc" integer DEFAULT 0 NOT NULL,
  "loot_rule" text DEFAULT 'UNSPECIFIED'::text NOT NULL,
  "required_roles" text,
  "host_role" text,
  "learner" boolean DEFAULT false NOT NULL,
  "teacher" boolean DEFAULT false NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL,
  "ttl_minutes" integer DEFAULT 30 NOT NULL
);

CREATE TABLE public."member_stats" (
  "rsn" text NOT NULL,
  "cl_obtained" integer,
  "cl_total" integer,
  "ca_points" integer,
  "ca_tier" text,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public."notable_items" (
  "name" text NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public."personal_bests" (
  "rsn" text NOT NULL,
  "boss_key" text NOT NULL,
  "seconds" numeric(10,2) NOT NULL,
  "achieved_at" timestamp with time zone DEFAULT now() NOT NULL,
  "source" text NOT NULL
);

CREATE TABLE public."welcome_message" (
  "id" smallint NOT NULL,
  "message" text NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public."wom_cache" (
  "metric" text NOT NULL,
  "payload" jsonb NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE public."announcements" ADD CONSTRAINT "announcements_pkey" PRIMARY KEY (id);

ALTER TABLE public."drops" ADD CONSTRAINT "drops_pkey" PRIMARY KEY (id);

ALTER TABLE public."drops" ADD CONSTRAINT "drops_rarity_check" CHECK (rarity IS NULL OR rarity > 0::double precision AND rarity <= 1::double precision);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_kc_check" CHECK (kc IS NULL OR kc >= 0 AND kc <= 100000);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_kc_source_check" CHECK (kc_source IS NULL OR (kc_source = ANY (ARRAY['LOCAL'::text, 'HISCORES'::text, 'MANUAL'::text])));

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_pkey" PRIMARY KEY (party_id, rsn);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_role_shape" CHECK (role IS NULL OR role ~ '^[A-Z][A-Z0-9_]{0,31}$'::text);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_rsn_shape" CHECK (char_length(btrim(rsn)) >= 1 AND char_length(btrim(rsn)) <= 12);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_status_check" CHECK (status = ANY (ARRAY['PENDING'::text, 'ACCEPTED'::text, 'DECLINED'::text]));

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_activity_shape" CHECK (activity ~ '^[A-Z][A-Z0-9_]{0,31}$'::text);

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_note_length" CHECK (note IS NULL OR char_length(note) <= 60);

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_pkey" PRIMARY KEY (id);

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_rsn_key" UNIQUE (rsn);

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_rsn_shape" CHECK (char_length(btrim(rsn)) >= 1 AND char_length(btrim(rsn)) <= 12);

ALTER TABLE public."lfg_entries" ADD CONSTRAINT "lfg_entries_ttl_range" CHECK (ttl_minutes IS NULL OR ttl_minutes >= 10 AND ttl_minutes <= 720);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_activity_check" CHECK (activity ~ '^[A-Z][A-Z0-9_]{0,31}$'::text);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_capacity_check" CHECK (capacity >= 2 AND capacity <= 100);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_host_rsn_check" CHECK (char_length(btrim(host_rsn)) >= 1 AND char_length(btrim(host_rsn)) <= 12);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_invocation_check" CHECK (invocation >= 0 AND invocation <= 600);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_members_check" CHECK (jsonb_typeof(members) = 'array'::text AND jsonb_array_length(members) <= 100);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_pkey" PRIMARY KEY (id);

ALTER TABLE public."lfg_formed_parties" ADD CONSTRAINT "lfg_formed_parties_world_check" CHECK (world IS NULL OR world >= 300 AND world <= 999);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_activity_shape" CHECK (activity ~ '^[A-Z][A-Z0-9_]{0,31}$'::text);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_capacity_check" CHECK (capacity >= 2 AND capacity <= 100);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_description_check" CHECK (description IS NULL OR char_length(description) <= 120);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_host_role_shape" CHECK (host_role IS NULL OR host_role ~ '^[A-Z][A-Z0-9_]{0,31}$'::text);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_host_rsn_key" UNIQUE (host_rsn);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_host_rsn_shape" CHECK (char_length(btrim(host_rsn)) >= 1 AND char_length(btrim(host_rsn)) <= 12);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_invocation_check" CHECK (invocation >= 0 AND invocation <= 600);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_loot_rule_check" CHECK (loot_rule = ANY (ARRAY['UNSPECIFIED'::text, 'FFA'::text, 'SPLIT'::text]));

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_min_kc_check" CHECK (min_kc >= 0);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_min_kc_range" CHECK (min_kc >= 0 AND min_kc <= 100000);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_pkey" PRIMARY KEY (id);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_roles_shape" CHECK (required_roles IS NULL OR required_roles ~ '^[A-Z][A-Z0-9_]*(,[A-Z][A-Z0-9_]*){0,99}$'::text);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_ttl_minutes_check" CHECK (ttl_minutes >= 10 AND ttl_minutes <= 720);

ALTER TABLE public."lfg_parties" ADD CONSTRAINT "lfg_parties_world_range" CHECK (world IS NULL OR world >= 300 AND world <= 999);

ALTER TABLE public."member_stats" ADD CONSTRAINT "member_stats_ca_points_check" CHECK (ca_points > 0 AND ca_points <= 10000);

ALTER TABLE public."member_stats" ADD CONSTRAINT "member_stats_cl_obtained_check" CHECK (cl_obtained > 0 AND cl_obtained <= 10000);

ALTER TABLE public."member_stats" ADD CONSTRAINT "member_stats_cl_total_check" CHECK (cl_total > 0 AND cl_total <= 10000);

ALTER TABLE public."member_stats" ADD CONSTRAINT "member_stats_pkey" PRIMARY KEY (rsn);

ALTER TABLE public."notable_items" ADD CONSTRAINT "notable_items_pkey" PRIMARY KEY (name);

ALTER TABLE public."personal_bests" ADD CONSTRAINT "personal_bests_pkey" PRIMARY KEY (rsn, boss_key);

ALTER TABLE public."personal_bests" ADD CONSTRAINT "personal_bests_seconds_check" CHECK (seconds > 0::numeric);

ALTER TABLE public."personal_bests" ADD CONSTRAINT "personal_bests_source_check" CHECK (source = ANY (ARRAY['live'::text, 'seed'::text]));

ALTER TABLE public."welcome_message" ADD CONSTRAINT "welcome_message_id_check" CHECK (id = 1);

ALTER TABLE public."welcome_message" ADD CONSTRAINT "welcome_message_pkey" PRIMARY KEY (id);

ALTER TABLE public."wom_cache" ADD CONSTRAINT "wom_cache_pkey" PRIMARY KEY (metric);

ALTER TABLE public."lfg_applicants" ADD CONSTRAINT "lfg_applicants_party_id_fkey" FOREIGN KEY (party_id) REFERENCES lfg_parties(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX lfg_applicants_one_party_per_rsn ON public.lfg_applicants USING btree (lower(btrim(rsn)));

CREATE INDEX lfg_applicants_rsn_idx ON public.lfg_applicants USING btree (rsn);

CREATE INDEX lfg_formed_parties_formed_at_idx ON public.lfg_formed_parties USING btree (formed_at DESC);

CREATE OR REPLACE FUNCTION public.lfg_check_applicant()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare
  p public.lfg_parties%rowtype;
  accepted integer;
begin
  select * into p from public.lfg_parties where id = new.party_id;
  if not found then
    raise exception 'party % does not exist', new.party_id using errcode = '23503';
  end if;
  if lower(btrim(p.host_rsn)) = lower(btrim(new.rsn)) then
    raise exception 'host cannot apply to their own party' using errcode = '23514';
  end if;
  if new.status = 'ACCEPTED' then
    select count(*) into accepted
      from public.lfg_applicants a
     where a.party_id = new.party_id
       and a.status = 'ACCEPTED'
       and lower(btrim(a.rsn)) <> lower(btrim(new.rsn));
    if 1 + accepted + 1 > p.capacity then
      raise exception 'party is full' using errcode = '23514';
    end if;
  end if;
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.lfg_clamp_timestamps()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  if tg_op = 'INSERT' then
    new.created_at := now();
  else
    new.created_at := old.created_at;
  end if;
  new.updated_at := least(coalesce(new.updated_at, now()), now());
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.lfg_entries_clamp_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  new.updated_at := least(coalesce(new.updated_at, now()), now());
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.lfg_formed_stamp()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
  new.formed_at := now();
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.submit_pbs(p_rsn text, p_entries jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    e jsonb;
    v_boss text;
    v_seconds numeric;
    v_source text;
begin
    if p_rsn is null or length(trim(p_rsn)) = 0 or length(p_rsn) > 30 then
        raise exception 'invalid rsn';
    end if;
    if p_entries is null or jsonb_typeof(p_entries) <> 'array'
        or jsonb_array_length(p_entries) > 300 then
        raise exception 'invalid entries';
    end if;

    for e in select * from jsonb_array_elements(p_entries) loop
        v_boss := lower(trim(e->>'boss_key'));
        v_seconds := (e->>'seconds')::numeric;
        v_source := e->>'source';
        if v_boss is null or length(v_boss) = 0 or length(v_boss) > 100
            or v_seconds is null or v_seconds <= 0 or v_seconds >= 86400
            or v_source is null or v_source not in ('live', 'seed') then
            continue;
        end if;

        insert into personal_bests (rsn, boss_key, seconds, source)
        values (trim(p_rsn), v_boss, v_seconds, v_source)
        on conflict (rsn, boss_key) do update
            set seconds = excluded.seconds,
                achieved_at = now(),
                source = excluded.source
            where personal_bests.seconds > excluded.seconds;
    end loop;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.submit_stats(p_rsn text, p_cl_obtained integer DEFAULT NULL::integer, p_cl_total integer DEFAULT NULL::integer, p_ca_points integer DEFAULT NULL::integer, p_ca_tier text DEFAULT NULL::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    if p_rsn is null or length(trim(p_rsn)) = 0 or length(p_rsn) > 30 then
        raise exception 'invalid rsn';
    end if;
    if p_cl_obtained is not null and (p_cl_total is null
        or p_cl_obtained <= 0 or p_cl_obtained > p_cl_total or p_cl_total > 10000) then
        raise exception 'invalid collection log counts';
    end if;
    if p_ca_points is not null and (p_ca_points <= 0 or p_ca_points > 10000) then
        raise exception 'invalid ca points';
    end if;
    if p_ca_tier is not null and p_ca_tier not in
        ('Easy', 'Medium', 'Hard', 'Elite', 'Master', 'Grandmaster') then
        raise exception 'invalid ca tier';
    end if;

    insert into member_stats (rsn, cl_obtained, cl_total, ca_points, ca_tier)
    values (trim(p_rsn), p_cl_obtained, p_cl_total, p_ca_points, p_ca_tier)
    on conflict (rsn) do update set
        cl_obtained = case
            when excluded.cl_obtained is not null
                and excluded.cl_obtained > coalesce(member_stats.cl_obtained, 0)
            then excluded.cl_obtained else member_stats.cl_obtained end,
        cl_total = case
            when excluded.cl_obtained is not null
                and excluded.cl_obtained > coalesce(member_stats.cl_obtained, 0)
            then excluded.cl_total else member_stats.cl_total end,
        ca_points = case
            when excluded.ca_points is not null
                and excluded.ca_points > coalesce(member_stats.ca_points, 0)
            then excluded.ca_points else member_stats.ca_points end,
        ca_tier = case
            when excluded.ca_points is not null
                and excluded.ca_points > coalesce(member_stats.ca_points, 0)
            then excluded.ca_tier else member_stats.ca_tier end,
        updated_at = now();
end;
$function$
;

CREATE TRIGGER lfg_applicants_check BEFORE INSERT OR UPDATE ON lfg_applicants FOR EACH ROW EXECUTE FUNCTION lfg_check_applicant();

CREATE TRIGGER lfg_applicants_clamp_timestamps BEFORE INSERT OR UPDATE ON lfg_applicants FOR EACH ROW EXECUTE FUNCTION lfg_clamp_timestamps();

CREATE TRIGGER lfg_entries_clamp_updated_at BEFORE INSERT OR UPDATE ON lfg_entries FOR EACH ROW EXECUTE FUNCTION lfg_entries_clamp_updated_at();

CREATE TRIGGER lfg_formed_parties_stamp BEFORE INSERT ON lfg_formed_parties FOR EACH ROW EXECUTE FUNCTION lfg_formed_stamp();

CREATE TRIGGER lfg_parties_clamp_timestamps BEFORE INSERT OR UPDATE ON lfg_parties FOR EACH ROW EXECUTE FUNCTION lfg_clamp_timestamps();

CREATE VIEW public."ca_leaderboard" AS  SELECT rsn,
    ca_points,
    ca_tier AS tier
   FROM member_stats
  WHERE ca_points IS NOT NULL
  ORDER BY ca_points DESC, updated_at
 LIMIT 20;

CREATE VIEW public."cl_leaderboard" AS  SELECT rsn,
    cl_obtained,
    cl_total
   FROM member_stats
  WHERE cl_obtained IS NOT NULL
  ORDER BY cl_obtained DESC, updated_at
 LIMIT 20;

CREATE VIEW public."gp_week_top" AS  SELECT rsn,
    sum(ge_value) AS gp
   FROM drops
  WHERE created_at > (now() - '7 days'::interval)
  GROUP BY rsn
  ORDER BY (sum(ge_value)) DESC
 LIMIT 3;

CREATE VIEW public."gp_week_total" AS  SELECT COALESCE(sum(ge_value), 0::bigint) AS total_gp,
    count(*)::integer AS drop_count
   FROM drops
  WHERE created_at > (now() - '7 days'::interval);

CREATE VIEW public."pb_leaderboard" AS  SELECT rsn,
    boss_key,
    seconds,
    achieved_at,
    source,
    rank
   FROM ( SELECT pb.rsn,
            pb.boss_key,
            pb.seconds,
            pb.achieved_at,
            pb.source,
            row_number() OVER (PARTITION BY pb.boss_key ORDER BY pb.seconds, pb.achieved_at) AS rank
           FROM personal_bests pb) ranked
  WHERE rank <= 3;

CREATE VIEW public."recent_clan_bests" AS  SELECT rsn,
    boss_key,
    seconds,
    achieved_at
   FROM ( SELECT pb.rsn,
            pb.boss_key,
            pb.seconds,
            pb.achieved_at,
            pb.source,
            row_number() OVER (PARTITION BY pb.boss_key ORDER BY pb.seconds, pb.achieved_at) AS rank
           FROM personal_bests pb) ranked
  WHERE rank = 1 AND source = 'live'::text
  ORDER BY achieved_at DESC
 LIMIT 5;

ALTER TABLE public."announcements" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."drops" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."lfg_applicants" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."lfg_entries" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."lfg_formed_parties" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."lfg_parties" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."member_stats" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."notable_items" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."personal_bests" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."welcome_message" ENABLE ROW LEVEL SECURITY;

ALTER TABLE public."wom_cache" ENABLE ROW LEVEL SECURITY;

CREATE POLICY "announcements anon read" ON public."announcements" FOR SELECT TO "anon" USING (true);

CREATE POLICY "Allow anon insert" ON public."drops" FOR INSERT TO "anon" WITH CHECK (true);

CREATE POLICY "Allow anon select" ON public."drops" FOR SELECT TO "anon" USING (true);

CREATE POLICY "anon full access lfg_applicants" ON public."lfg_applicants" FOR ALL TO "anon" USING (true) WITH CHECK (true);

CREATE POLICY "Allow anon delete" ON public."lfg_entries" FOR DELETE TO "anon" USING (true);

CREATE POLICY "Allow anon insert" ON public."lfg_entries" FOR INSERT TO "anon" WITH CHECK (true);

CREATE POLICY "Allow anon select" ON public."lfg_entries" FOR SELECT TO "anon" USING (true);

CREATE POLICY "Allow anon update" ON public."lfg_entries" FOR UPDATE TO "anon" USING (true) WITH CHECK (true);

CREATE POLICY "anon delete lfg_formed_parties" ON public."lfg_formed_parties" FOR DELETE TO "anon" USING (true);

CREATE POLICY "anon insert lfg_formed_parties" ON public."lfg_formed_parties" FOR INSERT TO "anon" WITH CHECK (true);

CREATE POLICY "anon read lfg_formed_parties" ON public."lfg_formed_parties" FOR SELECT TO "anon" USING (true);

CREATE POLICY "anon full access lfg_parties" ON public."lfg_parties" FOR ALL TO "anon" USING (true) WITH CHECK (true);

CREATE POLICY "notable_items anon read" ON public."notable_items" FOR SELECT TO "anon" USING (true);

CREATE POLICY "welcome_message anon read" ON public."welcome_message" FOR SELECT TO "anon" USING (true);

CREATE POLICY "wom_cache anon read" ON public."wom_cache" FOR SELECT TO "anon" USING (true);

GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO anon,authenticated;
REVOKE ALL ON public.personal_bests,public.member_stats FROM anon;

-- Final Boss protocol 3. Run against the exported v2 schema, in a TEST project first.
-- This transaction also cuts over writes: old v2 clients must be upgraded.
SELECT pg_advisory_xact_lock(1055, 3);
CREATE SCHEMA IF NOT EXISTS fb_private;
REVOKE ALL ON SCHEMA fb_private FROM PUBLIC, anon, authenticated;
CREATE TABLE IF NOT EXISTS fb_private.migrations(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM fb_private.migrations WHERE version=3) THEN
  RAISE EXCEPTION 'Protocol 3 already installed. Do not rerun a migration.';
 END IF;
END $$;
CREATE FUNCTION public.fb_name(n text) RETURNS text LANGUAGE sql IMMUTABLE STRICT
 SET search_path=pg_catalog AS $$ SELECT lower(btrim(replace(n, chr(160), ' '))) $$;
CREATE FUNCTION fb_private.check_name(n text) RETURNS text LANGUAGE plpgsql IMMUTABLE
 SET search_path=pg_catalog AS $$
DECLARE v text := public.fb_name(n);
BEGIN
 IF v IS NULL OR v !~ '^[a-z0-9 _-]{1,12}$' THEN RAISE EXCEPTION 'Invalid player name' USING ERRCODE='22023'; END IF;
 RETURN v;
END $$;
-- Preserve complete originals before deterministic PB/stat normalization.
CREATE TABLE fb_private.legacy_personal_bests AS TABLE public.personal_bests;
CREATE TABLE fb_private.legacy_member_stats AS TABLE public.member_stats;
CREATE TABLE fb_private.legacy_formed_parties AS TABLE public.lfg_formed_parties;
DO $$ BEGIN
 IF EXISTS(SELECT public.fb_name(host_rsn) FROM public.lfg_parties GROUP BY 1 HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Case-equivalent hosts exist. Resolve active LFG duplicates before migrating.';
 END IF;
 IF EXISTS(SELECT 1 FROM public.lfg_parties p JOIN public.lfg_applicants a ON public.fb_name(p.host_rsn)=public.fb_name(a.rsn)) THEN
  RAISE EXCEPTION 'A host is also an applicant. Resolve that active LFG membership before migrating.';
 END IF;
END $$;
DELETE FROM public.personal_bests;
INSERT INTO public.personal_bests(rsn,boss_key,seconds,achieved_at,source)
 SELECT DISTINCT ON(public.fb_name(rsn),lower(btrim(boss_key))) public.fb_name(rsn),lower(btrim(boss_key)),seconds,achieved_at,source
 FROM fb_private.legacy_personal_bests ORDER BY public.fb_name(rsn),lower(btrim(boss_key)),seconds,achieved_at,rsn;
DELETE FROM public.member_stats;
INSERT INTO public.member_stats(rsn,cl_obtained,cl_total,ca_points,ca_tier,updated_at)
 SELECT k.rsn,cl.cl_obtained,cl.cl_total,ca.ca_points,ca.ca_tier,greatest(cl.updated_at,ca.updated_at)
 FROM (SELECT DISTINCT public.fb_name(rsn) rsn FROM fb_private.legacy_member_stats) k
 LEFT JOIN LATERAL(SELECT * FROM fb_private.legacy_member_stats s WHERE public.fb_name(s.rsn)=k.rsn ORDER BY cl_obtained DESC NULLS LAST,updated_at DESC,rsn LIMIT 1) cl ON true
 LEFT JOIN LATERAL(SELECT * FROM fb_private.legacy_member_stats s WHERE public.fb_name(s.rsn)=k.rsn ORDER BY ca_points DESC NULLS LAST,updated_at DESC,rsn LIMIT 1) ca ON true;
-- Keep duplicate legacy snapshots, but only one retains the source-party key.
UPDATE public.lfg_formed_parties f SET party_id=NULL FROM (
 SELECT id,row_number() OVER(PARTITION BY party_id ORDER BY formed_at,id) n
 FROM public.lfg_formed_parties WHERE party_id IS NOT NULL
) d WHERE d.id=f.id AND d.n>1;
CREATE UNIQUE INDEX lfg_formed_source_unique ON public.lfg_formed_parties(party_id) WHERE party_id IS NOT NULL;
CREATE UNIQUE INDEX lfg_applicant_normalized ON public.lfg_applicants(public.fb_name(rsn));
CREATE UNIQUE INDEX lfg_host_normalized ON public.lfg_parties(public.fb_name(host_rsn));
ALTER TABLE public.lfg_parties ADD COLUMN version bigint NOT NULL DEFAULT 1;
DROP VIEW public.gp_week_top;
DROP VIEW public.gp_week_total;
ALTER TABLE public.drops ALTER COLUMN ge_value TYPE bigint;
ALTER TABLE public.drops ADD COLUMN event_id uuid, ADD COLUMN world_type text NOT NULL DEFAULT 'legacy', ADD COLUMN occurred_at timestamptz;
CREATE UNIQUE INDEX drops_event_unique ON public.drops(event_id) WHERE event_id IS NOT NULL;
CREATE INDEX drops_occurred_at_idx ON public.drops(coalesce(occurred_at,created_at));
CREATE INDEX drops_created_at_idx ON public.drops(created_at DESC,id);
CREATE INDEX lfg_updated_at_idx ON public.lfg_parties(updated_at);
CREATE VIEW public.gp_week_total AS SELECT coalesce(sum(ge_value),0)::numeric total_gp,count(*)::bigint drop_count FROM public.drops
 WHERE coalesce(occurred_at,created_at)>now()-interval '7 days' AND world_type IN('standard','legacy');
CREATE VIEW public.gp_week_top AS SELECT public.fb_name(rsn) rsn,sum(ge_value)::numeric gp FROM public.drops
 WHERE coalesce(occurred_at,created_at)>now()-interval '7 days' AND world_type IN('standard','legacy') GROUP BY 1 ORDER BY gp DESC,rsn LIMIT 3;
CREATE VIEW public.member_badges AS SELECT rsn,ca_tier tier FROM public.member_stats WHERE ca_tier IN('Elite','Master','Grandmaster');
CREATE TABLE fb_private.operations(id uuid PRIMARY KEY,actor text NOT NULL,request jsonb NOT NULL,result jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());

-- All LFG transitions share one short transaction lock. No external work occurs inside it.
CREATE FUNCTION fb_private.roles(activity text,hard boolean,capacity integer,required text) RETURNS text[] LANGUAGE plpgsql
 SET search_path=pg_catalog AS $$
DECLARE slots text[]; prefix text; r text; melee integer; freezes integer;
BEGIN
 IF activity IS NULL OR activity NOT IN('COX','TOB','TOA','KREEARRA','GRAARDOR','KRIL','ZILYANA','NEX','NIGHTMARE','CORP','DKS','HUEYCOATL','YAMA','ROYAL_TITANS','BA','ZALCANO','VOLCANIC_MINE','CASTLE_WARS','GOTR','WINTERTODT','GROUP_BOSS','MINIGAME','PVP','SKILLING','CHILLING') THEN RAISE EXCEPTION 'Unknown activity' USING ERRCODE='22023'; END IF;
 IF capacity IS NULL OR capacity<2 OR capacity>(CASE activity WHEN 'TOB' THEN 5 WHEN 'TOA' THEN 8 WHEN 'NEX' THEN 40 WHEN 'NIGHTMARE' THEN 80 WHEN 'CORP' THEN 30 WHEN 'HUEYCOATL' THEN 10 WHEN 'YAMA' THEN 2 WHEN 'ROYAL_TITANS' THEN 2 WHEN 'BA' THEN 5 WHEN 'KREEARRA' THEN 8 WHEN 'GRAARDOR' THEN 8 WHEN 'KRIL' THEN 8 WHEN 'ZILYANA' THEN 8 WHEN 'CASTLE_WARS' THEN 50 WHEN 'ZALCANO' THEN 30 WHEN 'VOLCANIC_MINE' THEN 30 WHEN 'GOTR' THEN 30 WHEN 'WINTERTODT' THEN 30 ELSE 100 END) THEN RAISE EXCEPTION 'Invalid capacity' USING ERRCODE='22023'; END IF;
 IF hard AND activity NOT IN('COX','TOB') THEN RAISE EXCEPTION 'Invalid hard mode' USING ERRCODE='22023'; END IF;
 IF activity='BA' THEN
  IF capacity<>5 THEN RAISE EXCEPTION 'BA requires five seats' USING ERRCODE='22023'; END IF;
  RETURN ARRAY['BA_ATTACKER','BA_DEFENDER','BA_COLLECTOR','BA_HEALER','BA_FILL'];
 ELSIF activity='TOB' THEN
  prefix:=CASE WHEN hard THEN 'TOB_HM_' ELSE 'TOB_' END;
  melee:=CASE WHEN capacity=5 THEN 2 ELSE 1 END;
  slots:=array_fill(prefix||'MELEE',ARRAY[melee]);
  IF capacity>=3 THEN slots:=array_append(slots,prefix||'RANGED'); END IF;
  freezes:=capacity-cardinality(slots);
  IF freezes=1 THEN slots:=array_append(slots,prefix||'FRZ');
  ELSE slots:=slots||ARRAY[prefix||'NFRZ',prefix||'SFRZ']; END IF;
  RETURN slots;
 ELSIF activity='COX' THEN
  slots:=string_to_array(required,',');
  IF slots IS NULL OR cardinality(slots)<>capacity THEN RAISE EXCEPTION 'Role counts must equal capacity' USING ERRCODE='22023'; END IF;
  FOREACH r IN ARRAY slots LOOP
   IF (NOT hard AND r NOT IN('COX_MELEE','COX_MAGE','COX_RUNNER','COX_FILL')) OR (hard AND r NOT IN('COX_CM_VENG','COX_CM_ANCIENT','COX_CM_NORMAL','COX_CM_FILL')) THEN RAISE EXCEPTION 'Invalid CoX role' USING ERRCODE='22023'; END IF;
  END LOOP;
  RETURN slots;
 END IF;
 RETURN ARRAY[]::text[];
END $$;
CREATE FUNCTION fb_private.seat(slots text[], wanted text) RETURNS integer LANGUAGE plpgsql IMMUTABLE
 SET search_path=pg_catalog AS $$
DECLARE i integer; prefix text;
BEGIN
 IF cardinality(slots)=0 OR wanted IS NULL OR wanted NOT IN ('TOB_MELEE','TOB_RANGED','TOB_FRZ','TOB_NFRZ','TOB_SFRZ','TOB_FILL','TOB_HM_MELEE','TOB_HM_RANGED','TOB_HM_FRZ','TOB_HM_NFRZ','TOB_HM_SFRZ','TOB_HM_FILL','COX_MELEE','COX_MAGE','COX_RUNNER','COX_FILL','COX_CM_VENG','COX_CM_ANCIENT','COX_CM_NORMAL','COX_CM_FILL','BA_ATTACKER','BA_DEFENDER','BA_COLLECTOR','BA_HEALER','BA_FILL') THEN RETURN 0; END IF;
 i:=array_position(slots,wanted); IF i IS NOT NULL THEN RETURN i; END IF;
 prefix:=CASE WHEN wanted LIKE 'TOB_HM_%' THEN 'TOB_HM_' WHEN wanted LIKE 'TOB_%' THEN 'TOB_' WHEN wanted LIKE 'COX_CM_%' THEN 'COX_CM_' WHEN wanted LIKE 'COX_%' THEN 'COX_' WHEN wanted LIKE 'BA_%' THEN 'BA_' ELSE NULL END;
 IF prefix IS NULL THEN RETURN 0; END IF;
 FOR i IN 1..cardinality(slots) LOOP
  IF wanted=prefix||'FILL' AND left(slots[i],length(prefix))=prefix AND (prefix<>'TOB_' OR slots[i] NOT LIKE 'TOB_HM_%') AND (prefix<>'COX_' OR slots[i] NOT LIKE 'COX_CM_%') THEN RETURN i; END IF;
  IF slots[i]=prefix||'FILL' THEN RETURN i; END IF;
  IF prefix IN('TOB_','TOB_HM_') AND wanted=prefix||'FRZ' AND slots[i] IN(prefix||'NFRZ',prefix||'SFRZ') THEN RETURN i; END IF;
 END LOOP;
 RETURN 0;
END $$;
CREATE FUNCTION fb_private.available(p public.lfg_parties) RETURNS text[] LANGUAGE plpgsql STABLE
 SET search_path=pg_catalog AS $$
DECLARE slots text[]:=fb_private.roles(p.activity,p.hard_mode,p.capacity,p.required_roles); a record; i integer;
BEGIN
 IF p.activity<>'TOA' AND p.invocation<>0 THEN RAISE EXCEPTION 'Invocation is only valid for ToA' USING ERRCODE='22023'; END IF;
 IF cardinality(slots)=0 THEN RETURN slots; END IF;
 i:=fb_private.seat(slots,p.host_role);
 IF i=0 THEN RAISE EXCEPTION 'Host role does not fit composition' USING ERRCODE='22023'; END IF;
 slots:=slots[1:i-1]||slots[i+1:cardinality(slots)];
 FOR a IN SELECT role FROM public.lfg_applicants WHERE party_id=p.id AND status='ACCEPTED' ORDER BY created_at,rsn LOOP
  i:=fb_private.seat(slots,a.role);
  IF i=0 THEN RAISE EXCEPTION 'Accepted role does not fit composition' USING ERRCODE='22023'; END IF;
  slots:=slots[1:i-1]||slots[i+1:cardinality(slots)];
 END LOOP;
 RETURN slots;
END $$;
CREATE FUNCTION public.fb_lfg(p_action text,p_actor text,p_data jsonb,p_operation uuid,p_expected bigint DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE actor text; target text; pid uuid; p public.lfg_parties; candidate public.lfg_parties;
 request jsonb; oldop fb_private.operations; result jsonb; slots text[]; wanted text; assigned text; seat integer; members jsonb; formed_id uuid; count_members integer;
BEGIN
 actor:=fb_private.check_name(p_actor);
 IF p_operation IS NULL OR p_data IS NULL OR jsonb_typeof(p_data)<>'object' THEN RAISE EXCEPTION 'Invalid command' USING ERRCODE='22023'; END IF;
 request:=jsonb_build_object('action',p_action,'data',p_data,'expected',p_expected);
 PERFORM pg_advisory_xact_lock(1055,3);
 SELECT * INTO oldop FROM fb_private.operations WHERE id=p_operation;
 IF FOUND THEN
  IF oldop.actor<>actor OR oldop.request<>request THEN RETURN jsonb_build_object('status','conflict','message','Operation ID reused with different input'); END IF;
  RETURN oldop.result;
 END IF;
 DELETE FROM public.lfg_parties WHERE updated_at<now()-interval '30 minutes';
 -- A subtransaction rolls back a failed switch/create, including withdrawals.
 BEGIN
  pid:=nullif(p_data->>'id','')::uuid;
  IF p_action='create' THEN
   IF EXISTS(SELECT 1 FROM public.lfg_parties WHERE public.fb_name(host_rsn)=actor) THEN RAISE EXCEPTION 'Already hosting' USING ERRCODE='23505'; END IF;
   IF EXISTS(SELECT 1 FROM public.lfg_applicants WHERE public.fb_name(rsn)=actor AND status='ACCEPTED') THEN RAISE EXCEPTION 'Leave your accepted party before hosting' USING ERRCODE='23514'; END IF;
   candidate:=jsonb_populate_record(NULL::public.lfg_parties,p_data);
   candidate.id:=coalesce(pid,gen_random_uuid());candidate.host_rsn:=actor;candidate.version:=1;
   candidate.hard_mode:=coalesce(candidate.hard_mode,false);candidate.invocation:=coalesce(candidate.invocation,0);
   candidate.min_kc:=coalesce(candidate.min_kc,0);candidate.loot_rule:=coalesce(candidate.loot_rule,'UNSPECIFIED');
   candidate.learner:=coalesce(candidate.learner,false);candidate.teacher:=coalesce(candidate.teacher,false);
   candidate.created_at:=now();candidate.updated_at:=now();candidate.ttl_minutes:=30;
   PERFORM fb_private.available(candidate);
   DELETE FROM public.lfg_applicants WHERE public.fb_name(rsn)=actor;
   INSERT INTO public.lfg_parties SELECT candidate.* RETURNING * INTO p;
   pid:=p.id;
  ELSIF p_action='remove_formed' THEN
   DELETE FROM public.lfg_formed_parties WHERE id=pid AND public.fb_name(host_rsn)=actor;
  ELSE
   SELECT * INTO p FROM public.lfg_parties WHERE id=pid FOR UPDATE;
   IF NOT FOUND THEN RAISE EXCEPTION 'Party no longer exists' USING ERRCODE='P0002'; END IF;
   IF p_expected IS NOT NULL AND p.version<>p_expected THEN RAISE EXCEPTION 'Party changed; refresh and try again' USING ERRCODE='40001'; END IF;
   target:=CASE WHEN p_action IN('accept','decline','kick','add') THEN fb_private.check_name(p_data->>'rsn') ELSE actor END;
   IF p_action IN('edit','disband','heartbeat','accept','decline','kick','add') AND public.fb_name(p.host_rsn)<>actor THEN RAISE EXCEPTION 'Only the host can perform this action' USING ERRCODE='42501'; END IF;
   IF p_action='edit' THEN
    candidate:=jsonb_populate_record(p,p_data-'id'-'host_rsn'-'version'-'created_at'-'updated_at'-'ttl_minutes');
    IF EXISTS(SELECT 1 FROM public.lfg_applicants WHERE party_id=pid AND status='ACCEPTED') AND ROW(candidate.activity,candidate.hard_mode,candidate.invocation,candidate.capacity,candidate.required_roles,candidate.host_role) IS DISTINCT FROM ROW(p.activity,p.hard_mode,p.invocation,p.capacity,p.required_roles,p.host_role) THEN RAISE EXCEPTION 'Cannot change composition with accepted members' USING ERRCODE='23514'; END IF;
    PERFORM fb_private.available(candidate);
    UPDATE public.lfg_parties SET activity=candidate.activity,hard_mode=candidate.hard_mode,invocation=candidate.invocation,capacity=candidate.capacity,description=candidate.description,world=candidate.world,min_kc=candidate.min_kc,loot_rule=candidate.loot_rule,required_roles=candidate.required_roles,host_role=candidate.host_role,learner=candidate.learner,teacher=candidate.teacher WHERE id=pid;
   ELSIF p_action='heartbeat' THEN NULL;
   ELSIF p_action='disband' THEN DELETE FROM public.lfg_parties WHERE id=pid;
   ELSIF p_action IN('leave','kick') THEN DELETE FROM public.lfg_applicants WHERE party_id=pid AND public.fb_name(rsn)=target;
   ELSIF p_action='decline' THEN
    UPDATE public.lfg_applicants SET status='DECLINED',updated_at=now() WHERE party_id=pid AND public.fb_name(rsn)=target AND status='PENDING';
    IF NOT FOUND THEN RAISE EXCEPTION 'Application changed or removed' USING ERRCODE='40001'; END IF;
   ELSIF p_action IN('apply','add','accept') THEN
    IF target=public.fb_name(p.host_rsn) OR EXISTS(SELECT 1 FROM public.lfg_parties WHERE public.fb_name(host_rsn)=target) THEN RAISE EXCEPTION 'Host cannot also apply' USING ERRCODE='23514'; END IF;
    IF 1+(SELECT count(*) FROM public.lfg_applicants WHERE party_id=pid AND status='ACCEPTED')>=p.capacity THEN RAISE EXCEPTION 'Party is full' USING ERRCODE='23514'; END IF;
    IF p_action='accept' THEN
     SELECT role INTO wanted FROM public.lfg_applicants WHERE party_id=pid AND public.fb_name(rsn)=target AND status='PENDING';
     IF NOT FOUND THEN RAISE EXCEPTION 'Application changed or removed' USING ERRCODE='40001'; END IF;
    ELSE wanted:=p_data->>'role'; END IF;
    slots:=fb_private.available(p);
    IF p.activity IN('COX','TOB','BA') THEN
     seat:=fb_private.seat(slots,wanted);
     IF seat=0 THEN RAISE EXCEPTION 'Requested role is no longer available' USING ERRCODE='23514'; END IF;
     assigned:=CASE WHEN slots[seat] LIKE '%_FILL' THEN wanted ELSE slots[seat] END;
    ELSE assigned:=NULL; END IF;
    IF p_action='apply' THEN
     IF EXISTS(SELECT 1 FROM public.lfg_applicants WHERE public.fb_name(rsn)=target AND status='ACCEPTED') THEN RAISE EXCEPTION 'Leave your accepted party before applying' USING ERRCODE='23514'; END IF;
     DELETE FROM public.lfg_applicants WHERE public.fb_name(rsn)=target;
     INSERT INTO public.lfg_applicants(party_id,rsn,role,learner,status,kc,kc_source) VALUES(pid,target,wanted,coalesce((p_data->>'learner')::boolean,false),'PENDING',(p_data->>'kc')::integer,p_data->>'kc_source');
    ELSIF p_action='add' THEN
     INSERT INTO public.lfg_applicants(party_id,rsn,role,learner,status,added_by_host) VALUES(pid,target,assigned,false,'ACCEPTED',true);
    ELSE UPDATE public.lfg_applicants SET status='ACCEPTED',role=assigned,updated_at=now() WHERE party_id=pid AND public.fb_name(rsn)=target; END IF;
   ELSE RAISE EXCEPTION 'Unknown action' USING ERRCODE='22023'; END IF;
   -- Applicant polling must not keep an abandoned host advertisement alive.
   UPDATE public.lfg_parties SET version=version+CASE WHEN p_action='heartbeat' THEN 0 ELSE 1 END,
    updated_at=CASE WHEN public.fb_name(host_rsn)=actor THEN now() ELSE updated_at END WHERE id=pid RETURNING * INTO p;
  END IF;
  IF p.id IS NOT NULL AND EXISTS(SELECT 1 FROM public.lfg_parties WHERE id=p.id) THEN
   SELECT 1+count(*) INTO count_members FROM public.lfg_applicants WHERE party_id=p.id AND status='ACCEPTED';
   IF count_members=p.capacity THEN
    PERFORM fb_private.available(p);
    members:=jsonb_build_array(jsonb_build_object('rsn',p.host_rsn,'role',p.host_role,'added_by_host',false))||coalesce((SELECT jsonb_agg(jsonb_build_object('rsn',rsn,'role',role,'added_by_host',added_by_host) ORDER BY created_at,rsn) FROM public.lfg_applicants WHERE party_id=p.id AND status='ACCEPTED'),'[]'::jsonb);
    INSERT INTO public.lfg_formed_parties(party_id,host_rsn,activity,hard_mode,invocation,capacity,world,members) VALUES(p.id,p.host_rsn,p.activity,p.hard_mode,p.invocation,p.capacity,p.world,members) RETURNING id INTO formed_id;
    DELETE FROM public.lfg_parties WHERE id=p.id;
   END IF;
  END IF;
  result:=jsonb_build_object('status','ok','id',pid,'version',p.version,'formed_id',formed_id);
 EXCEPTION WHEN integrity_constraint_violation OR invalid_parameter_value OR invalid_text_representation OR numeric_value_out_of_range OR insufficient_privilege OR no_data_found OR serialization_failure THEN
  result:=jsonb_build_object('status',CASE WHEN SQLSTATE='40001' THEN 'conflict' WHEN SQLSTATE='P0002' THEN 'not_found' ELSE 'invalid' END,'message',SQLERRM);
 END;
 INSERT INTO fb_private.operations VALUES(p_operation,actor,request,result,now());
 RETURN result;
END $$;
CREATE FUNCTION public.fb_board() RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT jsonb_build_object('protocol',3,'parties',coalesce((SELECT jsonb_agg(to_jsonb(p)||jsonb_build_object('lfg_applicants',coalesce((SELECT jsonb_agg(a ORDER BY created_at,rsn) FROM public.lfg_applicants a WHERE a.party_id=p.id),'[]'::jsonb)) ORDER BY p.created_at DESC,p.id) FROM public.lfg_parties p WHERE p.updated_at>=now()-interval '30 minutes'),'[]'::jsonb),
 'formed',coalesce((SELECT jsonb_agg(f ORDER BY formed_at DESC,id) FROM public.lfg_formed_parties f WHERE formed_at>now()-interval '7 days'),'[]'::jsonb))
$$;
CREATE FUNCTION public.fb_cleanup() RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(1055,3);
 DELETE FROM public.lfg_parties WHERE updated_at<now()-interval '30 minutes';
 DELETE FROM public.lfg_formed_parties WHERE formed_at<now()-interval '7 days';
 -- Keep receipts: deleting them permits ancient requests to execute again.
END $$;

CREATE FUNCTION public.fb_submit_pbs(p_rsn text,p_entries jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE actor text:=fb_private.check_name(p_rsn); e jsonb; boss text; seconds numeric; src text; accepted integer:=0; rejected integer:=0;
BEGIN
 IF jsonb_typeof(p_entries) IS DISTINCT FROM 'array' OR jsonb_array_length(p_entries)>300 THEN RAISE EXCEPTION 'Invalid batch' USING ERRCODE='22023'; END IF;
 FOR e IN SELECT * FROM jsonb_array_elements(p_entries) LOOP
  BEGIN
   boss:=lower(btrim(e->>'boss_key'));src:=e->>'source';
   IF jsonb_typeof(e)<>'object' OR boss IS NULL OR length(boss) NOT BETWEEN 1 AND 100 OR boss ~ '[[:cntrl:]<>]' OR (e->>'seconds') IS NULL OR (e->>'seconds') !~ '^[0-9]+([.][0-9]+)?$' OR src IS NULL OR src NOT IN('live','seed') THEN RAISE EXCEPTION 'Invalid entry' USING ERRCODE='22023'; END IF;
   seconds:=(e->>'seconds')::numeric;
   IF seconds<0.01 OR seconds>=86400 THEN RAISE EXCEPTION 'Invalid time' USING ERRCODE='22023'; END IF;
   INSERT INTO public.personal_bests(rsn,boss_key,seconds,source) VALUES(actor,boss,seconds,src)
    ON CONFLICT(rsn,boss_key) DO UPDATE SET seconds=excluded.seconds,achieved_at=now(),source=excluded.source WHERE public.personal_bests.seconds>excluded.seconds;
   accepted:=accepted+1;
  EXCEPTION WHEN invalid_parameter_value OR invalid_text_representation OR numeric_value_out_of_range OR check_violation THEN rejected:=rejected+1; END;
 END LOOP;
 RETURN jsonb_build_object('status','ok','accepted',accepted,'rejected',rejected);
END $$;
CREATE FUNCTION public.fb_submit_stats(p_rsn text,p_cl_obtained integer DEFAULT NULL,p_cl_total integer DEFAULT NULL,p_ca_points integer DEFAULT NULL,p_ca_tier text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE actor text:=fb_private.check_name(p_rsn);
BEGIN
 IF (p_cl_obtained IS NULL)<>(p_cl_total IS NULL) OR (p_cl_obtained IS NOT NULL AND (p_cl_obtained<=0 OR p_cl_obtained>p_cl_total OR p_cl_total>10000)) OR (p_ca_points IS NOT NULL AND (p_ca_points<=0 OR p_ca_points>10000)) OR (p_ca_tier IS NOT NULL AND (p_ca_points IS NULL OR p_ca_tier NOT IN('Easy','Medium','Hard','Elite','Master','Grandmaster'))) THEN RAISE EXCEPTION 'Invalid statistics' USING ERRCODE='22023'; END IF;
 INSERT INTO public.member_stats AS s(rsn,cl_obtained,cl_total,ca_points,ca_tier) VALUES(actor,p_cl_obtained,p_cl_total,p_ca_points,p_ca_tier)
 ON CONFLICT(rsn) DO UPDATE SET
 cl_obtained=greatest(s.cl_obtained,excluded.cl_obtained),
 cl_total=CASE WHEN excluded.cl_obtained>=coalesce(s.cl_obtained,0) THEN excluded.cl_total ELSE s.cl_total END,
 ca_points=greatest(s.ca_points,excluded.ca_points),
 ca_tier=CASE WHEN excluded.ca_points>=coalesce(s.ca_points,0) THEN excluded.ca_tier ELSE s.ca_tier END,updated_at=now();
 RETURN jsonb_build_object('status','ok');
END $$;
CREATE FUNCTION public.fb_submit_drop(p_row jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE d public.drops; existing public.drops;
BEGIN
 d:=jsonb_populate_record(NULL::public.drops,p_row);d.rsn:=fb_private.check_name(d.rsn);
 IF d.event_id IS NULL OR d.npc_name IS NULL OR length(d.npc_name) NOT BETWEEN 1 AND 100 OR d.item_name IS NULL OR length(d.item_name) NOT BETWEEN 1 AND 150 OR d.item_id IS NULL OR d.item_id<0 OR d.quantity IS NULL OR d.quantity<=0 OR d.ge_value IS NULL OR d.ge_value<0 OR d.world_type IS NULL OR d.world_type NOT IN('standard','special') OR d.occurred_at IS NULL THEN RAISE EXCEPTION 'Invalid drop' USING ERRCODE='22023'; END IF;
 d.occurred_at:=least(d.occurred_at,now());
 INSERT INTO public.drops(rsn,npc_name,item_name,item_id,ge_value,quantity,rarity,event_id,world_type,occurred_at)
 VALUES(d.rsn,d.npc_name,d.item_name,d.item_id,d.ge_value,d.quantity,d.rarity,d.event_id,d.world_type,d.occurred_at) ON CONFLICT(event_id) WHERE event_id IS NOT NULL DO NOTHING;
 SELECT * INTO existing FROM public.drops WHERE event_id=d.event_id;
 IF ROW(existing.rsn,existing.npc_name,existing.item_name,existing.item_id,existing.quantity,existing.ge_value,existing.world_type) IS DISTINCT FROM ROW(d.rsn,d.npc_name,d.item_name,d.item_id,d.quantity,d.ge_value,d.world_type) THEN RETURN jsonb_build_object('status','conflict','message','Event ID reused'); END IF;
 RETURN jsonb_build_object('status','ok','id',existing.id);
END $$;
CREATE FUNCTION public.fb_attach_screenshot(p_event uuid,p_path text) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF p_path IS NULL OR p_path !~ '^[0-9a-f-]{36}[.]png$' THEN RAISE EXCEPTION 'Invalid screenshot path' USING ERRCODE='22023'; END IF;
 UPDATE public.drops SET screenshot_url=p_path WHERE event_id=p_event AND screenshot_url IS NULL;
 IF NOT FOUND THEN
  IF EXISTS(SELECT 1 FROM public.drops WHERE event_id=p_event AND screenshot_url=p_path) THEN RETURN jsonb_build_object('status','ok'); END IF;
  RETURN jsonb_build_object('status','not_found');
 END IF;
 RETURN jsonb_build_object('status','ok');
END $$;
-- Revoke table mutation bypasses and legacy RPC entry points. Read projections stay available.
REVOKE ALL ON public.lfg_parties,public.lfg_applicants,public.lfg_formed_parties,public.personal_bests,public.member_stats,public.drops FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.lfg_parties,public.lfg_applicants,public.lfg_formed_parties,public.drops TO anon;
REVOKE ALL ON public.lfg_entries FROM PUBLIC,anon,authenticated;
REVOKE ALL ON public.announcements,public.notable_items,public.welcome_message,public.wom_cache FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.announcements,public.notable_items,public.welcome_message,public.wom_cache TO anon;
REVOKE ALL ON public.ca_leaderboard,public.cl_leaderboard,public.pb_leaderboard,public.recent_clan_bests,public.gp_week_top,public.gp_week_total,public.member_badges FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.ca_leaderboard,public.cl_leaderboard,public.pb_leaderboard,public.recent_clan_bests,public.gp_week_top,public.gp_week_total,public.member_badges TO anon;
REVOKE EXECUTE ON FUNCTION public.submit_pbs(text,jsonb),public.submit_stats(text,integer,integer,integer,text) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA fb_private FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA fb_private FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.fb_cleanup() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.fb_lfg(text,text,jsonb,uuid,bigint),public.fb_board(),public.fb_submit_pbs(text,jsonb),public.fb_submit_stats(text,integer,integer,integer,text),public.fb_submit_drop(jsonb),public.fb_attach_screenshot(uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fb_lfg(text,text,jsonb,uuid,bigint),public.fb_board(),public.fb_submit_pbs(text,jsonb),public.fb_submit_stats(text,integer,integer,integer,text),public.fb_submit_drop(jsonb),public.fb_attach_screenshot(uuid,text) TO anon;
INSERT INTO fb_private.migrations(version) VALUES(3);
NOTIFY pgrst,'reload schema';
COMMIT;
