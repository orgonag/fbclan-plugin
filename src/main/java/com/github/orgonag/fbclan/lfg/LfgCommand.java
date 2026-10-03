package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * The "!lfg" chat command, for the local player's own messages only
 * (other players' commands are ignored; the typed message still posts
 * normally). "!lfg" or "!lfg parties" prints the open parties;
 * "!lfg tob 4" posts an ASAP party with default details, the same as the
 * panel's Post now. Applying and managing stay in the panel.
 */
@Singleton
public class LfgCommand
{
    private static final String USAGE = "!lfg lists parties · !lfg <activity> [size] posts an ASAP party, e.g. !lfg tob 4";
    private static final java.util.regex.Pattern TIME = java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}|\\d{1,2}(am|pm)|am|pm|tomorrow|today|tonight");

    /** What an alias posts: the activity, hard mode, and ToA invocation. */
    private static final class Alias
    {
        final Activity activity;
        final boolean hard;
        final int invocation;

        Alias(Activity activity, boolean hard, int invocation)
        {
            this.activity = activity;
            this.hard = hard;
            this.invocation = invocation;
        }
    }

    private static final java.util.Map<String, Alias> ALIASES = new java.util.HashMap<>();

    static
    {
        alias(Activity.TOB, false, 0, "tob", "theatre", "theatreofblood");
        alias(Activity.TOB, true, 0, "hmt", "hmtob", "tobhm", "hardmodetob");
        alias(Activity.COX, false, 0, "cox", "raids", "chambers", "xeric", "chambersofxeric");
        alias(Activity.COX, true, 0, "cm", "coxcm", "challengemode");
        alias(Activity.TOA, false, 150, "toa", "tombs", "tombsofamascut");
        alias(Activity.TOA, false, 300, "expert", "etoa", "expertoa", "toaexpert");
        alias(Activity.NEX, false, 0, "nex");
        alias(Activity.KREEARRA, false, 0, "arma", "kree", "armadyl", "kreearra");
        alias(Activity.GRAARDOR, false, 0, "bandos", "graardor");
        alias(Activity.KRIL, false, 0, "zammy", "kril", "zamorak");
        alias(Activity.ZILYANA, false, 0, "sara", "zily", "saradomin", "zilyana");
        alias(Activity.NIGHTMARE, false, 0, "nm", "nightmare");
        alias(Activity.CORP, false, 0, "corp", "corporealbeast");
        alias(Activity.DKS, false, 0, "dks", "dagannothkings");
        alias(Activity.HUEYCOATL, false, 0, "huey", "hueycoatl");
        alias(Activity.YAMA, false, 0, "yama");
        alias(Activity.ROYAL_TITANS, false, 0, "titans", "rt", "royaltitans");
        alias(Activity.BA, false, 0, "ba", "barbarianassault");
        alias(Activity.ZALCANO, false, 0, "zalc", "zalcano");
        alias(Activity.VOLCANIC_MINE, false, 0, "vm", "volc", "volcanicmine");
        alias(Activity.CASTLE_WARS, false, 0, "cw", "castlewars");
        alias(Activity.GOTR, false, 0, "gotr", "rift", "guardiansoftherift");
        alias(Activity.WINTERTODT, false, 0, "wt", "todt", "wintertodt");
        alias(Activity.GROUP_BOSS, false, 0, "boss", "groupboss");
        alias(Activity.MINIGAME, false, 0, "mg", "minigame");
        alias(Activity.PVP, false, 0, "pvp", "pk");
        alias(Activity.SKILLING, false, 0, "skill", "skilling");
        alias(Activity.CHILLING, false, 0, "chill", "chilling");
    }

    private static void alias(Activity activity, boolean hard, int invocation, String... names)
    {
        for (String n : names)
        {
            ALIASES.put(n, new Alias(activity, hard, invocation));
        }
    }
    private static final Set<ChatMessageType> LOCAL_AUTHOR = EnumSet.of(
        ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT, ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.PRIVATECHATOUT);

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final Clan clan;
    private final PartyApi api;
    private final PartyBoard board;
    private final ScheduledExecutorService executor;

    @Inject
    public LfgCommand(Client client, ClientThread clientThread, FinalBossConfig config, Clan clan, PartyApi api,
                      PartyBoard board, ScheduledExecutorService executor)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.clan = clan;
        this.api = api;
        this.board = board;
        this.executor = executor;
    }

    // Client thread.
    public void onChatMessage(ChatMessage event)
    {
        if (!config.enableLfg() || !clan.canUpload() || !LOCAL_AUTHOR.contains(event.getType()))
        {
            return;
        }
        // PRIVATECHATOUT's name is the recipient; the author is us by definition.
        if (event.getType() != ChatMessageType.PRIVATECHATOUT
            && (event.getName() == null || !Names.same(Text.removeTags(event.getName()), clan.rsn())))
        {
            return;
        }
        String text = Text.removeTags(event.getMessage()).trim();
        if (!text.toLowerCase(Locale.ROOT).startsWith("!lfg"))
        {
            return;
        }
        String rest = text.substring(4);
        if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)))
        {
            return; // "!lfgsomething" is a different command
        }
        String keyword = rest.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (keyword.isEmpty() || keyword.equals("parties") || keyword.equals("party"))
        {
            com.github.orgonag.fbclan.core.Session session = clan.snapshot();
            executor.submit(() -> {
                if (!clan.current(session) || !config.enableLfg()) return;
                PartyApi.Snapshot snapshot = api.fetch();
                List<Party> parties = snapshot == null ? null : snapshot.getParties();
                String reply = parties == null ? "Couldn't reach the party board — try again." : summarize(parties);
                clientThread.invokeLater(() -> {
                    if (clan.current(session) && config.enableLfg() && client.getGameState() == GameState.LOGGED_IN)
                    {
                        print(reply);
                    }
                });
            });
        }
        else if (keyword.equals("help") || keyword.equals("?"))
        {
            print(USAGE);
        }
        else
        {
            post(rest.trim().toLowerCase(Locale.ROOT));
        }
    }

    // "!lfg tob 4": activity words, then an optional size as the last word. Client thread.
    private void post(String args)
    {
        List<String> words = new java.util.ArrayList<>(java.util.Arrays.asList(args.split("\\s+")));
        for (String w : words)
        {
            if (TIME.matcher(w).matches())
            {
                print("[LFG] Start times are set in the Final Boss panel; chat posts are ASAP.");
                return;
            }
        }
        Integer size = null;
        if (words.size() > 1 && words.get(words.size() - 1).matches("\\d{1,3}"))
        {
            size = Integer.parseInt(words.remove(words.size() - 1));
        }
        String key = String.join("", words).replaceAll("[^a-z0-9]", "");
        Alias alias = ALIASES.get(key);
        if (alias == null)
        {
            for (Activity a : Activity.values())
            {
                if (a.name().toLowerCase(Locale.ROOT).replace("_", "").equals(key)
                    || a.getDisplayName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").equals(key))
                {
                    alias = new Alias(a, false, a.usesInvocation() ? 150 : 0);
                }
            }
        }
        if (alias == null)
        {
            String typed = Text.removeTags(args);
            print("[LFG] Unknown activity \"" + (typed.length() > 20 ? typed.substring(0, 20) : typed)
                + "\". Try tob, hmt, cox, cm, toa, nex, bandos... (!lfg help)");
            return;
        }
        Activity activity = alias.activity;
        int min = Math.max(Party.MIN_CAPACITY, activity.getMinPartySize());
        int max = Math.max(min, activity.getMaxPartySize());
        int capacity = size == null ? activity.defaultPartySize() : size;
        if (capacity < min || capacity > max)
        {
            print("[LFG] " + activity.shortName() + (min == max ? " needs exactly " + min : " party size must be " + min + "-" + max) + ".");
            return;
        }
        if (!board.running())
        {
            print("[LFG] The party board isn't ready yet. Try again in a moment.");
            return;
        }
        Party asap = board.mineAsap();
        if (asap != null)
        {
            print("[LFG] You already have an ASAP party (" + asap.title() + " " + asap.memberCount() + "/" + asap.getCapacity()
                + "). Cancel it in the panel first.");
            return;
        }
        String rsn = clan.rsn();
        int world = client.getWorld();
        Party party = Party.quick(rsn, activity, alias.hard, alias.invocation, capacity, world > 0 ? world : null);
        com.github.orgonag.fbclan.core.Session session = clan.snapshot();
        executor.submit(() -> board.post(party, failure -> clientThread.invokeLater(() -> {
            if (!clan.current(session) || client.getGameState() != GameState.LOGGED_IN) return;
            print(failure == null
                ? "[LFG] Posted " + party.title() + " 1/" + capacity + " (ASAP" + (world > 0 ? ", W" + world : "") + "). Manage it in the Final Boss panel."
                : "[LFG] " + failure);
        })));
    }

    // "Parties: HMT - Host 3/5 W420 - needs Melee, North freeze | ..."
    static String summarize(List<Party> parties)
    {
        if (parties.isEmpty())
        {
            return "No parties are being hosted right now.";
        }
        StringBuilder sb = new StringBuilder("Parties: ");
        boolean first = true;
        for (Party p : parties)
        {
            if (sb.length() > 600) { sb.append(" | More in the panel."); break; }
            sb.append(first ? "" : " | ").append(p.title())
                .append(p.isScheduled() ? " @ " + PartyBoard.when(p.getScheduledFor()) : "").append(" - ").append(p.getHostRsn())
                .append(' ').append(p.memberCount()).append('/').append(p.getCapacity());
            first = false;
            if (p.getWorld() != null)
            {
                sb.append(" W").append(p.getWorld());
            }
            if (p.isFull())
            {
                sb.append(" (full)");
            }
            else
            {
                String needs = Role.summarize(p.openRoles());
                if (!needs.isEmpty())
                {
                    sb.append(" - needs ").append(needs);
                }
            }
        }
        return sb.toString();
    }

    private void print(String message)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
