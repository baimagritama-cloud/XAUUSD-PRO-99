package com.xauusdpro99.data.candle;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CandleStoreRecoveryTest {
    private Candle bar(long start, double close, long received, boolean open) {
        return new Candle("XAUUSD", CandleTimeframe.M1, start, 2000, 2010, 1990,
                close, 1, 2, open, "BiQuote OHLC REST", received);
    }

    @Test public void restoresClosedHistoryWithoutLiveFeed() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "ohlc_recovery_test.db";
        context.deleteDatabase(name);
        long now = 1800000000000L;
        long start = now - 301 * 60000L;
        try {
            try (CandleStore store = new CandleStore(context, name)) {
                List<Candle> bars = new ArrayList<>();
                for (int i = 0; i < 301; i++) bars.add(bar(start + i * 60000L, 2001, now, false));
                store.saveNative(CandleTimeframe.M1, bars, now);
                store.saveNative(CandleTimeframe.M1, Arrays.asList(
                        bar(start + 300 * 60000L, 2005, now + 1, false),
                        bar(now, 2001, now + 1, true),
                        bar(start - 60000L, 3000, now + 1, false)), now + 1);
                store.saveNative(CandleTimeframe.M1, Arrays.asList(bar(start + 300 * 60000L, 2002, now, false)), now + 1);
            }
            try (CandleStore reopened = new CandleStore(context, name)) {
                List<Candle> history = reopened.loadNative(CandleTimeframe.M1, 1000, now + 1);
                assertEquals(301, history.size());
                assertEquals(2005, history.get(300).close, 0.0);
                assertEquals(now + 1, history.get(300).receivedAtMs);
                CandleEngine engine = new CandleEngine(CandleTimeframe.M1);
                engine.restoreNativeHistory(history, now + 1);
                assertEquals(300, engine.snapshot(now + 1).closed.size());
                assertEquals(CandleEngine.SourceStatus.STALE, engine.snapshot(now + 1).sourceStatus);
                assertEquals(0L, engine.snapshot(now + 1).lastResponseAtMs);
                engine.markUnavailable("offline");
                assertEquals(300, engine.snapshot(now + 1).closed.size());
                assertEquals(CandleEngine.SourceStatus.STALE, engine.snapshot(now + 1).sourceStatus);
            }
        } finally { context.deleteDatabase(name); }
    }
}
