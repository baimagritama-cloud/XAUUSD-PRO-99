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

    private CandleEngine.Snapshot daily() {
        long day = Math.floorDiv(NOW, 86_400_000L) * 86_400_000L - 86_400_000L;
        List<Candle> bars = new ArrayList<>();
        bars.add(new Candle("XAUUSD", CandleTimeframe.D1, day, 100, 110, 90, 105, 0, 0,
                false, "fixture daily", NOW));
        return snapshot(bars, CandleTimeframe.D1);
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
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertEquals(IndicatorEngine.Validity.LIVE,r.validity);
        assertEquals(319d/305d*100d,r.momentum14,0.000001); // Close[last] / Close[last-14] * 100
        assertEquals(100d,r.rsi14,0.000001);
    }

    @Test public void ema200AndAtrAreCalculatedFromClosedBars() {
        List<Candle> bars=rising(220);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertNotNull(r.ema200);
        assertEquals(2d,r.atr14,0.000001);
        assertEquals(100d,r.adx14,0.000001);
        assertEquals(50d,r.plusDi14,0.000001);
        assertEquals(0d,r.minusDi14,0.000001);
    }

    @Test public void returnsNotEnoughDataInsteadOfPlaceholders() {
        List<Candle> bars=rising(120);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertEquals(IndicatorEngine.Validity.NOT_ENOUGH_DATA,r.validity);
        assertNotNull(r.rsi14);
        assertNotNull(r.momentum14);
        assertNull(r.ema200);
    }

    @Test public void ichimokuUsesCurrentClosedWindow() {
        List<Candle> bars=rising(220);
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertNotNull(r.tenkan9);
        assertNotNull(r.kijun26);
        assertNotNull(r.senkouB52);
        assertEquals((r.tenkan9+r.kijun26)/2d,r.senkouA,0.000001);
    }

    @Test public void allEmaAndIchimokuMatchLinearTrendAnalyticalValues() {
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(rising(220),CandleTimeframe.M1),daily(),NOW);
        assertEquals(314.5,r.ema10,1e-8);
        assertEquals(309.5,r.ema20,1e-8);
        assertEquals(294.5,r.ema50,1e-8);
        assertEquals(269.5,r.ema100,1e-8);
        assertEquals(219.5,r.ema200,1e-8);
        assertEquals(315d,r.tenkan9,1e-8);
        assertEquals(306.5,r.kijun26,1e-8);
        assertEquals(310.75,r.senkouA,1e-8);
        assertEquals(293.5,r.senkouB52,1e-8);
    }

    @Test public void classicDailyPivotLevelsMatchKnownOhlc() {
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(rising(220),CandleTimeframe.M1),daily(),NOW);
        assertEquals(305d/3,r.pivot,1e-8);
        assertEquals(340d/3,r.r1,1e-8);
        assertEquals(365d/3,r.r2,1e-8);
        assertEquals(400d/3,r.r3,1e-8);
        assertEquals(280d/3,r.s1,1e-8);
        assertEquals(245d/3,r.s2,1e-8);
        assertEquals(220d/3,r.s3,1e-8);
    }

    @Test public void fallingTrendHasBearishMomentumAndDirectionalMovement() {
        List<Candle> bars=new ArrayList<>();
        for(Candle b:rising(220)){
            double c=500-b.close;
            bars.add(new Candle("XAUUSD",CandleTimeframe.M1,b.openTimeMs,c+0.5,c+1,c-1,c,0,0,false,"fixture",NOW));
        }
        IndicatorEngine.Result r=new IndicatorEngine().calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertEquals(0d,r.rsi14,1e-8);
        assertEquals(181d/195d*100d,r.momentum14,1e-8);
        assertEquals(100d,r.adx14,1e-8);
        assertEquals(0d,r.plusDi14,1e-8);
        assertEquals(50d,r.minusDi14,1e-8);
        assertEquals(2d,r.atr14,1e-8);
        assertEquals(IndicatorEngine.Bias.BEARISH,r.bias);
    }

    @Test public void runningCandleCannotChangeClosedIndicatorValues() {
        List<Candle> bars=rising(220);
        IndicatorEngine engine=new IndicatorEngine();
        IndicatorEngine.Result before=engine.calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        long start=Math.floorDiv(NOW,60000L)*60000L;
        bars.add(new Candle("XAUUSD",CandleTimeframe.M1,start,319,900,310,899,0,0,true,"fixture",NOW));
        IndicatorEngine.Result after=engine.calculate(snapshot(bars,CandleTimeframe.M1),daily(),NOW);
        assertEquals(before.closedBars,after.closedBars);
        assertEquals(before.rsi14,after.rsi14);
        assertEquals(before.ema200,after.ema200);
        assertEquals(before.atr14,after.atr14);
        assertEquals(before.momentum14,after.momentum14);
    }
}
