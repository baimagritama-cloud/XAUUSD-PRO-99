package com.xauusdpro99.data.candle;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CandleEngineTest {
    private static final long NOW = 1_800_000_000_000L;

    private Candle candle(CandleTimeframe tf, long time, double o, double h, double l, double c, boolean open) {
        return new Candle("XAUUSD", tf, time, o, h, l, c, 0, 0, open, "test", NOW);
    }

    @Test public void rejectsInvalidOhlcAndFutureBars() {
        assertFalse(CandleEngine.isValid(candle(CandleTimeframe.M1, 1_799_999_940_000L, 10, 9, 8, 9, false), NOW));
        assertFalse(CandleEngine.isValid(candle(CandleTimeframe.M1, 1_800_000_060_000L, 10, 11, 9, 10, false), NOW));
    }

    @Test public void keepsOnlyClosedBarsAndSeparatesRunning() {
        long t = Math.floorDiv(NOW, 60_000L) * 60_000L - 120_000L;
        CandleEngine engine = new CandleEngine(CandleTimeframe.M1);
        engine.replaceFromServer(Arrays.asList(
                candle(CandleTimeframe.M1, t, 10, 12, 9, 11, false),
                candle(CandleTimeframe.M1, t + 120_000L, 11, 13, 10, 12, true)), NOW, "test");
        CandleEngine.Snapshot s = engine.snapshot(NOW);
        assertEquals(1, s.closed.size());
        assertNotNull(s.running);
    }

    @Test public void aggregationRequiresEveryM1Part() {
        long t = Math.floorDiv(NOW, 120_000L) * 120_000L - 240_000L;
        Candle a = candle(CandleTimeframe.M1, t, 10, 12, 9, 11, false);
        Candle b = candle(CandleTimeframe.M1, t + 60_000L, 11, 13, 10, 12, false);
        assertEquals(1, MultiTimeframeEngine.aggregateClosedM1(Arrays.asList(a, b),
                CandleTimeframe.M2, NOW).size());
        assertEquals(0, MultiTimeframeEngine.aggregateClosedM1(Collections.singletonList(a),
                CandleTimeframe.M2, NOW).size());
    }
}
