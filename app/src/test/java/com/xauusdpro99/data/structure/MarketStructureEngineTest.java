package com.xauusdpro99.data.structure;
import com.xauusdpro99.data.candle.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MarketStructureEngineTest {
 static final long NOW=1_800_000_000_000L;
 private CandleEngine.Snapshot snap(double[][] v){List<Candle>b=new ArrayList<>();long end=Math.floorDiv(NOW,60_000L)*60_000L,start=end-v.length*60_000L;for(int i=0;i<v.length;i++)b.add(new Candle("XAUUSD",CandleTimeframe.M1,start+i*60_000L,v[i][0],v[i][1],v[i][2],v[i][3],0,0,false,"test",NOW));CandleEngine e=new CandleEngine(CandleTimeframe.M1);e.replaceFromServer(b,NOW,"TEST");return e.snapshot(NOW);}
 @Test public void pivotOnlyAppearsAfterRightSideConfirmation(){double[][]v={{10,11,9,10},{10,12,9,11},{11,15,10,12},{12,13,10,11},{11,12,9,10},{10,11,8,9},{9,10,8,9}};MarketStructureEngine.Result r=new MarketStructureEngine().analyze(snap(v),NOW);MarketStructureEngine.Swing h=null;for(MarketStructureEngine.Swing s:r.swings)if(s.type==MarketStructureEngine.SwingType.SWING_HIGH)h=s;assertNotNull(h);assertEquals(2,h.pivotIndex);assertEquals(4,h.confirmationIndex);assertTrue(h.confirmedAtMs>h.pivotTimeMs);}
 @Test public void wickOnlyBreakIsFalseBreakoutNotBos(){double[][]v={{10,11,9,10},{10,12,9,11},{11,15,10,12},{12,13,10,11},{11,12,9,10},{10,16,10,11},{11,12,9,10},{10,11,8,9}};MarketStructureEngine.Result r=new MarketStructureEngine().analyze(snap(v),NOW);boolean f=false;for(MarketStructureEngine.Event e:r.events)if(e.type==MarketStructureEngine.EventType.FALSE_BREAKOUT_UP)f=true;assertTrue(f);}
}
