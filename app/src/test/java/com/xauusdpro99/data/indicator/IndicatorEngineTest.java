package com.xauusdpro99.data.indicator;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import com.xauusdpro99.data.candle.CandleTimeframe;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class IndicatorEngineTest {
    private static final long NOW=1_800_000_000_000L;

    private CandleEngine.Snapshot snapshot(List<Candle> bars,CandleTimeframe tf) {
        CandleEngine e=new CandleEngine(tf);
        e.replaceFromServer(bars,NOW,"TEST FIXTURE");
        return e.snapshot(NOW);
    }

    private List<Candle> rising(int count) {
        List<Candle> bars=new ArrayList<>();
        long end=Math.floorDiv(NOW,60_000L)*60_000L;
        long start=end-count*60_000L;
        for(int i=0;i<count;i++){
            double c=100+i;
            bars.add(new Candle("XAUUSD",CandleTimeframe.M1,start+i*60_000L,c-0.5,c+1,c-1,c,0,0,false,"fixture",NOW));
        }
        return bars;
    }

    @Test public void momentumUsesMtStyle100BaselineAndRsiUsesWilder() {
        List<Candle> bars=rising(220);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),null,NOW);
        assertEquals(Validity.LIVE,r.validity);
        assertEquals(100d+219d/100d*100d,r.momentum14,0.000001); // close[219] / close[205] * 100
        assertEquals(100d,r.rsi14,0.000001);
    }

    @Test public void ema200AndAtrAreCalculatedFromClosedBars() {
        List<Candle> bars=rising(220);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),null,NOW);
        assertNotNull(r.ema200);
        assertEquals(3d,r.atr14,0.000001);
        assertEquals(100d,r.adx14,0.000001);
        assertEquals(100d,r.plusDi14,0.000001);
        assertEquals(0d,r.minusDi14,0.000001);
    }

    @Test public void returnsNotEnoughDataInsteadOfPlaceholders() {
        List<Candle> bars=rising(120);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),null,NOW);
        assertEquals(Validity.NOT_ENOUGH_DATA,r.validity);
        assertNull(r.rsi14);
        assertNull(r.ema200);
    }

    @Test public void ichimokuUsesCurrentClosedWindow() {
        List<Candle> bars=rising(220);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),null,NOW);
        assertNotNull(r.tenkan9);
        assertNotNull(r.kijun26);
        assertNotNull(r.senkouB52);
        assertEquals((r.tenkan9+r.kijun26)/2d,r.senkouA,0.000001);
    }
}
