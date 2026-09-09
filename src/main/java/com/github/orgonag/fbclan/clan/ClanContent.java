package com.github.orgonag.fbclan.clan;

import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

/**
 * Clan-curated, read-only content: announcements, the once-per-session
 * welcome message, and the notable-items list. All three live in
 * Supabase tables written solely by the clan's sheet-sync script; the
 * plugin only reads them. Fetches run on the executor and fail soft,
 * keeping the previous value.
 */
@Singleton
public class ClanContent
{
    @Value
    public static class Announcement
    {
        String date;  // YYYY-MM-DD, may be blank
        String title;
        String body;  // newlines preserved
    }

    private static final int MAX_TITLE = 200;
    private static final int MAX_BODY = 10_000;
    private static final int MAX_WELCOME = 200;
    private static final String WELCOME_COLOR = "a020f0";

    private final Supabase db;
    private final Clan clan;
    private final Client client;
    private final ClientThread clientThread;

    private volatile List<Announcement> announcements = Collections.emptyList();
    private volatile Set<String> notableItems = Collections.emptySet();
    private volatile String welcome = "";
    private volatile boolean welcomeShown;

    @Inject
    public ClanContent(Supabase db, Clan clan, Client client, ClientThread clientThread)
    {
        this.db = db;
        this.clan = clan;
        this.client = client;
        this.clientThread = clientThread;
    }

    public List<Announcement> announcements()
    {
        return announcements;
    }

    // Normalized item-name keys (see Names.itemKey).
    public Set<String> notableItems()
    {
        return notableItems;
    }

    public void resetSession()
    {
        welcomeShown = false;
    }

    // ------------------------------------------------------------ fetches

    // Fetch failures keep the previous value; an empty table clears it.
    public void refreshAnnouncements()
    {
        JsonArray rows = db.getOrNull("announcements", "select=posted_at,title,body&order=posted_at.desc,sort_order.asc");
        if (rows == null)
        {
            return;
        }
        List<Announcement> out = new ArrayList<>();
        for (JsonElement el : rows)
        {
            JsonObject row = el.getAsJsonObject();
            String title = cap(Supabase.str(row, "title"), MAX_TITLE);
            String body = cap(Supabase.str(row, "body"), MAX_BODY);
            if (!title.isEmpty() || !body.isEmpty())
            {
                out.add(new Announcement(Supabase.str(row, "posted_at"), title, body));
            }
        }
        announcements = Collections.unmodifiableList(out);
    }

    // Once per session: the list changes rarely.
    public void refreshNotableItems()
    {
        JsonArray rows = db.getOrNull("notable_items", "select=name");
        if (rows == null)
        {
            return;
        }
        Set<String> names = new HashSet<>();
        for (JsonElement el : rows)
        {
            String key = Names.itemKey(Supabase.str(el.getAsJsonObject(), "name"));
            if (!key.isEmpty())
            {
                names.add(key);
            }
        }
        notableItems = Collections.unmodifiableSet(names);
    }

    public void refreshWelcome()
    {
        JsonArray rows = db.getOrNull("welcome_message", "select=message&id=eq.1");
        if (rows == null)
        {
            return;
        }
        // Remote text printed into the chatbox: strip anything that could
        // read as chat markup, collapse whitespace, cap the length.
        String raw = rows.size() == 0 ? "" : Supabase.str(rows.get(0).getAsJsonObject(), "message");
        String clean = raw.replaceAll("<[^>]*>", "").replace("<", "").replace(">", "")
            .replaceAll("\\s+", " ").trim();
        welcome = cap(clean, MAX_WELCOME);
    }

    // ------------------------------------------------------------ welcome

    // Called after the startup fetch and after verification; whichever
    // lands last prints the line. Synchronized: both callers are executor
    // threads.
    public synchronized void maybeShowWelcome()
    {
        String message = welcome;
        if (!clan.isVerified() || welcomeShown || message.isEmpty())
        {
            return;
        }
        welcomeShown = true;
        clientThread.invokeLater(() -> {
            GameState gs = client.getGameState();
            if (gs == GameState.LOADING || gs == GameState.HOPPING || gs == GameState.CONNECTION_LOST)
            {
                return false; // transient — try again next tick
            }
            if (!clan.isVerified() || gs != GameState.LOGGED_IN)
            {
                welcomeShown = false; // true logout — re-attempt next login
                return true;
            }
            // postEvent=false keeps our own line out of the chat-event
            // pipeline (the pet matcher would otherwise see it).
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=" + WELCOME_COLOR + ">[Final Boss] " + message + "</col>", null, false);
            return true;
        });
    }

    private static String cap(String s, int max)
    {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
