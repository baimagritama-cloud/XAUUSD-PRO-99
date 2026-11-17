package com.xauusdpro99.data.signal;

import com.xauusdpro99.data.candle.*;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.structure.*;
import java.util.*;

/** Human-readable conditional market narrative derived from actual quote, candles and structural levels. */
public final class MarketJourneyEngine {
 public enum PriceState{BREAKOUT,RETEST,REJECTION,CONSOLIDATION,UNKNOWN}
 public enum Safety{SAFE,CAUTION,UNKNOWN}
 public static final class Input{
  public final double price,bid,ask,spread;public final boolean live,valid;public final long received,now;
  public final Map<CandleTimeframe,CandleEngine.Snapshot> candles;
  public final Map<CandleTimeframe,IndicatorEngine.Result> indicators;
  public final Map<CandleTimeframe,MarketStructureEngine.Result> structures;
  public final Map<CandleTimeframe,SupplyDemandEngine.Result> zones;
  public final Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> patterns;
  public Input(double p,double b,double a,double s,boolean l,boolean v,long r,long n,
   Map<CandleTimeframe,CandleEngine.Snapshot> c,Map<CandleTimeframe,IndicatorEngine.Result> i,
   Map<CandleTimeframe,MarketStructureEngine.Result> m,Map<CandleTimeframe,SupplyDemandEngine.Result> z,
   Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> ps){
   price=p;bid=b;ask=a;spread=s;live=l;valid=v;received=r;now=n;candles=c;indicators=i;structures=m;zones=z;patterns=ps;
  }
 }
 public static final class Level{
  public final String label,timeframe,type;public final double lower,upper,distance;
  Level(String l,String t,String y,double lo,double hi,double p){label=l;timeframe=t;type=y;lower=lo;upper=hi;distance=p<lo?lo-p:p>hi?p-hi:0;}
 }
 public static final class Result{
  public final boolean dataValid;public final double currentPrice;public final String mainDirection,position,nearestDemand,nearestSupply,nextDestination,safeArea,cautionArea,cancelBullish,cancelBearish;
  public final PriceState state;public final Safety safety;public final List<Level> nearbyLevels;public final List<String> bullishScenario,bearishScenario,reversalNotes;
  Result(boolean v,double p,String d,String pos,PriceState st,Safety sf,String dem,String sup,String dest,String safe,String caution,String cb,String cr,List<Level> lv,List<String> bull,List<String> bear,List<String> rev){
   dataValid=v;currentPrice=p;mainDirection=d;position=pos;state=st;safety=sf;nearestDemand=dem;nearestSupply=sup;nextDestination=dest;
   safeArea=safe;cautionArea=caution;cancelBullish=cb;cancelBearish=cr;nearbyLevels=Collections.unmodifiableList(lv);
   bullishScenario=Collections.unmodifiableList(bull);bearishScenario=Collections.unmodifiableList(bear);reversalNotes=Collections.unmodifiableList(rev);
  }
 }
 public Result analyze(Input x){
  List<Level> lv=new ArrayList<>();List<String> bull=new ArrayList<>(),bear=new ArrayList<>(),rev=new ArrayList<>();
  if(x==null||!x.live||!x.valid||!pos(x.price)||!pos(x.bid)||!pos(x.ask)||x.ask<x.bid||x.received<=0||x.now<x.received||x.now-x.received>15000)
   return new Result(false,Double.NaN,"UNKNOWN","Quote tidak valid",PriceState.UNKNOWN,Safety.UNKNOWN,"N/A","N/A","N/A","Belum dapat dinilai","Belum dapat dinilai","Tidak tersedia","Tidak tersedia",lv,bull,bear,rev);
  Level dem=zone(x,SupplyDemandEngine.ZoneType.DEMAND),sup=zone(x,SupplyDemandEngine.ZoneType.SUPPLY);
  Level support=swing(x,false),resistance=swing(x,true);
  if(dem!=null)lv.add(dem);if(sup!=null)lv.add(sup);if(support!=null)lv.add(support);if(resistance!=null)lv.add(resistance);
  lv.sort(Comparator.comparingDouble(a->a.distance));
  String dir=direction(x);PriceState state=state(x,dem,sup);
  double atr=atr(x),near=pos(atr)?Math.max(atr*.5,x.spread*2):Double.NaN;
  boolean inside=(dem!=null&&dem.distance==0)||(sup!=null&&sup.distance==0);
  Safety safety=!pos(near)||lv.isEmpty()?Safety.UNKNOWN:inside||lv.get(0).distance<=near?Safety.CAUTION:Safety.SAFE;
  String pos="Harga "+f(x.price)+"; "+(dem!=null&&dem.distance==0?"di dalam Demand "+dem.timeframe:sup!=null&&sup.distance==0?"di dalam Supply "+sup.timeframe:"di luar zona terdekat");
  String safe=lv.isEmpty()?"Belum ada level valid untuk menilai area aman":"Relatif lebih aman bila berjarak > "+f(near)+" dari level terdekat; bukan jaminan";
  String caution=inside?"WASPADA: harga berada di dalam zona aktif":safety==Safety.CAUTION?"WASPADA: harga dekat level struktural":"Waspadai spread/volatilitas dan reaksi pada level terdekat";
  Level destination=nextOpposingZone(x,dir); if(destination==null)destination=dir.equals("BULLISH")?resistance:dir.equals("BEARISH")?support:null; String dest=describe(destination);
  Candle m5=last(x,CandleTimeframe.M5);
  if(dem!=null){bull.add("Demand "+dem.timeframe+" berada pada "+f(dem.lower)+"–"+f(dem.upper)+".");
   if(m5!=null)bull.add("Skenario BUY: tunggu candle M5 close di atas high candle konfirmasi aktual "+f(m5.high)+" dan retest bertahan; tujuan teknis berikutnya "+dest+".");}
  if(sup!=null){bear.add("Supply "+sup.timeframe+" berada pada "+f(sup.lower)+"–"+f(sup.upper)+".");
   if(m5!=null)bear.add("Skenario SELL: tunggu candle M5 close di bawah low candle konfirmasi aktual "+f(m5.low)+" dan retest gagal; tujuan teknis berikutnya "+dest+".");}
  String cb=dem==null?"Level invalidasi bullish belum tersedia":"Bullish batal jika candle tertutup menembus Demand bawah "+f(dem.lower)+" lalu retest dari bawah gagal.";
  String cr=sup==null?"Level invalidasi bearish belum tersedia":"Bearish batal jika candle tertutup menembus Supply atas "+f(sup.upper)+" lalu retest dari atas gagal.";
  if(dem!=null&&dem.distance==0)rev.add("Potensi reversal bullish: tunggu rejection dan close M5/M15 terkonfirmasi; jangan entry hanya karena harga menyentuh Demand.");
  if(sup!=null&&sup.distance==0)rev.add("Potensi reversal bearish: tunggu rejection dan close M5/M15 terkonfirmasi; jangan entry hanya karena harga menyentuh Supply.");
  if(rev.isEmpty())rev.add("Belum ada reversal pada zona aktif yang terkonfirmasi.");
  return new Result(true,x.price,dir,pos,state,safety,describe(dem),describe(sup),dest,safe,caution,cb,cr,lv,bull,bear,rev);
 }
 private static PriceState state(Input x,Level dem,Level sup){
  MarketStructureEngine.Result m=x.structures==null?null:x.structures.get(CandleTimeframe.M5);
  if(m!=null&&!m.events.isEmpty()){MarketStructureEngine.EventType t=m.events.get(m.events.size()-1).type;
   if(t==MarketStructureEngine.EventType.BOS_BULLISH||t==MarketStructureEngine.EventType.BOS_BEARISH)return PriceState.BREAKOUT;
   if(t==MarketStructureEngine.EventType.FALSE_BREAKOUT_UP||t==MarketStructureEngine.EventType.FALSE_BREAKOUT_DOWN)return PriceState.REJECTION;}
  if(x.zones!=null)for(SupplyDemandEngine.Result z:x.zones.values())if(z!=null&&!z.breakouts.isEmpty()){
   SupplyDemandEngine.Breakout b=z.breakouts.get(z.breakouts.size()-1);
   if(b.retest==SupplyDemandEngine.RetestStatus.RETESTED_REJECTED)return PriceState.REJECTION;
   if(b.retest==SupplyDemandEngine.RetestStatus.RETESTED_ACCEPTED)return PriceState.RETEST;
   if(b.type==SupplyDemandEngine.BreakoutType.VALID_UP||b.type==SupplyDemandEngine.BreakoutType.VALID_DOWN)return PriceState.BREAKOUT;}
  if(dem!=null&&dem.distance==0||sup!=null&&sup.distance==0)return PriceState.RETEST;
  return m!=null&&m.trend==MarketStructureEngine.Trend.CONSOLIDATION?PriceState.CONSOLIDATION:PriceState.UNKNOWN;
 }
 private static String direction(Input x){int b=0,s=0;if(x.structures!=null)for(CandleTimeframe t:new CandleTimeframe[]{CandleTimeframe.W1,CandleTimeframe.D1,CandleTimeframe.H4}){
  MarketStructureEngine.Result r=x.structures.get(t);if(r!=null&&r.validity.startsWith("VALID")){if(r.trend==MarketStructureEngine.Trend.BULLISH)b++;if(r.trend==MarketStructureEngine.Trend.BEARISH)s++;}}
  if(b>s)return "BULLISH";if(s>b)return "BEARISH";return "MIXED / UNKNOWN";}

 private static Level nextOpposingZone(Input x,String dir){
  if(x.zones==null)return null;Level best=null;
  for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null&&r.validity.startsWith("VALID"))
   for(SupplyDemandEngine.Zone z:r.activeZones()){
    boolean valid=dir.equals("BULLISH")?z.type==SupplyDemandEngine.ZoneType.SUPPLY&&z.lower>x.price:
      dir.equals("BEARISH")&&z.type==SupplyDemandEngine.ZoneType.DEMAND&&z.upper<x.price;
    if(!valid)continue;Level n=new Level(z.label,z.timeframe,z.type.name(),z.lower,z.upper,x.price);
    if(best==null||n.distance<best.distance)best=n;
   }
  return best;
 }
 private static Level zone(Input x,SupplyDemandEngine.ZoneType type){Level best=null;if(x.zones!=null)for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null&&r.validity.startsWith("VALID"))
  for(SupplyDemandEngine.Zone z:r.activeZones())if(z.type==type){Level n=new Level(z.label,z.timeframe,type.name(),z.lower,z.upper,x.price);if(best==null||n.distance<best.distance)best=n;}return best;}
 private static Level swing(Input x,boolean high){Level best=null;if(x.structures!=null)for(Map.Entry<CandleTimeframe,MarketStructureEngine.Result> it:x.structures.entrySet()){
  MarketStructureEngine.Result r=it.getValue();if(r==null||!r.validity.startsWith("VALID"))continue;MarketStructureEngine.Swing s=high?r.lastSwingHigh:r.lastSwingLow;
  if(s==null||high&&s.price<=x.price||!high&&s.price>=x.price)continue;Level n=new Level((high?"Swing High ":"Swing Low ")+s.label,it.getKey().name(),high?"RESISTANCE":"SUPPORT",s.price,s.price,x.price);if(best==null||n.distance<best.distance)best=n;}return best;}
 private static Candle last(Input x,CandleTimeframe tf){CandleEngine.Snapshot s=x.candles==null?null:x.candles.get(tf);return s==null||s.sourceStatus!=CandleEngine.SourceStatus.LIVE||s.closed.isEmpty()?null:s.closed.get(s.closed.size()-1);}
 private static double atr(Input x){IndicatorEngine.Result r=x.indicators==null?null:x.indicators.get(CandleTimeframe.M5);return r==null||r.validity!=IndicatorEngine.Validity.LIVE||r.atr14==null?Double.NaN:r.atr14;}
 private static String describe(Level l){return l==null?"Belum tersedia":l.label+" "+l.timeframe+" ["+f(l.lower)+"–"+f(l.upper)+"]";}
 private static String f(double d){return Double.isFinite(d)?String.format(Locale.US,"%.2f",d):"N/A";}
 private static boolean pos(double d){return Double.isFinite(d)&&d>0;}
}
