package com.github.orgonag.fbclan.lfg;
import com.github.orgonag.fbclan.core.*;
import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class PartyApiTest
{
    @Test public void lostResponseRetriesOneLogicalOperation()
    {
        Clan clan=mock(Clan.class); Supabase db=mock(Supabase.class);
        Session session=new Session(7,"Alice","profile",true);
        when(clan.snapshot()).thenReturn(session); when(clan.current(session)).thenReturn(true);
        when(db.rpcResult(eq("fb_lfg"),any())).thenReturn(new ApiResult(0,null,"timeout"),
            new ApiResult(200,new JsonParser().parse("{\"status\":\"ok\"}"),null));
        Party party=Party.builder().hostRsn("Alice").activity(Activity.YAMA).capacity(2)
            .requiredRoles(Collections.emptyList()).applicants(Collections.emptyList()).build();
        assertTrue(new PartyApi(db,clan).save(party));
        ArgumentCaptor<JsonObject> args=ArgumentCaptor.forClass(JsonObject.class);
        verify(db,times(2)).rpcResult(eq("fb_lfg"),args.capture());
        assertEquals(args.getAllValues().get(0),args.getAllValues().get(1));
        assertEquals("Alice",args.getValue().get("p_actor").getAsString());
        assertEquals("create",args.getValue().get("p_action").getAsString());
    }
    @Test public void staleSessionCannotWrite()
    {
        Clan clan=mock(Clan.class); Supabase db=mock(Supabase.class);
        Session session=new Session(7,"Alice","profile",true);
        when(clan.snapshot()).thenReturn(session);
        assertFalse(new PartyApi(db,clan).disband(UUID.randomUUID().toString()));
        verifyNoInteractions(db);
    }
    @Test public void domainConflictIsNotRetriedAsHttpSuccess()
    {
        Clan clan=mock(Clan.class); Supabase db=mock(Supabase.class);
        Session session=new Session(7,"Alice","profile",true);
        when(clan.snapshot()).thenReturn(session); when(clan.current(session)).thenReturn(true);
        when(db.rpcResult(eq("fb_lfg"),any())).thenReturn(new ApiResult(200,new JsonParser().parse("{\"status\":\"conflict\"}"),null));
        assertFalse(new PartyApi(db,clan).disband(UUID.randomUUID().toString()));
        verify(db,times(1)).rpcResult(eq("fb_lfg"),any());
    }
}
