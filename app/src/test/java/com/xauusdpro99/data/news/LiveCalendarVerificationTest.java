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
        System.out.println("LIVE PRIMARY events="+primary.getEvents().size());
        EconomicNewsEngine backup=new EconomicNewsEngine();
        assertTrue(EconomicCalendarClient.refresh(backup,"","",(url,key)->{
            if(url.equals(EconomicCalendarClient.PRIMARY))throw new IOException("controlled primary outage");
            return EconomicCalendarClient.fetch(url,key);
        }));
        assertEquals(EconomicCalendarClient.FALLBACK,backup.getSource());
        System.out.println("LIVE FALLBACK events="+backup.getEvents().size());
    }
}
