package com.xauusdpro99.data.news;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;

public class EconomicCalendarFallbackTest {
    private static final long NOW=1800000000000L;
    private static final String PRIMARY="{\"events\":[{\"currency\":\"USD\",\"title\":\"CPI\",\"ts\":1800007200}]}";
    private static final String BACKUP="{\"data\":[{\"eventName\":\"CPI\",\"source\":\"bls\",\"scheduledAt\":\""+java.time.Instant.ofEpochMilli(NOW+7200000L)+"\"}]}";
    @Test public void primarySuccessDoesNotCallBackup() throws Exception {
        EconomicNewsEngine e=new EconomicNewsEngine();
        List<String> calls=new ArrayList<>();
        assertTrue(EconomicCalendarClient.refresh(e,"","",(url,key)->{
            calls.add(url);return new EconomicCalendarClient.Response(PRIMARY,url,NOW);
        }));
        assertEquals(Arrays.asList(EconomicCalendarClient.PRIMARY),calls);
    }
    @Test public void networkFailureUsesBackup() throws Exception {
        EconomicNewsEngine e=new EconomicNewsEngine();
        List<String> calls=new ArrayList<>();
        assertTrue(EconomicCalendarClient.refresh(e,"","",(url,key)->{
            calls.add(url);
            if(url.equals(EconomicCalendarClient.PRIMARY))throw new IOException("simulated timeout");
            return new EconomicCalendarClient.Response(BACKUP,url,NOW);
        }));
        assertEquals(Arrays.asList(EconomicCalendarClient.PRIMARY,EconomicCalendarClient.FALLBACK),calls);
        assertEquals(EconomicCalendarClient.FALLBACK,e.getSource());
    }
    @Test public void malformedOrEmptyPrimaryUsesBackup() throws Exception {
        for(String invalid:Arrays.asList("not-json","{\"events\":[]}")){
            EconomicNewsEngine e=new EconomicNewsEngine();
            assertTrue(EconomicCalendarClient.refresh(e,"","",(url,key)->new EconomicCalendarClient.Response(url.equals(EconomicCalendarClient.PRIMARY)?invalid:BACKUP,url,NOW)));
            assertEquals(EconomicCalendarClient.FALLBACK,e.getSource());
        }
    }
    @Test public void bothInvalidNeverProduceLiveData() throws Exception {
        EconomicNewsEngine e=new EconomicNewsEngine();
        assertFalse(EconomicCalendarClient.refresh(e,"","",(url,key)->new EconomicCalendarClient.Response("[]",url,NOW)));
        assertNotEquals(EconomicNewsEngine.DataStatus.LIVE,e.getDataStatus());
        assertTrue(e.evaluateAlerts(NOW).isEmpty());
    }
}
