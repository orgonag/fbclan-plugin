package com.github.orgonag.fbclan.pb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.WorldType;

/**
 * Pairs the two chat lines a boss kill produces — the kill-count line
 * (carries the boss name) and the duration line (carries the time) —
 * which arrive in either order, mirroring core ChatCommandsPlugin.
 * Emits PbSubmissions; the caller ships them to Supabase. State is only
 * touched from the client thread (chat events), no synchronization.
 */
@Slf4j
public class PbTrackingService
{
    // ToB/ToA team size is not in the chat message; core computes it from
    // varbits. Injected as suppliers so tests don't need a Client.
    private final IntSupplier tobTeamSize;
    private final IntSupplier toaTeamSize;

    private String lastBossKey;
    private int lastBossTick = -1;
    private double lastPbSeconds = -1;
    private boolean lastPbNew;
    private String lastPbTeamSize;

    public PbTrackingService(IntSupplier tobTeamSize, IntSupplier toaTeamSize)
    {
        this.tobTeamSize = tobTeamSize;
        this.toaTeamSize = toaTeamSize;
    }

    // Non-standard worlds (Leagues, DMM, speedruns...) must not feed the
    // clan board. Core does NOT guard this; we must.
    public static boolean isStandardWorld(EnumSet<WorldType> worldTypes)
    {
        return !worldTypes.contains(WorldType.SEASONAL)
            && !worldTypes.contains(WorldType.DEADMAN)
            && !worldTypes.contains(WorldType.TOURNAMENT_WORLD)
            && !worldTypes.contains(WorldType.BETA_WORLD)
            && !worldTypes.contains(WorldType.QUEST_SPEEDRUNNING)
            && !worldTypes.contains(WorldType.NOSAVE_MODE)
            && !worldTypes.contains(WorldType.FRESH_START_WORLD)
            && !worldTypes.contains(WorldType.PVP_ARENA);
    }

    public List<PbSubmission> onGameMessage(String message, int tick)
    {
        List<PbSubmission> result = process(message, tick);

        // Core invalidates the remembered KC at the end of every chat
        // message that arrives on a later tick — the KC/PB pair always
        // lands within one tick.
        if (lastBossKey != null && lastBossTick != tick)
        {
            lastBossKey = null;
            lastBossTick = -1;
        }
        return result;
    }

    private List<PbSubmission> process(String message, int tick)
    {
        Optional<String> kc = PbMessageParser.parseKillCount(message);
        if (kc.isPresent())
        {
            return onKillCount(kc.get(), tick);
        }

        List<PbSubmission> sepulchre = PbMessageParser.parseSepulchre(message);
        if (!sepulchre.isEmpty())
        {
            return sepulchre;
        }

        Optional<PbMessageParser.Duration> duration = PbMessageParser.parseDuration(message);
        if (duration.isPresent())
        {
            return onDuration(duration.get());
        }

        return Collections.emptyList();
    }

    private List<PbSubmission> onKillCount(String bossKey, int tick)
    {
        if (lastPbSeconds > -1)
        {
            // PB line arrived first (raids do this); attach it now.
            String teamSize = lastPbTeamSize;
            if (bossKey.contains("theatre of blood"))
            {
                int size = tobTeamSize.getAsInt();
                teamSize = size == 1 ? "Solo" : (size + " players");
            }
            else if (bossKey.contains("tombs of amascut"))
            {
                int size = toaTeamSize.getAsInt();
                teamSize = size == 1 ? "Solo" : (size + " players");
            }

            String source = lastPbNew ? "live" : "seed";
            List<PbSubmission> result = new ArrayList<>();
            // Overall key: the server's improve-only write replaces core's
            // "only if lower than existing overall" check.
            result.add(new PbSubmission(bossKey, lastPbSeconds, source));
            if (teamSize != null)
            {
                result.add(new PbSubmission(
                    bossKey + " " + teamSize.toLowerCase(java.util.Locale.ROOT),
                    lastPbSeconds, source));
            }

            lastPbSeconds = -1;
            lastPbTeamSize = null;
            return result;
        }

        lastBossKey = bossKey;
        lastBossTick = tick;
        return Collections.emptyList();
    }

    private List<PbSubmission> onDuration(PbMessageParser.Duration duration)
    {
        if (lastBossKey != null)
        {
            // KC line arrived first (most bosses). Core emits only the
            // unsuffixed key in this order; we mirror that.
            PbSubmission sub = new PbSubmission(lastBossKey, duration.getSeconds(),
                duration.isNewPb() ? "live" : "seed");
            lastPbSeconds = -1;
            lastPbTeamSize = null;
            return Collections.singletonList(sub);
        }

        lastPbSeconds = duration.getSeconds();
        lastPbNew = duration.isNewPb();
        lastPbTeamSize = duration.getTeamSize();
        return Collections.emptyList();
    }
}
