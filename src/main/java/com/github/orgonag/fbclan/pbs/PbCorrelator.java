package com.github.orgonag.fbclan.pbs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Client-thread confined; both halves expire together and a match consumes both. */
public final class PbCorrelator
{
    private int tick = -1;
    private String boss;
    private PbParser.Duration duration;

    public void reset() { tick = -1; boss = null; duration = null; }

    public List<PbParser.Submission> accept(String message, int now, java.util.function.Function<String, String> team)
    {
        if (now != tick) { reset(); tick = now; }
        List<PbParser.Submission> standalone = PbParser.sepulchre(message);
        if (!standalone.isEmpty()) { reset(); return standalone; }
        java.util.Optional<String> kc = PbParser.killCount(message);
        if (kc.isPresent()) boss = kc.get();
        else
        {
            java.util.Optional<PbParser.Duration> parsed = PbParser.duration(message);
            if (parsed.isPresent()) duration = parsed.get();
        }
        if (boss == null || duration == null) return Collections.emptyList();
        List<PbParser.Submission> out = new ArrayList<>();
        double seconds = duration.getSeconds();
        if (Double.isFinite(seconds) && seconds > 0 && seconds < 86400)
        {
            String source = duration.isNewPb() ? "live" : "seed";
            out.add(new PbParser.Submission(boss, seconds, source));
            String size = team.apply(boss);
            if (size == null) size = duration.getTeamSize();
            if (size != null) out.add(new PbParser.Submission(boss + " " + size.toLowerCase(Locale.ROOT), seconds, source));
        }
        reset();
        return out;
    }
}
