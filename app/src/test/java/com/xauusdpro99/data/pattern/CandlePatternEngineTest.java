package com.xauusdpro99.data.pattern;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import com.xauusdpro99.data.candle.CandleTimeframe;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CandlePatternEngineTest {
    private static final long NOW=1_800_000_000_000L;
    private List<Candle> make(double[][] v){
        List<Candle> out=new ArrayList<>();long end=Math.floorDiv(NOW,60_000L)*60_000L,start=end-v.length*60_000L;
        for(int i=0;i<v.length;i++)out.add(new Candle("XAUUSD",CandleTimeframe.M1,start+i*60_000L,v[i][0],v[i][1],v[i][2],v[i][3],0,0,false,"test",NOW));
        return out;
    }
    private CandleEngine.Snapshot snapshot(List<Candle> bars){CandleEngine e=new CandleEngine(CandleTimeframe.M1);e.replaceFromServer(bars,NOW,"UNIT TEST");return e.snapshot(NOW);}
    private CandlePatternEngine.Pattern find(List<CandlePatternEngine.Pattern> ps,String name){for(CandlePatternEngine.Pattern p:ps)if(p.name.equals(name))return p;return null;}
    private double[][] flat15(){double[][] v=new double[15][4];for(int i=0;i<15;i++){v[i]=new double[]{100,101,99,100.2};}return v;}

    @Test public void bullishEngulfingIsDetectedOnClosedBars(){
        List<Candle> bars=make(new double[][]{{105,106,99,100},{99,108,98,107}});
        CandlePatternEngine.Pattern p=find(new CandlePatternEngine().detect(snapshot(bars),null,NOW),"Bullish Engulfing");
        assertNotNull(p);assertEquals(CandlePatternEngine.Direction.BULLISH,p.direction);
        assertEquals(CandlePatternEngine.Status.DETECTED,p.status);assertEquals(107d,p.formationPrice,0.000001);
    }
    @Test public void hammerUsesWickBodyAndAtrGeometry(){
        double[][] v=flat15();List<double[]> all=new ArrayList<>();for(double[] x:v)all.add(x);
        all.add(new double[]{100,101.1,98.4,100.8});
        List<Candle> bars=make(all.toArray(new double[0][0]));
        CandlePatternEngine.Pattern p=find(new CandlePatternEngine().detect(snapshot(bars),null,NOW),"Hammer");
        assertNotNull(p);assertTrue(p.lowerWick>=2*p.body);assertEquals(CandlePatternEngine.Status.DETECTED,p.status);
    }
    @Test public void demandZoneConfirmsLocationButNotEntry(){
        List<Candle> bars=make(new double[][]{{105,106,99,100},{99,108,98,107}});
        CandlePatternEngine.PriceZone zone=new CandlePatternEngine.PriceZone(CandlePatternEngine.ZoneType.DEMAND,97,101,"DEMAND M15");
        CandlePatternEngine.Context ctx=new CandlePatternEngine.Context(null,null,java.util.Collections.singletonList(zone));
        CandlePatternEngine.Pattern p=find(new CandlePatternEngine().detect(snapshot(bars),ctx,NOW),"Bullish Engulfing");
        assertNotNull(p);assertEquals(CandlePatternEngine.Status.LOCATION_CONFIRMED,p.status);assertEquals("DEMAND M15",p.location);
    }
    @Test public void followingCloseConfirmsOrInvalidates(){
        List<Candle> confirm=make(new double[][]{{105,106,99,100},{99,108,98,107},{107,110,106,109}});
        CandlePatternEngine.Pattern p=find(new CandlePatternEngine().detect(snapshot(confirm),null,NOW),"Bullish Engulfing");
        assertNotNull(p);assertEquals(CandlePatternEngine.Status.ENTRY_CONFIRMED,p.status);
        List<Candle> invalid=make(new double[][]{{105,106,99,100},{99,108,98,107},{107,108,96,97}});
        CandlePatternEngine.Pattern q=find(new CandlePatternEngine().detect(snapshot(invalid),null,NOW),"Bullish Engulfing");
        assertNotNull(q);assertEquals(CandlePatternEngine.Status.INVALIDATED,q.status);
    }
    @Test public void runningAndUnavailableCandlesAreNotUsed(){
        List<Candle> bars=make(new double[][]{{105,106,99,100},{99,108,98,107}});
        bars.add(new Candle("XAUUSD",CandleTimeframe.M1,bars.get(1).openTimeMs+60_000L,107,111,106,110,0,0,true,"test",NOW));
        assertNotNull(find(new CandlePatternEngine().detect(snapshot(bars),null,NOW),"Bullish Engulfing"));
        CandleEngine empty=new CandleEngine(CandleTimeframe.M1);
        assertTrue(new CandlePatternEngine().detect(empty.snapshot(NOW),null,NOW).isEmpty());
    }
    @Test public void malformedOhlcIsRejected(){
        List<Candle> bars=make(new double[][]{{105,106,99,100},{99,108,98,107}});
        Candle c=bars.get(1);bars.set(1,new Candle("XAUUSD",CandleTimeframe.M1,c.openTimeMs,99,97,98,107,0,0,false,"bad",NOW));
        assertTrue(new CandlePatternEngine().detect(snapshot(bars),null,NOW).isEmpty());
    }
}
