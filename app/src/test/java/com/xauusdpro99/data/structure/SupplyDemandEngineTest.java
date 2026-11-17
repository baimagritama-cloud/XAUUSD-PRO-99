package com.xauusdpro99.data.structure;
import com.xauusdpro99.data.candle.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SupplyDemandEngineTest {
 static final long NOW=1_800_000_000_000L;
 private CandleEngine.Snapshot snap(double[][]v){List<Candle>b=new ArrayList<>();long end=Math.floorDiv(NOW,60_000L)*60_000L,start=end-v.length*60_000L;for(int i=0;i<v.length;i++)b.add(new Candle("XAUUSD",CandleTimeframe.M1,start+i*60_000L,v[i][0],v[i][1],v[i][2],v[i][3],0,0,false,"test",NOW));CandleEngine e=new CandleEngine(CandleTimeframe.M1);e.replaceFromServer(b,NOW,"TEST");return e.snapshot(NOW);}
 @Test public void detectsDemandFromBaseAndBullishDisplacement(){double[][]v=new double[20][4];for(int i=0;i<20;i++)v[i]=new double[]{100,101,99,100};v[15]=new double[]{100,101,99,100};v[16]=new double[]{100,101,99,100};v[17]=new double[]{100,105,100,104};v[18]=new double[]{104,106,103,105};v[19]=new double[]{105,106,104,105};SupplyDemandEngine.Result r=new SupplyDemandEngine().analyze(snap(v),105,NOW);boolean found=false;for(SupplyDemandEngine.Zone z:r.zones)if(z.type==SupplyDemandEngine.ZoneType.DEMAND){found=true;assertTrue(z.upper>=z.lower);assertEquals("M1",z.timeframe);}assertTrue(found);}
 @Test public void timeframePaletteIsStable(){assertEquals("#8000FF",SupplyDemandEngine.timeframeColor("H4",SupplyDemandEngine.ZoneType.DEMAND));assertEquals("#1677FF",SupplyDemandEngine.timeframeColor("D1",SupplyDemandEngine.ZoneType.SUPPLY));assertEquals("#FF4FA3",SupplyDemandEngine.timeframeColor("W1",SupplyDemandEngine.ZoneType.DEMAND));}
}
