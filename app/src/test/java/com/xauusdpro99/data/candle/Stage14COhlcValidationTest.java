package com.xauusdpro99.data.candle;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class Stage14COhlcValidationTest {
    private Candle bar(CandleTimeframe tf,long time,boolean open,double volume){
        return new Candle("XAUUSD",tf,time,2000,2003,1998,2001,volume,1,open,"TEST FIXTURE",time);
    }
    @Test public void nativeRetentionAndValidation(){
        long now=System.currentTimeMillis();
        for(CandleTimeframe tf:CandleTimeframe.values()){
            if(!tf.nativeSupported)continue;
            long end=now-now%tf.durationMs;
            while(!tf.isAligned(end))end-=60000L;
            List<Candle> input=new ArrayList<>();
            for(int i=1;i<=350;i++)input.add(bar(tf,end-i*tf.durationMs,false,1));
            input.add(input.get(0));
            input.add(bar(tf,end-tf.durationMs,false,Double.NaN));
            input.add(bar(tf,end,true,1));
            Collections.reverse(input);
            CandleEngine e=new CandleEngine(tf);
            e.replaceFromServer(input,now,"TEST FIXTURE");
            CandleEngine.Snapshot s=e.snapshot(now);
            assertEquals(tf.name(),300,s.closed.size());
            assertTrue(s.rejectedCount>=2);
            long prev=0;
            for(Candle c:s.closed){assertEquals(tf,c.timeframe);assertTrue(c.openTimeMs>prev);assertTrue(CandleEngine.isValid(c,now));assertFalse(c.isOpen);prev=c.openTimeMs;}
        }
    }
    @Test public void aggregatedRetentionWithDuplicates(){
        long end=1800000000000L;
        List<Candle> input=new ArrayList<>();
        for(int i=1;i<=999;i++)input.add(bar(CandleTimeframe.M1,end-i*60000L,false,1));
        input.add(input.get(100));
        Collections.reverse(input);
        for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.M2,CandleTimeframe.M3}){
            List<Candle> bars=MultiTimeframeEngine.aggregateClosedM1(input,tf,end);
            assertEquals(300,bars.size());
            for(Candle c:bars){assertEquals(tf,c.timeframe);assertEquals(tf.durationMs/60000L,c.tickVolume);assertTrue(CandleEngine.isValid(c,end));}
        }
    }
}
