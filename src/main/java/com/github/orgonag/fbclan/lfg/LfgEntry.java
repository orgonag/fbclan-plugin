package com.github.orgonag.fbclan.lfg;

import lombok.Value;
import java.time.Instant;
import java.time.OffsetDateTime;

@Value
public class LfgEntry
{
    String rsn;
    LfgActivity activity;
    Instant updatedAt;

    public static LfgEntry fromJson(String rsn, String activityKey, String updatedAtStr)
    {
        LfgActivity activity = LfgActivity.fromKey(activityKey);
        if (activity == null)
        {
            return null;
        }
        Instant updatedAt;
        try
        {
            updatedAt = OffsetDateTime.parse(updatedAtStr).toInstant();
        }
        catch (Exception e)
        {
            return null;
        }
        return new LfgEntry(rsn, activity, updatedAt);
    }
}
