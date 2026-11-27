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
        EconomicNewsEngine backup=new EconomicNewsEngine();
        assertTrue(EconomicCalendarClient.refresh(backup,"","",(url,key)->{
            if(url.equals(EconomicCalendarClient.PRIMARY))throw new IOException("controlled primary outage");
            return EconomicCalendarClient.fetch(url,key);
        }));
        assertEquals(EconomicCalendarClient.FALLBACK,backup.getSource());
        verifyFields("FALLBACK",backup);
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
}
