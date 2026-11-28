package com.xauusdpro99.data.candle;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryRegressionTest {
    private static final long MONDAY = 1704067200000L;
    private static final long NOW = MONDAY + 400L * CandleTimeframe.W1.durationMs;
    private Candle bar(CandleTimeframe tf, long t, double close, long received, String source, long ticks) {
        return new Candle("XAUUSD", tf, t, 2000, 2010, 1990, close, 1, ticks, false, source, received);
    }
    private List<Candle> series(CandleTimeframe tf, int count) {
        List<Candle> bars = new ArrayList<>();
        for (int i=0; i<count; i++) bars.add(bar(tf, MONDAY+i*tf.durationMs, 2001, NOW, "BiQuote OHLC SQLite", 2));
        return bars;
    }
    @Test public void derivedCapsAndOhlc() {
        for (CandleTimeframe tf : new CandleTimeframe[]{CandleTimeframe.M2,CandleTimeframe.M3,CandleTimeframe.H8,CandleTimeframe.H12}) {
            CandleTimeframe base = tf.isAggregatedFromM1()?CandleTimeframe.M1:CandleTimeframe.H4;
            int n = (int)(tf.durationMs/base.durationMs);
            List<Candle> input = series(base,301*n);
            List<Candle> out = tf.isAggregatedFromM1()?MultiTimeframeEngine.aggregateClosedM1(input,tf,NOW):MultiTimeframeEngine.aggregateClosedH4(input,tf,NOW);
            assertEquals(tf.name(),300,out.size());
            assertEquals(MONDAY+tf.durationMs,out.get(0).openTimeMs);
            assertEquals(2000,out.get(0).open,0);
            assertEquals(2010,out.get(0).high,0);
            assertEquals(1990,out.get(0).low,0);
            assertEquals(2001,out.get(0).close,0);
            assertEquals(n,out.get(0).volume,0);
            assertEquals(2L*n,out.get(0).tickVolume);
        }
    }
    @Test public void incompleteAndOverflowGroupsRejected() {
        List<Candle> m = series(CandleTimeframe.M1,3);
        m.remove(1);
        assertTrue(MultiTimeframeEngine.aggregateClosedM1(m,CandleTimeframe.M3,NOW).isEmpty());
        List<Candle> h = series(CandleTimeframe.H4,3);
        h.remove(1);
        assertTrue(MultiTimeframeEngine.aggregateClosedH4(h,CandleTimeframe.H12,NOW).isEmpty());
        m = series(CandleTimeframe.M1,2);
        m.set(0,bar(CandleTimeframe.M1,MONDAY,2001,NOW,"BiQuote OHLC REST",Long.MAX_VALUE));
        assertTrue(MultiTimeframeEngine.aggregateClosedM1(m,CandleTimeframe.M2,NOW).isEmpty());
    }
    @Test public void weeklyCapAndConservativeSession() {
        List<Candle> days = new ArrayList<>();
        for(int w=0;w<301;w++) for(int d=0;d<5;d++)
            days.add(bar(CandleTimeframe.D1,MONDAY+w*CandleTimeframe.W1.durationMs+d*CandleTimeframe.D1.durationMs,2001,NOW,"BiQuote OHLC SQLite",2));
        List<Candle> weeks = MultiTimeframeEngine.aggregateClosedD1(days,NOW);
        assertEquals(300,weeks.size());
        assertEquals(MONDAY+CandleTimeframe.W1.durationMs,weeks.get(0).openTimeMs);
        List<Candle> one = new ArrayList<>(days.subList(0,5));
        assertTrue(MultiTimeframeEngine.aggregateClosedD1(one,MONDAY+5*CandleTimeframe.D1.durationMs).isEmpty());
        one.add(bar(CandleTimeframe.D1,MONDAY+5*CandleTimeframe.D1.durationMs,2001,NOW,"BiQuote OHLC REST",2));
        assertTrue(MultiTimeframeEngine.aggregateClosedD1(one,NOW).isEmpty());
        one.remove(5); one.remove(2);
        assertTrue(MultiTimeframeEngine.aggregateClosedD1(one,NOW).isEmpty());
        CandleEngine engine = new CandleEngine(CandleTimeframe.W1);
        engine.replaceAggregated(weeks,NOW,"historical",NOW);
        assertEquals(0L,engine.snapshot(NOW).lastResponseAtMs);
        assertEquals(CandleEngine.SourceStatus.STALE,engine.snapshot(NOW).sourceStatus);
    }
    @Test public void cacheProvenanceAndRevision() {
        CandleTimeframe tf = CandleTimeframe.M1;
        long t = NOW-tf.durationMs;
        Candle newer = bar(tf,t,2005,NOW,"BiQuote OHLC SQLite",2);
        Candle older = bar(tf,t,2002,NOW-1,"BiQuote OHLC REST",2);
        Candle bad = bar(tf,t,3000,NOW,"BiQuote OHLC REST",2);
        Candle wrongSource = bar(tf,t,2007,NOW,"other",2);
        assertFalse(CandleEngine.isValid(bad,NOW));
        assertEquals(1,MultiTimeframeEngine.mergeM1History(Arrays.asList(newer,older,bad,wrongSource),NOW).size());
        CandleEngine engine = new CandleEngine(tf);
        engine.restoreNativeHistory(Arrays.asList(newer),NOW);
        assertEquals(CandleEngine.SourceStatus.STALE,engine.snapshot(NOW).sourceStatus);
        engine.replaceFromServer(Arrays.asList(older),NOW,"REST");
        assertEquals(2005,engine.snapshot(NOW).closed.get(0).close,0);
        engine.markUnavailable("offline");
        assertEquals(CandleEngine.SourceStatus.STALE,engine.snapshot(NOW).sourceStatus);
    }
}
