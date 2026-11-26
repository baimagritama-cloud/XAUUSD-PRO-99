package com.xauusdpro99.data.signal;

import com.xauusdpro99.data.candle.*;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.structure.*;
import java.util.*;

/** Dynamic entry/SL/TP planner. Every price level must originate from validated market data. */
public final class EntryRiskEngine {
 public static final double MIN_RR=1.5,MAX_STOP_ATR=2.5;
 public static final class Input {
  public final SignalScoringEngine.Result scored;
  public final Map<CandleTimeframe,CandleEngine.Snapshot> candles;
  public final Map<CandleTimeframe,IndicatorEngine.Result> indicators;
  public final Map<CandleTimeframe,MarketStructureEngine.Result> structures;
  public final Map<CandleTimeframe,SupplyDemandEngine.Result> zones;
  public final Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> patterns;
  public final double bid,ask,spread,currentPrice; public final long receivedAt,now;
  public final boolean live,quoteValid;
  public Input(SignalScoringEngine.Result s,Map<CandleTimeframe,CandleEngine.Snapshot> c,
   Map<CandleTimeframe,IndicatorEngine.Result> i,Map<CandleTimeframe,MarketStructureEngine.Result> m,
   Map<CandleTimeframe,SupplyDemandEngine.Result> z,Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> p,
   double bid,double ask,double spread,double price,long received,long now,boolean live,boolean valid){
   scored=s;candles=c;indicators=i;structures=m;zones=z;patterns=p;this.bid=bid;this.ask=ask;
   this.spread=spread;currentPrice=price;receivedAt=received;this.now=now;this.live=live;quoteValid=valid;
  }
 }
 public static final class Target {
  public final String label,timeframe,source; public final double price,rr;
  Target(String l,String tf,String s,double p,double r){label=l;timeframe=tf;source=s;price=p;rr=r;}
 }
 public static final class Result {
  public final boolean valid;public final String summary,pattern,timeframe;
  public final SignalScoringEngine.Direction direction;public final double entry,sl,risk,atr,buffer,spread,rr;
  public final List<Target> targets;public final List<String> accepted,rejected;
  Result(boolean v,String sum,SignalScoringEngine.Direction d,String p,String tf,double e,double sl,double risk,
   double atr,double buf,double sp,double rr,List<Target> t,List<String>a,List<String>r){
   valid=v;summary=sum;direction=d;pattern=p;timeframe=tf;entry=e;this.sl=sl;this.risk=risk;this.atr=atr;
   buffer=buf;spread=sp;this.rr=rr;targets=Collections.unmodifiableList(t);
   accepted=Collections.unmodifiableList(a);rejected=Collections.unmodifiableList(r);
  }
 }
 private static final class Level {double price;String tf,source;Level(double p,String t,String s){price=p;tf=t;source=s;}}
 public Result calculate(Input x){
  List<String> ok=new ArrayList<>(),bad=new ArrayList<>();List<Target> tps=new ArrayList<>();
  if(x==null||x.scored==null)return result(false,null,"N/A","N/A",Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,tps,ok,Collections.singletonList("Input tidak tersedia"));
  SignalScoringEngine.Result s=x.scored;SignalScoringEngine.Direction d=s.direction;
  boolean entryStatus=s.status==(d==SignalScoringEngine.Direction.BUY?SignalScoringEngine.Status.BUY:SignalScoringEngine.Status.SELL);
  if(!entryStatus)bad.add("Scoring engine belum menerbitkan BUY/SELL: "+s.status);
  if(s.totalScore<57)bad.add("Skor di bawah 57");
  if(!x.live||!x.quoteValid||!pos(x.bid)||!pos(x.ask)||x.ask<x.bid||!pos(x.currentPrice)
    ||x.receivedAt<=0||x.now<x.receivedAt||x.now-x.receivedAt>15000)bad.add("Quote tidak LIVE/valid atau lebih tua 15 detik");
  if(!pos(x.spread)||x.spread>SignalScoringEngine.DEFAULT_MAX_SPREAD)bad.add("Spread tidak layak");
  CandlePatternEngine.Pattern p=findPattern(x,s);
  if(p==null)bad.add("Pola entry tidak ditemukan pada M1/M2/M3/M5");
  else if(p.status!=CandlePatternEngine.Status.ENTRY_CONFIRMED)bad.add("Pola belum ENTRY_CONFIRMED");
  else ok.add("Pola "+p.name+" terkonfirmasi pada "+p.timeframe);
  boolean main=mainTrend(x,d),reversal=hasChoch(x,d);
  if(main||reversal)ok.add(main?"Arah utama mendukung":"CHOCH reversal terkonfirmasi");else bad.add("Arah utama/CHOCH belum mendukung");
  boolean loc=location(x,d,p);if(loc)ok.add("Demand/Support atau Supply/Resistance valid");else bad.add("Lokasi entry tidak valid");
  boolean react=p!=null&&p.status==CandlePatternEngine.Status.ENTRY_CONFIRMED
    &&(d==SignalScoringEngine.Direction.BUY?p.direction==CandlePatternEngine.Direction.BULLISH&&p.closePosition>=.55
    :p.direction==CandlePatternEngine.Direction.BEARISH&&p.closePosition<=.45);
  if(react)ok.add("Reaksi candle searah terkonfirmasi");else bad.add("Reaksi candle belum terkonfirmasi");
  if(structure(x,d)||reversal)ok.add("Struktur utama dan timing mendukung");else bad.add("Struktur belum mendukung");
  if(indicator(x,d))ok.add("Indikator/DI/momentum mendukung");else bad.add("Indikator belum mendukung");
  double e=d==SignalScoringEngine.Direction.BUY?x.ask:x.bid,atr=atr(x,p);
  double buffer=pos(atr)?Math.max(x.spread*1.5,atr*.15):Double.NaN;
  if(!pos(atr))bad.add("ATR14 entry timeframe tidak tersedia");
  double sl=stop(x,d,p,e,buffer),risk=d==SignalScoringEngine.Direction.BUY?e-sl:sl-e;
  if(!pos(sl)||!pos(risk))bad.add("Swing/zone invalidation untuk SL tidak tersedia");else ok.add("SL memakai swing/zone invalidation + ATR/spread buffer");
  double cap=pos(atr)?Math.max(atr*MAX_STOP_ATR,x.spread*3):Double.NaN;
  if(pos(risk)&&pos(cap)&&risk<=cap)ok.add("Jarak SL sesuai volatilitas");else bad.add("Jarak SL melebihi batas ATR/spread");
  if(pos(risk))tps.addAll(targets(x,d,e,risk));
  double rr=tps.isEmpty()?Double.NaN:tps.get(0).rr;
  if(!tps.isEmpty())ok.add("TP berdasarkan level teknis; TP1 RR "+f(rr));else bad.add("Tidak ada target teknis dengan RR minimum 1:1.5");
  boolean valid=bad.isEmpty()&&entryStatus&&s.totalScore>=57&&pos(risk)&&risk<=cap&&!tps.isEmpty()&&rr>=MIN_RR;
  return result(valid,d,p==null?s.patternName:p.name,p==null?"N/A":p.timeframe,e,sl,risk,atr,buffer,x.spread,rr,tps,ok,bad);
 }
 private Result result(boolean v,SignalScoringEngine.Direction d,String p,String tf,double e,double sl,double risk,double atr,double b,double sp,double rr,List<Target> t,List<String>ok,List<String>bad){
  return new Result(v,v?"ENTRY PLAN VALID":"ENTRY PLAN BLOCKED — jangan entry",d,p,tf,e,sl,risk,atr,b,sp,rr,t,ok,bad);
 }
 private static CandlePatternEngine.Pattern findPattern(Input x,SignalScoringEngine.Result s){
  if(x.patterns==null)return null;CandlePatternEngine.Pattern best=null;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.M1,CandleTimeframe.M2,CandleTimeframe.M3,CandleTimeframe.M5}){
   List<CandlePatternEngine.Pattern> ps=x.patterns.get(tf);if(ps==null)continue;
   for(CandlePatternEngine.Pattern p:ps)if(p.name.equals(s.patternName)&&
    p.direction==(s.direction==SignalScoringEngine.Direction.BUY?CandlePatternEngine.Direction.BULLISH:CandlePatternEngine.Direction.BEARISH)
    &&(best==null||p.timestampMs>best.timestampMs))best=p;
  }return best;
 }
 private static boolean mainTrend(Input x,SignalScoringEngine.Direction d){
  if(x.structures==null)return false;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.W1,CandleTimeframe.D1,CandleTimeframe.H4}){
   MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity.startsWith("VALID")&&trend(r.trend,d))return true;
  }return false;
 }
 private static boolean hasChoch(Input x,SignalScoringEngine.Direction d){
  if(x.structures==null)return false;MarketStructureEngine.EventType wanted=d==SignalScoringEngine.Direction.BUY?MarketStructureEngine.EventType.CHOCH_BULLISH:MarketStructureEngine.EventType.CHOCH_BEARISH;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.H4,CandleTimeframe.D1,CandleTimeframe.H1,CandleTimeframe.M30,CandleTimeframe.M15}){
   MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity.startsWith("VALID"))for(MarketStructureEngine.Event e:r.events)if(e.type==wanted)return true;
  }return false;
 }
 private static boolean location(Input x,SignalScoringEngine.Direction d,CandlePatternEngine.Pattern p){
  if(p==null)return false;if(p.location!=null&&!p.location.equals("NO STRUCTURAL LOCATION"))return true;
  if(x.zones!=null)for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null)for(SupplyDemandEngine.Zone z:r.activeZones())
   if((d==SignalScoringEngine.Direction.BUY?z.type==SupplyDemandEngine.ZoneType.DEMAND:z.type==SupplyDemandEngine.ZoneType.SUPPLY)&&p.formationPrice>=z.lower&&p.formationPrice<=z.upper)return true;
  return false;
 }
 private static boolean structure(Input x,SignalScoringEngine.Direction d){
  if(x.structures==null)return false;boolean a=false,b=false;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.W1,CandleTimeframe.D1,CandleTimeframe.H4}){MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity.startsWith("VALID")&&trend(r.trend,d))a=true;}
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.M5,CandleTimeframe.M3,CandleTimeframe.M2,CandleTimeframe.M1}){MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity.startsWith("VALID")&&trend(r.trend,d))b=true;}
  return a&&b;
 }
 private static boolean indicator(Input x,SignalScoringEngine.Direction d){
  if(x.indicators==null)return false;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.H4,CandleTimeframe.H1,CandleTimeframe.M15,CandleTimeframe.M5}){
   IndicatorEngine.Result r=x.indicators.get(tf);if(r==null||r.validity!=IndicatorEngine.Validity.LIVE)continue;
   if(bias(r.bias,d)||bias(r.momentumDirection,d)||r.plusDi14!=null&&r.minusDi14!=null&&(d==SignalScoringEngine.Direction.BUY?r.plusDi14>r.minusDi14:r.minusDi14>r.plusDi14))return true;
  }return false;
 }
 private static double atr(Input x,CandlePatternEngine.Pattern p){
  CandleTimeframe tf=p==null?CandleTimeframe.M5:tf(p.timeframe);IndicatorEngine.Result r=x.indicators==null?null:x.indicators.get(tf);
  if(r==null||r.validity!=IndicatorEngine.Validity.LIVE||r.atr14==null)r=x.indicators==null?null:x.indicators.get(CandleTimeframe.M5);
  return r==null||r.validity!=IndicatorEngine.Validity.LIVE||r.atr14==null||!pos(r.atr14)?Double.NaN:r.atr14;
 }
 private static double stop(Input x,SignalScoringEngine.Direction d,CandlePatternEngine.Pattern p,double e,double b){
  if(p==null||!pos(b))return Double.NaN;double anchor=d==SignalScoringEngine.Direction.BUY?p.patternLow:p.patternHigh;
  if(x.zones!=null)for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null)for(SupplyDemandEngine.Zone z:r.activeZones()){
   boolean right=d==SignalScoringEngine.Direction.BUY?z.type==SupplyDemandEngine.ZoneType.DEMAND:z.type==SupplyDemandEngine.ZoneType.SUPPLY;
   if(right&&p.formationPrice>=z.lower&&p.formationPrice<=z.upper){double v=d==SignalScoringEngine.Direction.BUY?z.lower:z.upper;
    if(d==SignalScoringEngine.Direction.BUY&&v<e)anchor=Math.min(anchor,v);
    if(d==SignalScoringEngine.Direction.SELL&&v>e)anchor=Math.max(anchor,v);}
  }
  MarketStructureEngine.Result m=x.structures==null?null:x.structures.get(tf(p.timeframe));
  if(m!=null){MarketStructureEngine.Swing sw=d==SignalScoringEngine.Direction.BUY?m.lastSwingLow:m.lastSwingHigh;
   if(sw!=null&&pos(atr(x,p))&&Math.abs(e-sw.price)<=atr(x,p)*1.5){
    if(d==SignalScoringEngine.Direction.BUY&&sw.price<e)anchor=Math.min(anchor,sw.price);
    if(d==SignalScoringEngine.Direction.SELL&&sw.price>e)anchor=Math.max(anchor,sw.price);
   }}
  return d==SignalScoringEngine.Direction.BUY?anchor-b:anchor+b;
 }
 private static List<Target> targets(Input x,SignalScoringEngine.Direction d,double e,double risk){
  List<Level> ls=new ArrayList<>();
  if(x.zones!=null)for(Map.Entry<CandleTimeframe,SupplyDemandEngine.Result> it:x.zones.entrySet())if(it.getValue()!=null)
   for(SupplyDemandEngine.Zone z:it.getValue().activeZones()){
    if(d==SignalScoringEngine.Direction.BUY&&z.type==SupplyDemandEngine.ZoneType.SUPPLY&&z.lower>e)ls.add(new Level(z.lower,z.timeframe,"SUPPLY "+z.label));
    if(d==SignalScoringEngine.Direction.SELL&&z.type==SupplyDemandEngine.ZoneType.DEMAND&&z.upper<e)ls.add(new Level(z.upper,z.timeframe,"DEMAND "+z.label));
   }
  if(x.indicators!=null)for(Map.Entry<CandleTimeframe,IndicatorEngine.Result> it:x.indicators.entrySet())if(it.getValue()!=null&&it.getValue().validity==IndicatorEngine.Validity.LIVE){
   List<Double> vals=d==SignalScoringEngine.Direction.BUY?it.getValue().swingResistances:it.getValue().swingSupports;
   for(Double v:vals)if(v!=null&&(d==SignalScoringEngine.Direction.BUY?v>e:v<e))ls.add(new Level(v,it.getKey().name(),"SWING S/R"));
  }
  if(x.structures!=null)for(Map.Entry<CandleTimeframe,MarketStructureEngine.Result> it:x.structures.entrySet())if(it.getValue()!=null&&it.getValue().validity.startsWith("VALID")){
   MarketStructureEngine.Swing sw=d==SignalScoringEngine.Direction.BUY?it.getValue().lastSwingHigh:it.getValue().lastSwingLow;
   if(sw!=null&&(d==SignalScoringEngine.Direction.BUY?sw.price>e:sw.price<e))ls.add(new Level(sw.price,it.getKey().name(),"CONFIRMED SWING "+sw.label));
  }
  ls.sort((a,b)->d==SignalScoringEngine.Direction.BUY?Double.compare(a.price,b.price):Double.compare(b.price,a.price));
  List<Target> out=new ArrayList<>();double last=Double.NaN;
  for(Level l:ls){if(Double.isFinite(last)&&Math.abs(last-l.price)<=Math.max(x.spread*.5,.01))continue;double rr=Math.abs(l.price-e)/risk;
   if(rr<MIN_RR)continue;out.add(new Target("TP"+(out.size()+1),l.tf,l.source,l.price,rr));last=l.price;if(out.size()==3)break;}
  return out;
 }
 private static CandleTimeframe tf(String s){try{return CandleTimeframe.valueOf(s);}catch(Exception e){return CandleTimeframe.M5;}}
 private static boolean trend(MarketStructureEngine.Trend t,SignalScoringEngine.Direction d){return d==SignalScoringEngine.Direction.BUY?t==MarketStructureEngine.Trend.BULLISH:t==MarketStructureEngine.Trend.BEARISH;}
 private static boolean bias(IndicatorEngine.Bias b,SignalScoringEngine.Direction d){return d==SignalScoringEngine.Direction.BUY?b==IndicatorEngine.Bias.BULLISH:b==IndicatorEngine.Bias.BEARISH;}
 private static boolean pos(double n){return Double.isFinite(n)&&n>0;}
 private static String f(double n){return Double.isFinite(n)?String.format(Locale.US,"%.2f",n):"N/A";}
}
