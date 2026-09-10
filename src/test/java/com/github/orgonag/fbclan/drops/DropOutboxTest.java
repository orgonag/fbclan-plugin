package com.github.orgonag.fbclan.drops;
import com.github.orgonag.fbclan.core.Session;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class DropOutboxTest
{
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void survivesRestartAndIsolatesProfiles() throws Exception
    {
        java.nio.file.Path directory = temp.newFolder().toPath();
        Session alice = new Session(1,"Alice","profile1",true);
        JsonObject row = new JsonObject();
        String id = java.util.UUID.randomUUID().toString();
        row.addProperty("event_id",id); row.addProperty("rsn","Alice");
        new DropOutbox(directory).add(alice,row);
        DropOutbox restarted = new DropOutbox(directory);
        assertTrue(restarted.pending(new Session(2,"Bob","profile2",true)).isEmpty());
        assertTrue(restarted.pending(new Session(2,"Alice","different",true)).isEmpty());
        assertEquals(id,restarted.pending(alice).get(0).get("event_id").getAsString());
        restarted.remove(id); assertTrue(restarted.pending(alice).isEmpty());
    }
}
