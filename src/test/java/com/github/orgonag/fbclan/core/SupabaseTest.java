package com.github.orgonag.fbclan.core;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupabaseTest
{
    @Test public void followsActualServerPageSize() throws Exception
    {
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            String value=chain.request().url().queryParameter("offset");
            int offset=value==null ? 0 : Integer.parseInt(value);
            StringBuilder body=new StringBuilder("[");
            for(int i=offset;i<Math.min(offset+200,1200);i++) body.append(i==offset?"":",").append("{\"id\":").append(i).append('}');
            body.append(']');
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
                .header("Content-Range",offset+"-"+Math.min(offset+199,1199)+"/1200")
                .body(ResponseBody.create(MediaType.parse("application/json"),body.toString())).build();
        }).build();
        com.google.gson.JsonArray result=new Supabase(http).getOrNull("example","select=id&order=id.asc");
        assertEquals(1200,result.size()); assertEquals(6,requests.get());
        assertEquals(1199,result.get(1199).getAsJsonObject().get("id").getAsInt());
    }
    @Test public void httpFailureIsNotAnEmptyTable()
    {
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
            .body(ResponseBody.create(MediaType.parse("application/json"),"{}")).build()).build();
        assertNull(new Supabase(http).getOrNull("example","select=id"));
    }
    @Test public void explicitLimitDoesNotFetchMore()
    {
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Range","0-0/1200").body(ResponseBody.create(MediaType.parse("application/json"),"[{\"id\":1}]")).build();
        }).build();
        assertEquals(1,new Supabase(http).getOrNull("example","select=id&limit=1").size());
        assertEquals(1,requests.get());
    }
}
