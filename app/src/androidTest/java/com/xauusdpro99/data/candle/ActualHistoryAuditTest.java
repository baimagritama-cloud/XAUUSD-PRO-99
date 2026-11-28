package com.xauusdpro99.data.candle;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ActualHistoryAuditTest {
    @Test public void auditActualHistory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Map<CandleTimeframe,CandleEngine.Snapshot>> result = new AtomicReference<>();
        MultiTimeframeEngine engine = new MultiTimeframeEngine(context, new MultiTimeframeEngine.Listener() {
            public void onUpdated(Map<CandleTimeframe,CandleEngine.Snapshot> snapshots) {
                result.set(snapshots); done.countDown();
            }
            public void onLog(String message) { android.util.Log.i("ActualHistoryAudit",message); }
        });
        try {
            engine.refreshAll();
            assertTrue("Production OHLC refresh timed out",done.await(240,TimeUnit.SECONDS));
            Map<CandleTimeframe,CandleEngine.Snapshot> snapshots = result.get();
            assertEquals(12,snapshots.size());
            long now = System.currentTimeMillis();
            int total=0;
            File report = new File(context.getCacheDir(),"actual-history-audit.tsv");
            try (PrintWriter out = new PrintWriter(report,"UTF-8")) {
                out.println("TF\tCLOSED\tDEFICIT\tSTATUS\tKIND\tFIRST_MS\tLAST_MS\tGAPS\tSOURCE");
                for (CandleTimeframe tf : CandleTimeframe.values()) {
                    CandleEngine.Snapshot s = snapshots.get(tf);
                    assertNotNull(s);
                    int count=s.closed.size();
                    assertTrue(count<=300);
                    long first=0,last=0;
                    for(Candle bar:s.closed) {
                        assertEquals(tf,bar.timeframe);
                        assertFalse(bar.isOpen);
                        assertTrue(CandleEngine.isValid(bar,now));
                        assertTrue(bar.openTimeMs>last);
                        if(first==0) first=bar.openTimeMs;
                        last=bar.openTimeMs;
                    }
                    if(s.lastResponseAtMs==0 || tf==CandleTimeframe.W1)
                        assertNotEquals(CandleEngine.SourceStatus.LIVE,s.sourceStatus);
                    total+=count;
                    out.println(tf+"\t"+count+"\t"+(300-count)+"\t"+s.sourceStatus+"\t"+s.kind+"\t"+first+"\t"+last+"\t"+s.gapCount+"\t"+s.source.replaceAll("[\\r\\n\\t]"," "));
                }
                out.println("TOTAL\t"+total+"\t"+(3600-total)+"\t"+(total==3600?"CAPACITY_MET":"CAPACITY_NOT_MET"));
                assertFalse(out.checkError());
            }
        } finally { engine.close(); }
    }
}
