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
