package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.lfg.LfgActivity;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 * One representative item sprite per LFG activity, drawn from RuneLite's
 * item cache so the plugin ships no image files. IDs are item ids from
 * net.runelite.api.gameval.ItemID (named in the comments).
 */
final class LfgIcons
{
    private static final ImageIcon PLACEHOLDER = new ImageIcon(
        new BufferedImage(LfgIconSource.SIZE, LfgIconSource.SIZE, BufferedImage.TYPE_INT_ARGB));

    private LfgIcons()
    {
    }

    static int itemIdFor(LfgActivity activity)
    {
        switch (activity)
        {
            case COX:
                return 20997;   // TWISTED_BOW
            case TOB:
                return 22325;   // SCYTHE_OF_VITUR
            case TOA:
                return 27275;   // TUMEKENS_SHADOW
            case KREEARRA:
                return 11828;   // ARMADYL_CHESTPLATE
            case GRAARDOR:
                return 11832;   // BANDOS_CHESTPLATE
            case KRIL:
                return 11824;   // ZAMORAK_SPEAR
            case ZILYANA:
                return 11838;   // SARADOMIN_SWORD
            case NEX:
                return 26235;   // ZARYTE_VAMBRACES
            case NIGHTMARE:
                return 24417;   // INQUISITORS_MACE
            case CORP:
                return 12817;   // ELYSIAN (spirit shield)
            case DKS:
                return 6737;    // BERZERKER_RING
            case HUEYCOATL:
                return 30064;   // TOME_OF_EARTH
            case YAMA:
                return 30750;   // OATHPLATE_HELM
            case ROYAL_TITANS:
                return 30634;   // TWINFLAME_STAFF
            case BA:
                return 10551;   // BARBASSAULT_PENANCE_FIGHTER_TORSO
            case ZALCANO:
                return 23953;   // PRIF_TOOL_SEED (crystal tool seed)
            case VOLCANIC_MINE:
                return 21622;   // FOSSIL_VOLCANIC_ASH
            case CASTLE_WARS:
                return 4067;    // CASTLEWARS_TICKET
            case GOTR:
                return 26822;   // ABYSSAL_LANTERN
            case WINTERTODT:
                return 20708;   // PYROMANCER_HOOD
            case GROUP_BOSS:
                return 13576;   // DRAGON_WARHAMMER
            case MINIGAME:
                return 3853;    // NECKLACE_OF_MINIGAMES_8
            case PVP:
                return 964;     // SKULL
            case SKILLING:
                return 11850;   // GRACEFUL_HOOD
            case CHILLING:
            default:
                return 1978;    // CUP_OF_TEA
        }
    }

    // A fixed-size label that shows the activity's sprite once it loads
    // (placeholder first so the row never reflows), with the activity
    // name as tooltip.
    static JLabel label(LfgIconSource source, LfgActivity activity)
    {
        JLabel label = new JLabel(PLACEHOLDER);
        label.setPreferredSize(new Dimension(LfgIconSource.SIZE, LfgIconSource.SIZE));
        label.setToolTipText(activity.getDisplayName());
        source.apply(itemIdFor(activity), label);
        return label;
    }
}
