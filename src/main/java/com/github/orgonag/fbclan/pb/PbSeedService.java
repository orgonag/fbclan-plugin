package com.github.orgonag.fbclan.pb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * One-time-per-session upload of the PBs RuneLite core (Chat Commands
 * plugin) has already stored locally for this account, so the clan board
 * is populated with pre-existing times — no waiting for fresh kills.
 * Everything goes through the improve-only RPC as source='seed', so a
 * stale local value can never overwrite a better recorded time.
 */
@Slf4j
public class PbSeedService
{
    private static final String PB_GROUP = "personalbest";

    private final ConfigManager configManager;

    public PbSeedService(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    // Reads the active RS-profile's stored PBs. Returns empty when the
    // member never ran Chat Commands or isn't logged in yet.
    public List<PbSubmission> collectLocalPbs()
    {
        String profile = configManager.getRSProfileKey();
        if (profile == null)
        {
            return new ArrayList<>();
        }
        Map<String, Double> stored = new LinkedHashMap<>();
        for (String key : configManager.getRSProfileConfigurationKeys(PB_GROUP, profile, ""))
        {
            Double seconds = configManager.getRSProfileConfiguration(PB_GROUP, key, double.class);
            stored.put(key, seconds);
        }
        List<PbSubmission> subs = buildSeedSubmissions(stored);
        log.debug("Collected {} local PB(s) to seed", subs.size());
        return subs;
    }

    static List<PbSubmission> buildSeedSubmissions(Map<String, Double> stored)
    {
        List<PbSubmission> result = new ArrayList<>();
        for (Map.Entry<String, Double> e : stored.entrySet())
        {
            String key = e.getKey();
            Double seconds = e.getValue();
            if (key == null || key.isEmpty() || seconds == null || seconds <= 0)
            {
                continue;
            }
            result.add(new PbSubmission(key, seconds, "seed"));
        }
        return result;
    }
}
