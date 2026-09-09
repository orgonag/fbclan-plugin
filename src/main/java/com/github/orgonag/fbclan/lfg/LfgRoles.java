package com.github.orgonag.fbclan.lfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure role-composition rules for the role-based activities. No client
 * or Swing types.
 *
 * <ul>
 * <li><b>ToB / HMT</b>: composition is fixed by party size. A three-man
 *     has one combined Freeze; larger teams split North/South, and a
 *     five-man runs two Melee.</li>
 * <li><b>CoX / CM</b>: the host sets a count per role, with Fill
 *     absorbing whatever's left.</li>
 * <li><b>BA</b>: flexible — one of each base role is required, then a
 *     spare slot may double any one of them.</li>
 * </ul>
 */
public final class LfgRoles
{
    private LfgRoles()
    {
    }

    // Every role a player can be in this activity/mode, in display order.
    public static List<LfgRole> playableRoles(LfgActivity activity, boolean hardMode)
    {
        switch (activity)
        {
            case TOB:
                return hardMode
                    ? Arrays.asList(LfgRole.TOB_HM_MELEE, LfgRole.TOB_HM_RANGED, LfgRole.TOB_HM_FRZ,
                        LfgRole.TOB_HM_NFRZ, LfgRole.TOB_HM_SFRZ)
                    : Arrays.asList(LfgRole.TOB_MELEE, LfgRole.TOB_RANGED, LfgRole.TOB_FRZ,
                        LfgRole.TOB_NFRZ, LfgRole.TOB_SFRZ);
            case COX:
                return hardMode
                    ? Arrays.asList(LfgRole.COX_CM_VENG, LfgRole.COX_CM_ANCIENT, LfgRole.COX_CM_NORMAL, LfgRole.COX_CM_FILL)
                    : Arrays.asList(LfgRole.COX_MELEE, LfgRole.COX_MAGE, LfgRole.COX_RUNNER, LfgRole.COX_FILL);
            case BA:
                return Arrays.asList(LfgRole.BA_ATTACKER, LfgRole.BA_DEFENDER, LfgRole.BA_COLLECTOR, LfgRole.BA_HEALER);
            default:
                return Collections.emptyList();
        }
    }

    // The per-mode "any role" wildcard an applicant may pick, or null.
    public static LfgRole anyRole(LfgActivity activity, boolean hardMode)
    {
        switch (activity)
        {
            case TOB:
                return hardMode ? LfgRole.TOB_HM_FILL : LfgRole.TOB_FILL;
            case COX:
                return hardMode ? LfgRole.COX_CM_FILL : LfgRole.COX_FILL;
            case BA:
                return LfgRole.BA_FILL;
            default:
                return null;
        }
    }

    // The roles the host configures a count for (CoX only). ToB is fixed
    // by size and BA is flexible, so neither offers counts.
    public static boolean hostChoosesCounts(LfgActivity activity)
    {
        return activity == LfgActivity.COX;
    }

    // ToB's fixed team composition (a role multiset) for a party size.
    public static List<LfgRole> tobComposition(int partySize, boolean hardMode)
    {
        List<LfgRole> comp = new ArrayList<>();
        int melee = partySize >= 5 ? 2 : (partySize >= 2 ? 1 : 0);
        int ranged = partySize >= 3 ? 1 : 0;
        int freezers = Math.max(0, partySize - melee - ranged);
        for (int i = 0; i < melee; i++)
        {
            comp.add(hardMode ? LfgRole.TOB_HM_MELEE : LfgRole.TOB_MELEE);
        }
        for (int i = 0; i < ranged; i++)
        {
            comp.add(hardMode ? LfgRole.TOB_HM_RANGED : LfgRole.TOB_RANGED);
        }
        if (freezers == 1)
        {
            comp.add(hardMode ? LfgRole.TOB_HM_FRZ : LfgRole.TOB_FRZ);
            return comp;
        }
        int north = (freezers + 1) / 2;
        int south = freezers / 2;
        for (int i = 0; i < north; i++)
        {
            comp.add(hardMode ? LfgRole.TOB_HM_NFRZ : LfgRole.TOB_NFRZ);
        }
        for (int i = 0; i < south; i++)
        {
            comp.add(hardMode ? LfgRole.TOB_HM_SFRZ : LfgRole.TOB_SFRZ);
        }
        return comp;
    }

    // The composition to store for a new party: ToB's fixed layout, the
    // host's CoX counts, or nothing (BA is flexible; everything else has
    // no roles).
    public static List<LfgRole> requiredRoles(LfgActivity activity, boolean hardMode, int capacity,
                                              Map<LfgRole, Integer> coxCounts)
    {
        switch (activity)
        {
            case TOB:
                return tobComposition(capacity, hardMode);
            case COX:
            {
                List<LfgRole> roles = new ArrayList<>();
                int filled = 0;
                for (LfgRole role : playableRoles(activity, hardMode))
                {
                    if (role.isFill())
                    {
                        continue;
                    }
                    int n = coxCounts == null ? 0 : Math.max(0, coxCounts.getOrDefault(role, 0));
                    for (int i = 0; i < n && filled < capacity; i++)
                    {
                        roles.add(role);
                        filled++;
                    }
                }
                LfgRole fill = anyRole(activity, hardMode);
                while (filled < capacity)
                {
                    roles.add(fill);
                    filled++;
                }
                return roles;
            }
            default:
                return Collections.emptyList();
        }
    }

    // The roles still open given the stored composition, the roles already
    // taken (host + accepted members), and the capacity. Each taken role
    // consumes the best-matching open slot: exact first, then an
    // interchangeable one (ToB freezes), then a Fill slot; an "Any" pick
    // consumes the last open slot.
    public static List<LfgRole> openRoles(LfgActivity activity, boolean hardMode, int capacity,
                                          List<LfgRole> required, List<LfgRole> taken)
    {
        if (activity == LfgActivity.BA)
        {
            return baOpenRoles(taken, capacity);
        }
        if (required == null || required.isEmpty())
        {
            return Collections.emptyList();
        }
        List<LfgRole> open = new ArrayList<>(required);
        if (taken != null)
        {
            for (LfgRole t : taken)
            {
                if (t == null)
                {
                    // A member with no declared role still occupies a seat.
                    if (!open.isEmpty())
                    {
                        open.remove(open.size() - 1);
                    }
                    continue;
                }
                int idx = open.indexOf(t);
                if (idx < 0)
                {
                    idx = indexOfFillable(open, t, false);
                }
                if (idx < 0)
                {
                    idx = indexOfFillable(open, t, true);
                }
                if (idx < 0 && !open.isEmpty())
                {
                    idx = open.size() - 1;
                }
                if (idx >= 0)
                {
                    open.remove(idx);
                }
            }
        }
        return open;
    }

    // First open slot `taken` can fill; with allowFill=false, Fill slots
    // are skipped so an exact/interchangeable match wins first.
    private static int indexOfFillable(List<LfgRole> open, LfgRole taken, boolean allowFill)
    {
        for (int i = 0; i < open.size(); i++)
        {
            LfgRole slot = open.get(i);
            if (slot.isFill() && !allowFill)
            {
                continue;
            }
            if (taken.canFill(slot))
            {
                return i;
            }
        }
        return -1;
    }

    // BA: every base role once, then a spare slot may double a role (never
    // more than two of the same). Empty when the team is full.
    private static List<LfgRole> baOpenRoles(List<LfgRole> taken, int capacity)
    {
        Map<LfgRole, Integer> counts = new EnumMap<>(LfgRole.class);
        for (LfgRole role : playableRoles(LfgActivity.BA, false))
        {
            counts.put(role, 0);
        }
        int seats = 0;
        if (taken != null)
        {
            for (LfgRole t : taken)
            {
                seats++;
                if (t != null && counts.containsKey(t))
                {
                    counts.merge(t, 1, Integer::sum);
                }
            }
        }
        int openSlots = Math.max(0, capacity - seats);
        if (openSlots == 0)
        {
            return Collections.emptyList();
        }
        int emptyRoles = 0;
        for (int c : counts.values())
        {
            if (c == 0)
            {
                emptyRoles++;
            }
        }
        boolean allowExtra = openSlots > emptyRoles;
        List<LfgRole> open = new ArrayList<>();
        for (Map.Entry<LfgRole, Integer> e : counts.entrySet())
        {
            int c = e.getValue();
            if (c == 0 || (allowExtra && c < 2))
            {
                open.add(e.getKey());
            }
        }
        return open;
    }

    // Distinct open roles a player could apply as, in playable order, plus
    // the mode's "Any" wildcard when anything is open at all.
    public static List<LfgRole> applyOptions(LfgActivity activity, boolean hardMode, List<LfgRole> open)
    {
        List<LfgRole> options = new ArrayList<>();
        if (open.isEmpty())
        {
            return options;
        }
        for (LfgRole role : playableRoles(activity, hardMode))
        {
            if (role.isFill())
            {
                continue;
            }
            for (LfgRole slot : open)
            {
                if (role.canFill(slot))
                {
                    options.add(role);
                    break;
                }
            }
        }
        LfgRole any = anyRole(activity, hardMode);
        if (any != null)
        {
            options.add(any);
        }
        return options;
    }

    // Compact "Melee, Melee, N freeze" -> "2x Melee, N freeze" summary.
    public static String summarize(List<LfgRole> roles)
    {
        if (roles == null || roles.isEmpty())
        {
            return "";
        }
        Map<LfgRole, Integer> counts = new EnumMap<>(LfgRole.class);
        List<LfgRole> order = new ArrayList<>();
        for (LfgRole r : roles)
        {
            if (!counts.containsKey(r))
            {
                order.add(r);
            }
            counts.merge(r, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (LfgRole r : order)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            int n = counts.get(r);
            if (n > 1)
            {
                sb.append(n).append("x ");
            }
            sb.append(r.getDisplayName());
        }
        return sb.toString();
    }

    // Serialization for the required_roles column: comma-separated keys.
    public static String encode(List<LfgRole> roles)
    {
        if (roles == null || roles.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (LfgRole r : roles)
        {
            if (sb.length() > 0)
            {
                sb.append(',');
            }
            sb.append(r.getKey());
        }
        return sb.toString();
    }

    // Unknown keys (from a newer client) are dropped rather than failing
    // the whole row.
    public static List<LfgRole> decode(String encoded)
    {
        List<LfgRole> roles = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty())
        {
            return roles;
        }
        for (String part : encoded.split(","))
        {
            LfgRole r = LfgRole.fromKey(part.trim());
            if (r != null)
            {
                roles.add(r);
            }
        }
        return roles;
    }
}
