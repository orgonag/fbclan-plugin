package com.github.orgonag.fbclan.lfg;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * A role a player can fill in a role-based activity. Each difficulty mode
 * owns a fully separate role set so a pick in one can never be matched
 * against a party of another. The *_FILL values are per-mode wildcards:
 * for Chambers of Xeric a Fill slot is a real, flexible team spot; for
 * Theatre of Blood and Barbarian Assault it only ever means "any role"
 * on an application and is never part of a composition.
 *
 * Stored in the database by enum name (see LfgParty / LfgApplicant).
 */
@Getter
public enum LfgRole
{
    // Theatre of Blood (normal)
    TOB_MELEE("Melee"),
    TOB_RANGED("Ranged"),
    TOB_FRZ("Freeze"),
    TOB_NFRZ("North freeze"),
    TOB_SFRZ("South freeze"),
    TOB_FILL("Any"),

    // Theatre of Blood Hard Mode
    TOB_HM_MELEE("Melee"),
    TOB_HM_RANGED("Ranged"),
    TOB_HM_FRZ("Freeze"),
    TOB_HM_NFRZ("North freeze"),
    TOB_HM_SFRZ("South freeze"),
    TOB_HM_FILL("Any"),

    // Chambers of Xeric (normal)
    COX_MELEE("Melee"),
    COX_MAGE("Mage"),
    COX_RUNNER("Runner"),
    COX_FILL("Fill"),

    // Chambers of Xeric Challenge Mode
    COX_CM_VENG("Veng"),
    COX_CM_ANCIENT("Ancient"),
    COX_CM_NORMAL("Normal spells"),
    COX_CM_FILL("Fill"),

    // Barbarian Assault
    BA_ATTACKER("Attacker"),
    BA_DEFENDER("Defender"),
    BA_COLLECTOR("Collector"),
    BA_HEALER("Healer"),
    BA_FILL("Any");

    // A three-man ToB team has a single combined Freeze slot instead of a
    // north/south pair, so the three are interchangeable when matching.
    private static final Set<LfgRole> TOB_FREEZE = EnumSet.of(TOB_FRZ, TOB_NFRZ, TOB_SFRZ);
    private static final Set<LfgRole> TOB_HM_FREEZE = EnumSet.of(TOB_HM_FRZ, TOB_HM_NFRZ, TOB_HM_SFRZ);
    private static final Set<LfgRole> FILLS = EnumSet.of(TOB_FILL, TOB_HM_FILL, COX_FILL, COX_CM_FILL, BA_FILL);

    private final String displayName;

    LfgRole(String displayName)
    {
        this.displayName = displayName;
    }

    public String getKey()
    {
        return name();
    }

    public boolean isFill()
    {
        return FILLS.contains(this);
    }

    // True when a player who picked this role can take a slot advertised as
    // `needed`: the same role, any mode's Fill on either side, or another
    // freeze slot of the same ToB mode.
    public boolean canFill(LfgRole needed)
    {
        if (needed == null)
        {
            return false;
        }
        if (this == needed || this.isFill() || needed.isFill())
        {
            return true;
        }
        return (TOB_FREEZE.contains(this) && TOB_FREEZE.contains(needed))
            || (TOB_HM_FREEZE.contains(this) && TOB_HM_FREEZE.contains(needed));
    }

    public static LfgRole fromKey(String key)
    {
        if (key == null)
        {
            return null;
        }
        try
        {
            return valueOf(key);
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
}
