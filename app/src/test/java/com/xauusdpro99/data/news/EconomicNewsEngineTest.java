package com.xauusdpro99.data.news;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class EconomicNewsEngineTest {
    private static final long NOW=1_800_000_000_000L;
    private static String json(String eventDate,String actual) {
        return "[{\"Country\":\"United States\",\"Event\":\"CPI y/y\",\"Date\":\""+eventDate+"\",\"Importance\":\"3\",\"Previous\":\"2.4\",\"Forecast\":\"2.5\",\"Actual\":"+actual+"},"
            +"{\"Country\":\"United States\",\"Event\":\"Unrelated event\",\"Date\":\""+eventDate+"\"},"
            +"{\"Country\":\"Canada\",\"Event\":\"CPI y/y\",\"Date\":\""+eventDate+"\"}]";
    }
    @Test public void missingValuesStayNullAndOnlyRelevantUsdEventsAccepted(){
        EconomicNewsEngine e=new EconomicNewsEngine();
        assertTrue(e.updateJson(json("2027-01-15T12:30:00","null"),"https://calendar.example/api",NOW));
        assertEquals(1,e.getEvents().size());
        EconomicNewsEngine.Event x=e.getEvents().get(0);
        assertEquals("CPI y/y",x.name);assertEquals(Double.valueOf(2.4),x.previous);
        assertEquals(Double.valueOf(2.5),x.forecast);assertNull(x.actual);
    }
    @Test public void rejectsNonHttpsAndMalformedCalendar(){
        EconomicNewsEngine e=new EconomicNewsEngine();
        assertFalse(e.updateJson("[]","http://bad.example",NOW));
        assertEquals(EconomicNewsEngine.DataStatus.DATA_UNAVAILABLE,e.getDataStatus());
        assertFalse(e.updateJson("not-json","https://calendar.example",NOW));
        assertEquals(EconomicNewsEngine.DataStatus.INVALID,e.getDataStatus());
    }
    @Test public void alertWindowsDeduplicateAndRequireLiveCalendar(){
        EconomicNewsEngine e=new EconomicNewsEngine();
        assertTrue(e.updateJson(json("2027-01-15T13:15:00","null"),"https://calendar.example/api",NOW));
        assertTrue(e.evaluateAlerts(NOW).isEmpty());
        long release=java.time.Instant.parse("2027-01-15T13:15:00Z").toEpochMilli();
        List<EconomicNewsEngine.Alert> a=e.evaluateAlerts(release-45*60_000L);
        assertEquals(1,a.size());assertEquals(EconomicNewsEngine.AlertKind.MINUTES_45,a.get(0).kind);
        assertTrue(e.evaluateAlerts(release-45*60_000L).isEmpty());
        assertEquals(1,e.evaluateAlerts(release-30*60_000L).size());
    }
    @Test public void staleCalendarStopsAlerts(){
        EconomicNewsEngine e=new EconomicNewsEngine();
        assertTrue(e.updateJson(json("2027-01-15T13:15:00","null"),"https://calendar.example/api",NOW));
        e.markStale(NOW+31*60_000L);
        assertEquals(EconomicNewsEngine.DataStatus.STALE,e.getDataStatus());
        assertTrue(e.evaluateAlerts(NOW+31*60_000L).isEmpty());
    }
}
