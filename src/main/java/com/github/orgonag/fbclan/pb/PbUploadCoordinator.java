package com.github.orgonag.fbclan.pb;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarbitID;

/**
 * Upload side of the PB feature: parses PB chat lines live (mirroring the
 * core Chat Commands plugin's detection) and seeds RuneLite's locally
 * stored PBs once per session. Non-standard worlds (Leagues, DMM,
 * speedrun...) never upload.
 */
public class PbUploadCoordinator
{
    private final Client client;
    private final FinalBossConfig config;
    private final ClanSession session;
    private final ScheduledExecutorService executor;
    private final PbTrackingService trackingService;
    private final PbSubmitService submitService;
    private final PbSeedService seedService;

    // Once-per-session latch for the PB seed upload. The coordinator is
    // freshly constructed each startUp (so this initializes false);
    // reset() exists as a defensive API for callers that reuse one.
    private volatile boolean seeded = false;

    public PbUploadCoordinator(Client client, FinalBossConfig config, ClanSession session,
        ScheduledExecutorService executor, PbSubmitService submitService, PbSeedService seedService)
    {
        this.client = client;
        this.config = config;
        this.session = session;
        this.executor = executor;
        this.submitService = submitService;
        this.seedService = seedService;
        this.trackingService = new PbTrackingService(this::tobTeamSize, this::toaTeamSize);
    }

    public void reset()
    {
        seeded = false;
    }

    public void onChatMessage(ChatMessage event)
    {
        ChatMessageType type = event.getType();
        // Core ChatCommands accepts these three for PB lines; TRADE is
        // irrelevant here.
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
            && type != ChatMessageType.FRIENDSCHATNOTIFICATION)
        {
            return;
        }
        String rsn = session.getRsn();
        if (!session.canUpload() || !config.enablePbUpload()
            || !PbTrackingService.isStandardWorld(client.getWorldType()))
        {
            return;
        }
        List<PbSubmission> subs = trackingService.onGameMessage(event.getMessage(), client.getTickCount());
        if (!subs.isEmpty())
        {
            executor.submit(() -> submitService.submit(rsn, subs));
        }
    }

    public void maybeSeed()
    {
        String rsn = session.getRsn();
        if (!session.canUpload() || seeded || !config.enablePbUpload())
        {
            return;
        }
        if (!PbTrackingService.isStandardWorld(client.getWorldType()))
        {
            return;
        }
        seeded = true;
        executor.submit(() -> {
            List<PbSubmission> seeds = seedService.collectLocalPbs();
            submitService.submit(rsn, seeds);
        });
    }

    private int tobTeamSize()
    {
        return Math.min(client.getVarbitValue(VarbitID.TOB_CLIENT_P0), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOB_CLIENT_P1), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOB_CLIENT_P2), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOB_CLIENT_P3), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOB_CLIENT_P4), 1);
    }

    private int toaTeamSize()
    {
        return Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P0), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P1), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P2), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P3), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P4), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P5), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P6), 1) +
            Math.min(client.getVarbitValue(VarbitID.TOA_CLIENT_P7), 1);
    }
}
