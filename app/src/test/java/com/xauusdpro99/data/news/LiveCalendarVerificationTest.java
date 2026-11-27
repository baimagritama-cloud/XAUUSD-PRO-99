package com.xauusdpro99.data.news;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import java.io.IOException;
public class LiveCalendarVerificationTest {
    @Test public void livePrimaryAndRealFallback() throws Exception {
        assumeTrue("Explicit live network verification required","1".equals(System.getenv("VERIFY_LIVE_CALENDAR")));
        EconomicNewsEngine primary=new EconomicNewsEngine();
        EconomicCalendarClient.Response p=EconomicCalendarClient.fetch(EconomicCalendarClient.PRIMARY,"");
        assertTrue(primary.getDetail(),primary.updateJson(p.body,p.source,p.receivedAtMs));
        verifyFields("PRIMARY",primary);
        verifyPayload(p.body,"events",primary);
        EconomicNewsEngine backup=new EconomicNewsEngine();
        final String[] fallbackBody={null};
        assertTrue(EconomicCalendarClient.refresh(backup,"","",(url,key)->{
            if(url.equals(EconomicCalendarClient.PRIMARY))throw new IOException("controlled primary outage");
            EconomicCalendarClient.Response response=EconomicCalendarClient.fetch(url,key);
            fallbackBody[0]=response.body;
            return response;
        }));
        assertEquals(EconomicCalendarClient.FALLBACK,backup.getSource());
        verifyFields("FALLBACK",backup);
        verifyPayload(fallbackBody[0],"data",backup);
    }
    private static void verifyFields(String label,EconomicNewsEngine engine) {
        assertEquals(EconomicNewsEngine.DataStatus.LIVE,engine.getDataStatus());
        assertFalse(engine.getEvents().isEmpty());
        int previous=0,forecast=0,actual=0,knownImpact=0;
        for(EconomicNewsEngine.Event e:engine.getEvents()){
            assertTrue(e.releaseAtMs>0);
            assertNotNull(e.name);
            assertNotNull(e.source);
            assertNotNull(e.impact);
            if(e.impact!=EconomicNewsEngine.Impact.UNKNOWN)knownImpact++;
            if(e.previous!=null)previous++;
            if(e.forecast!=null)forecast++;
            if(e.actual!=null)actual++;
        }
        assertTrue("No recognized impact in "+label,knownImpact>0);
        System.out.println("LIVE "+label+" events="+engine.getEvents().size()+" previous="+previous+" forecast="+forecast+" actual="+actual+" knownImpact="+knownImpact);
    }
    private static void verifyPayload(String body,String arrayKey,EconomicNewsEngine engine) {
        com.google.gson.JsonArray rows=com.google.gson.JsonParser.parseString(body).getAsJsonObject().getAsJsonArray(arrayKey);
        boolean primary=arrayKey.equals("events");
        for(EconomicNewsEngine.Event event:engine.getEvents()){
            com.google.gson.JsonObject match=null;
            for(com.google.gson.JsonElement row:rows){
                com.google.gson.JsonObject o=row.getAsJsonObject();
                String name=o.get(primary?"title":"eventName").getAsString();
                long time=primary?o.get("ts").getAsLong()*1000L:java.time.Instant.parse(o.get("scheduledAt").getAsString()).toEpochMilli();
                if(name.equals(event.name)&&time==event.releaseAtMs){match=o;break;}
            }
            assertNotNull("Source row missing: "+event.name,match);
            assertEquals(rawValue(match,"previous"),event.previous);
            assertEquals(rawValue(match,"forecast"),event.forecast);
            assertEquals(rawValue(match,"actual"),event.actual);
            assertEquals(match.get(primary?"impact":"importance").getAsString().toUpperCase(java.util.Locale.US),event.impact.name());
        }
    }
    private static String rawValue(com.google.gson.JsonObject o,String key){
        if(!o.has(key)||o.get(key).isJsonNull())return null;
        String s=o.get(key).getAsString().trim();
        return s.isEmpty()||s.equalsIgnoreCase("null")||s.equalsIgnoreCase("n/a")||s.equals("--")?null:s;
    }
}
