package com.github.orgonag.fbclan.lfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * A seat a player can fill in a role-based activity, plus the
 * composition rules. Each difficulty mode owns its own role set so a
 * pick in one mode never matches a party of another. The *_FILL values
 * are wildcards: a real flexible seat in CoX, "any role" on an
 * application for ToB and BA.
 *
 * <ul>
 * <li>ToB / HMT: composition is fixed by size (3 = Melee/Ranged/Freeze;
 *     4 splits North/South; 5 runs two Melee).</li>
 * <li>CoX / CM: the host sets a count per role, Fill takes the rest.</li>
 * <li>BA: one of each base role, then a spare seat may double one.</li>
 * </ul>
 * Stored in the database by enum name.
 */
@Getter
public enum Role
{
    TOB_MELEE("Melee"), TOB_RANGED("Ranged"), TOB_FRZ("Freeze"), TOB_NFRZ("North freeze"), TOB_SFRZ("South freeze"), TOB_FILL("Any"),
    TOB_HM_MELEE("Melee"), TOB_HM_RANGED("Ranged"), TOB_HM_FRZ("Freeze"), TOB_HM_NFRZ("North freeze"), TOB_HM_SFRZ("South freeze"), TOB_HM_FILL("Any"),
    COX_MELEE("Melee"), COX_MAGE("Mage"), COX_RUNNER("Runner"), COX_FILL("Fill"),
    COX_CM_VENG("Veng"), COX_CM_ANCIENT("Ancient"), COX_CM_NORMAL("Normal spells"), COX_CM_FILL("Fill"),
    BA_ATTACKER("Attacker"), BA_DEFENDER("Defender"), BA_COLLECTOR("Collector"), BA_HEALER("Healer"), BA_FILL("Any");

    private static final Set<Role> TOB_FREEZE = EnumSet.of(TOB_FRZ, TOB_NFRZ, TOB_SFRZ);
    private static final Set<Role> TOB_HM_FREEZE = EnumSet.of(TOB_HM_FRZ, TOB_HM_NFRZ, TOB_HM_SFRZ);
    private static final Set<Role> FILLS = EnumSet.of(TOB_FILL, TOB_HM_FILL, COX_FILL, COX_CM_FILL, BA_FILL);

    private final String displayName;

    Role(String displayName)
    {
        this.displayName = displayName;
    }

    public String key()
    {
        return name();
    }

    public boolean isFill()
    {
        return FILLS.contains(this);
    }

    // Can a player who picked this role take a seat advertised as `needed`?
    public boolean canFill(Role needed)
    {
        if (needed == null)
        {
            return false;
        }
        if (!family().equals(needed.family())) return false;
        if (this == needed || isFill() || needed.isFill())
        {
            return true;
        }
        return (this == TOB_FRZ && TOB_FREEZE.contains(needed))
            || (this == TOB_HM_FRZ && TOB_HM_FREEZE.contains(needed));
    }

    private String family()
    {
        String key = name();
        if (key.startsWith("TOB_HM_")) return "TOB_HM";
        if (key.startsWith("COX_CM_")) return "COX_CM";
        return key.substring(0, key.indexOf('_'));
    }

    public static Role fromKey(String key)
    {
        try
        {
            return key == null ? null : valueOf(key);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    @Override
    public String toString()
    {
        return displayName;
    }

    // ------------------------------------------------------------ composition

    // Every role a player can be in this activity/mode, in display order.
    public static List<Role> playable(Activity activity, boolean hardMode)
    {
        switch (activity)
        {
            case TOB:
                return hardMode
                    ? Arrays.asList(TOB_HM_MELEE, TOB_HM_RANGED, TOB_HM_FRZ, TOB_HM_NFRZ, TOB_HM_SFRZ)
                    : Arrays.asList(TOB_MELEE, TOB_RANGED, TOB_FRZ, TOB_NFRZ, TOB_SFRZ);
            case COX:
                return hardMode
                    ? Arrays.asList(COX_CM_VENG, COX_CM_ANCIENT, COX_CM_NORMAL, COX_CM_FILL)
                    : Arrays.asList(COX_MELEE, COX_MAGE, COX_RUNNER, COX_FILL);
            case BA:
                return Arrays.asList(BA_ATTACKER, BA_DEFENDER, BA_COLLECTOR, BA_HEALER);
            default:
                return Collections.emptyList();
        }
    }

    public static Role any(Activity activity, boolean hardMode)
    {
        switch (activity)
        {
            case TOB: return hardMode ? TOB_HM_FILL : TOB_FILL;
            case COX: return hardMode ? COX_CM_FILL : COX_FILL;
            case BA: return BA_FILL;
            default: return null;
        }
    }

    public static List<Role> tobComposition(int size, boolean hard)
    {
        List<Role> comp = new ArrayList<>();
        int melee = size >= 5 ? 2 : size >= 2 ? 1 : 0;
        int ranged = size >= 3 ? 1 : 0;
        int freezers = Math.max(0, size - melee - ranged);
        for (int i = 0; i < melee; i++)
        {
            comp.add(hard ? TOB_HM_MELEE : TOB_MELEE);
        }
        for (int i = 0; i < ranged; i++)
        {
            comp.add(hard ? TOB_HM_RANGED : TOB_RANGED);
        }
        if (freezers == 1)
        {
            comp.add(hard ? TOB_HM_FRZ : TOB_FRZ);
            return comp;
        }
        for (int i = 0; i < (freezers + 1) / 2; i++)
        {
            comp.add(hard ? TOB_HM_NFRZ : TOB_NFRZ);
        }
        for (int i = 0; i < freezers / 2; i++)
        {
            comp.add(hard ? TOB_HM_SFRZ : TOB_SFRZ);
        }
        return comp;
    }

    // The composition to store for a new party: ToB's fixed layout, the
    // host's CoX counts padded with Fill, or nothing.
    public static List<Role> required(Activity activity, boolean hard, int capacity, Map<Role, Integer> coxCounts)
    {
        if (activity == Activity.TOB)
        {
            return tobComposition(capacity, hard);
        }
        if (activity != Activity.COX)
        {
            return Collections.emptyList();
        }
        List<Role> roles = new ArrayList<>();
        for (Role role : playable(activity, hard))
        {
            int n = role.isFill() || coxCounts == null ? 0 : Math.max(0, coxCounts.getOrDefault(role, 0));
            if (roles.size() + n > capacity) throw new IllegalArgumentException("Role counts exceed party capacity");
            for (int i = 0; i < n; i++)
            {
                roles.add(role);
            }
        }
        while (roles.size() < capacity)
        {
            roles.add(any(activity, hard));
        }
        return roles;
    }

    // Seats still open given the stored composition and the roles already
    // taken (host + accepted members). Each taken role consumes the best
    // matching seat: exact, then interchangeable (ToB freezes), then Fill,
    // then whatever's last.
    public static List<Role> open(Activity activity, boolean hard, int capacity, List<Role> required, List<Role> taken)
    {
        if (activity == Activity.BA)
        {
            return baOpen(taken, capacity);
        }
        if (required == null || required.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Role> open = new ArrayList<>(required);
        for (Role t : taken)
        {
            if (open.isEmpty())
            {
                break;
            }
            if (t == null) return Collections.emptyList();
            int idx = open.indexOf(t);
            if (idx < 0)
            {
                idx = fillable(open, t, false);
            }
            if (idx < 0)
            {
                idx = fillable(open, t, true);
            }
            if (idx < 0) return Collections.emptyList();
            open.remove(idx);
        }
        return open;
    }

    private static int fillable(List<Role> open, Role taken, boolean allowFill)
    {
        for (int i = 0; i < open.size(); i++)
        {
            if ((allowFill || !open.get(i).isFill()) && taken.canFill(open.get(i)))
            {
                return i;
            }
        }
        return -1;
    }

    // BA: each base role once, then a spare seat may double one (never
    // more than two of the same).
    private static List<Role> baOpen(List<Role> taken, int capacity)
    {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role r : playable(Activity.BA, false))
        {
            counts.put(r, 0);
        }
        for (Role t : taken)
        {
            if (t != null && counts.containsKey(t))
            {
                counts.merge(t, 1, Integer::sum);
            }
        }
        int openSeats = Math.max(0, capacity - taken.size());
        if (openSeats == 0)
        {
            return Collections.emptyList();
        }
        long empty = counts.values().stream().filter(c -> c == 0).count();
        boolean allowExtra = openSeats > empty;
        List<Role> open = new ArrayList<>();
        counts.forEach((role, c) -> {
            if (c == 0 || (allowExtra && c < 2))
            {
                open.add(role);
            }
        });
        return open;
    }

    // Distinct roles a player could apply as, plus the mode's wildcard.
    public static List<Role> applyOptions(Activity activity, boolean hard, List<Role> open)
    {
        List<Role> options = new ArrayList<>();
        if (open.isEmpty())
        {
            return options;
        }
        for (Role role : playable(activity, hard))
        {
            if (!role.isFill() && open.stream().anyMatch(role::canFill))
            {
                options.add(role);
            }
        }
        Role any = any(activity, hard);
        if (any != null)
        {
            options.add(any);
        }
        return options;
    }

    // "Melee, Melee, N freeze" -> "2x Melee, North freeze".
    public static String summarize(List<Role> roles)
    {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        List<Role> order = new ArrayList<>();
        for (Role r : roles)
        {
            if (counts.merge(r, 1, Integer::sum) == 1)
            {
                order.add(r);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Role r : order)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            int n = counts.get(r);
            sb.append(n > 1 ? n + "x " : "").append(r.displayName);
        }
        return sb.toString();
    }

    // required_roles column: comma-separated keys.
    public static String encode(List<Role> roles)
    {
        if (roles == null || roles.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Role r : roles)
        {
            sb.append(sb.length() > 0 ? "," : "").append(r.key());
        }
        return sb.toString();
    }

    // Unknown keys (a newer client) are dropped, not fatal.
    public static List<Role> decode(String encoded)
    {
        List<Role> roles = new ArrayList<>();
        if (encoded != null)
        {
            for (String part : encoded.split(","))
            {
                Role r = fromKey(part.trim());
                if (r != null)
                {
                    roles.add(r);
                }
            }
        }
        return roles;
    }
}
