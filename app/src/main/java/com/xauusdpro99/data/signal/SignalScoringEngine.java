package com.xauusdpro99.data.signal;

import com.xauusdpro99.data.candle.*;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.structure.MarketStructureEngine;
import com.xauusdpro99.data.structure.SupplyDemandEngine;
import java.util.*;

/** Deterministic fail-closed setup scoring. Points are quality scores, not win probabilities. */
public final class SignalScoringEngine {
 public static final int WEIGHT_PATTERN=20,WEIGHT_LOCATION=25,WEIGHT_INDICATORS=20,WEIGHT_STRUCTURE=15,WEIGHT_ALIGNMENT=15,WEIGHT_ENTRY=5,MIN_CANDIDATE_SCORE=57;
 public static final double MIN_RISK_REWARD=1.5,DEFAULT_MAX_SPREAD=0.80;
 private static final long MAX_TICK_AGE_MS=15000;
 public enum Direction{BUY,SELL} public enum Quality{HIGH_QUALITY,MODERATE_QUALITY,EARLY_LOWER_QUALITY,NO_TRADE} public enum Status{BUY,SELL,WAIT,NO_TRADE}
 public static final class Input{
  public final Map<CandleTimeframe,CandleEngine.Snapshot> candles;
  public final Map<CandleTimeframe,IndicatorEngine.Result> indicators;
  public final Map<CandleTimeframe,MarketStructureEngine.Result> structures;
  public final Map<CandleTimeframe,SupplyDemandEngine.Result> zones;
  public final Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> patterns;
  public final boolean connectionLive,quoteValid; public final long quoteReceivedAtMs,nowMs;
  public final double bid,ask,spread,maxSpread;
  public Input(Map<CandleTimeframe,CandleEngine.Snapshot> c,Map<CandleTimeframe,IndicatorEngine.Result> i,
   Map<CandleTimeframe,MarketStructureEngine.Result> s,Map<CandleTimeframe,SupplyDemandEngine.Result> z,
   Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> p,boolean live,boolean valid,long received,long now,
   double bid,double ask,double spread,double maxSpread){
   candles=c;indicators=i;structures=s;zones=z;patterns=p;connectionLive=live;quoteValid=valid;
   quoteReceivedAtMs=received;nowMs=now;this.bid=bid;this.ask=ask;this.spread=spread;this.maxSpread=maxSpread;
  }
 }
 public static final class Component{public final String name,explanation;public final int weight,points;Component(String n,int w,int p,String e){name=n;weight=w;points=Math.max(0,Math.min(w,p));explanation=e;}}
 public static final class Conflict{public final String timeframe,role,explanation;public final IndicatorEngine.Bias indicatorBias;public final MarketStructureEngine.Trend structureTrend;
  Conflict(CandleTimeframe tf,String role,IndicatorEngine.Bias b,MarketStructureEngine.Trend t,String e){timeframe=tf.name();this.role=role;indicatorBias=b;structureTrend=t;explanation=e;}}
 public static final class Result{
  public final Direction direction;public final int totalScore;public final Quality quality;public final Status status;
  public final List<Component> components;public final List<Conflict> conflicts;public final List<String> acceptedReasons,rejectedReasons;
  public final String patternName,setupKey;public final double entry,stopLoss,target,riskReward,spread;public final long evaluatedAtMs;
  Result(Direction d,int score,Quality q,Status st,List<Component> c,List<Conflict> cf,List<String>a,List<String>r,String pn,String key,double en,double sl,double tp,double rr,double sp,long at){
   direction=d;totalScore=score;quality=q;status=st;components=Collections.unmodifiableList(c);conflicts=Collections.unmodifiableList(cf);
   acceptedReasons=Collections.unmodifiableList(a);rejectedReasons=Collections.unmodifiableList(r);patternName=pn;setupKey=key;
   entry=en;stopLoss=sl;target=tp;riskReward=rr;spread=sp;evaluatedAtMs=at;
  }
 }
 private final Set<String> emitted=new LinkedHashSet<>();
 public synchronized List<Result> evaluate(Input in){return Collections.unmodifiableList(Arrays.asList(evaluateDirection(in,Direction.BUY),evaluateDirection(in,Direction.SELL)));}
 public synchronized Result evaluateDirection(Input in,Direction d){
  if(in==null)return empty(d,"INPUT NULL");
  List<String> ok=new ArrayList<>(),bad=new ArrayList<>();List<Conflict> conflicts=new ArrayList<>();
  boolean tfOk=validateTimeframes(in,bad);
  double actualSpread=in.ask-in.bid,limit=in.maxSpread>0?in.maxSpread:DEFAULT_MAX_SPREAD;
  boolean quoteOk=in.connectionLive&&in.quoteValid&&in.quoteReceivedAtMs>0&&in.nowMs>=in.quoteReceivedAtMs&&in.nowMs-in.quoteReceivedAtMs<=MAX_TICK_AGE_MS&&pos(in.bid)&&pos(in.ask)&&in.ask>=in.bid;
  if(!quoteOk)bad.add("Tick/koneksi tidak LIVE, tidak valid, atau berumur >15 detik");
  boolean spreadOk=quoteOk&&Double.isFinite(actualSpread)&&actualSpread>=0&&actualSpread<=limit;
  if(!spreadOk)bad.add("Spread tidak layak: "+f(actualSpread)+"; batas "+f(limit));
  CandlePatternEngine.Pattern p=selectPattern(in,d);
  int patternPts=p==null?0:(int)Math.round(p.strength/100d*20);
  if(p==null)bad.add("Pola candle searah tidak ditemukan pada M1/M2/M3/M5");else ok.add(p.name+" "+p.timeframe+" "+p.status);
  int locationPts=locationScore(in,d,p);boolean locationOk=locationPts>=18&&p!=null&&!"NO STRUCTURAL LOCATION".equals(p.location);
  if(locationOk)ok.add("Lokasi valid: "+p.location);else bad.add("Lokasi Supply/Demand atau S/R belum valid");
  int indicatorPts=indicatorScore(in,d),structurePts=structureScore(in,d,conflicts),alignPts=alignmentScore(in,d,conflicts);
  int entryPts=p!=null&&p.status==CandlePatternEngine.Status.ENTRY_CONFIRMED?5:0;
  if(entryPts==5)ok.add("Candle tertutup berikutnya mengonfirmasi pola");else bad.add("Menunggu konfirmasi candle tertutup berikutnya");
  boolean structureOk=structureSupports(in,d);
  if(structureOk)ok.add("Struktur utama dan setup mendukung");else bad.add("Struktur utama/setup belum mendukung");
  double entry=d==Direction.BUY?in.ask:in.bid,atr=atrM1(in);
  double sl=p==null?Double.NaN:(d==Direction.BUY?p.patternLow-atr*.10:p.patternHigh+atr*.10);
  double target=target(in,d,entry),risk=d==Direction.BUY?entry-sl:sl-entry,reward=d==Direction.BUY?target-entry:entry-target;
  double rr=pos(risk)&&pos(reward)?reward/risk:Double.NaN;
  boolean riskOk=pos(sl)&&pos(target)&&sl>0&&(d==Direction.BUY?sl<entry&&target>entry:sl>entry&&target<entry)&&Double.isFinite(rr)&&rr>=MIN_RISK_REWARD;
  if(riskOk)ok.add("SL/TP valid; RR "+f(rr));else bad.add("SL/TP tidak valid atau RR < 1:1.5");
  boolean patternOk=p!=null&&p.status!=CandlePatternEngine.Status.INVALIDATED;
  if(!patternOk)bad.add("Pola kosong atau INVALIDATED");
  boolean entryConfirmed=entryPts==5;
  String key=p==null?"":d+"|"+p.timeframe+"|"+p.name+"|"+p.timestampMs;
  boolean duplicate=!key.isEmpty()&&emitted.contains(key);if(duplicate)bad.add("Setup duplikat; sinyal tidak diterbitkan");
  int total=Math.min(100,patternPts+locationPts+indicatorPts+structurePts+alignPts+entryPts);
  Quality q=quality(total);if(total<57)bad.add("Skor di bawah 57: NO TRADE");
  boolean gates=tfOk&&quoteOk&&spreadOk&&patternOk&&locationOk&&structureOk&&riskOk&&entryConfirmed&&!duplicate;
  Status status=total<57?Status.NO_TRADE:!gates?Status.WAIT:d==Direction.BUY?Status.BUY:Status.SELL;
  if(status==Status.BUY||status==Status.SELL){ok.add("Seluruh syarat wajib lolos");emitted.add(key);while(emitted.size()>300)emitted.remove(emitted.iterator().next());}
  List<Component> components=Arrays.asList(new Component("Candle Pattern",20,patternPts,p==null?"Tidak ada pola":p.name+" / "+p.status),
   new Component("Supply/Demand + S/R",25,locationPts,locationOk?p.location:"Lokasi belum valid"),
   new Component("Konfirmasi indikator",20,indicatorPts,"RSI/EMA/ADX/Momentum lintas timeframe"),
   new Component("Market Structure",15,structurePts,"Trend/BOS/CHOCH lintas timeframe"),
   new Component("Multi-timeframe alignment",15,alignPts,"Bobot per peran dan konflik tercatat"),
   new Component("Entry Confirmation",5,entryPts,entryConfirmed?"Confirmed":"Menunggu candle berikutnya"));
  return new Result(d,total,q,status,components,conflicts,ok,bad,p==null?"":p.name,key,entry,sl,target,rr,actualSpread,in.nowMs);
 }
 private static boolean validateTimeframes(Input x,List<String> bad){
  boolean valid=true;
  for(CandleTimeframe tf:CandleTimeframe.values()){
   CandleEngine.Snapshot c=x.candles==null?null:x.candles.get(tf);
   IndicatorEngine.Result i=x.indicators==null?null:x.indicators.get(tf);
   MarketStructureEngine.Result s=x.structures==null?null:x.structures.get(tf);
   SupplyDemandEngine.Result z=x.zones==null?null:x.zones.get(tf);
   if(c==null||c.sourceStatus!=CandleEngine.SourceStatus.LIVE||c.closed.isEmpty()){bad.add(tf+": candle tidak LIVE/closed");valid=false;}
   if(i==null||i.validity!=IndicatorEngine.Validity.LIVE){bad.add(tf+": indikator NOT LIVE/NOT ENOUGH DATA");valid=false;}
   if(s==null||s.validity==null||!s.validity.startsWith("VALID")){bad.add(tf+": struktur tidak valid");valid=false;}
   if(z==null||z.validity==null||!z.validity.startsWith("VALID")){bad.add(tf+": Supply/Demand tidak valid");valid=false;}
  }return valid;
 }
 private static CandlePatternEngine.Pattern selectPattern(Input x,Direction d){
  if(x.patterns==null)return null;CandlePatternEngine.Pattern best=null;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.M1,CandleTimeframe.M2,CandleTimeframe.M3,CandleTimeframe.M5}){
   List<CandlePatternEngine.Pattern> list=x.patterns.get(tf);if(list==null)continue;
   for(CandlePatternEngine.Pattern p:list){boolean match=d==Direction.BUY?p.direction==CandlePatternEngine.Direction.BULLISH:p.direction==CandlePatternEngine.Direction.BEARISH;
    if(match&&p.status!=CandlePatternEngine.Status.INVALIDATED&&(best==null||p.timestampMs>best.timestampMs||(p.timestampMs==best.timestampMs&&p.strength>best.strength)))best=p;
   }
  }return best;
 }
 private static int locationScore(Input x,Direction d,CandlePatternEngine.Pattern p){
  if(x.zones==null||p==null)return 0;int best=0;
  for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null)for(SupplyDemandEngine.Zone z:r.activeZones()){
   boolean right=d==Direction.BUY?z.type==SupplyDemandEngine.ZoneType.DEMAND:z.type==SupplyDemandEngine.ZoneType.SUPPLY;
   if(right&&(p.formationPrice>=z.lower&&p.formationPrice<=z.upper||d==Direction.BUY&&p.patternLow<=z.upper&&p.patternLow>=z.lower||d==Direction.SELL&&p.patternHigh>=z.lower&&p.patternHigh<=z.upper))best=25;
  }
  if(best>0)return best;
  if(x.indicators!=null)for(IndicatorEngine.Result r:x.indicators.values())if(r!=null&&r.validity==IndicatorEngine.Validity.LIVE){
   List<Double> levels=d==Direction.BUY?r.swingSupports:r.swingResistances;
   for(Double level:levels)if(level!=null&&Math.abs((d==Direction.BUY?p.patternLow:p.patternHigh)-level)<=Math.max(p.formationPrice*.0005,r.atr14==null?0:r.atr14*.35))best=Math.max(best,20);
  }return best;
 }
 private static int indicatorScore(Input x,Direction d){
  if(x.indicators==null)return 0;double got=0,total=0;
  for(CandleTimeframe tf:CandleTimeframe.values()){IndicatorEngine.Result r=x.indicators.get(tf);if(r==null||r.validity!=IndicatorEngine.Validity.LIVE)continue;
   int w=roleWeight(tf),n=0,a=0;total+=w;
   if(r.bias!=IndicatorEngine.Bias.NEUTRAL){n++;if(match(r.bias,d))a++;}
   if(r.momentumDirection!=IndicatorEngine.Bias.NEUTRAL){n++;if(match(r.momentumDirection,d))a++;}
   if(r.plusDi14!=null&&r.minusDi14!=null){n++;if(d==Direction.BUY?r.plusDi14>r.minusDi14:r.minusDi14>r.plusDi14)a++;}
   if(n>0)got+=w*(double)a/n;
  }return total==0?0:(int)Math.round(got/total*20);
 }
 private static int structureScore(Input x,Direction d,List<Conflict> cf){
  if(x.structures==null)return 0;double got=0,total=0;
  for(CandleTimeframe tf:CandleTimeframe.values()){MarketStructureEngine.Result r=x.structures.get(tf);if(r==null||r.validity==null||!r.validity.startsWith("VALID"))continue;
   int w=roleWeight(tf);total+=w;boolean a=trend(r.trend,d)||hasBreak(r,d),o=opposes(r.trend,d)||hasBreak(r,other(d));
   if(a)got+=w;else if(o){got-=w*.35;addConflict(cf,tf,"STRUCTURE",null,r,"Struktur berlawanan");}else got+=w*.25;
  }return total==0?0:(int)Math.round(Math.max(0,Math.min(1,got/total))*15);
 }
 private static int alignmentScore(Input x,Direction d,List<Conflict> cf){
  if(x.indicators==null)return 0;double got=0,total=0;
  for(CandleTimeframe tf:CandleTimeframe.values()){IndicatorEngine.Result r=x.indicators.get(tf);if(r==null||r.validity!=IndicatorEngine.Validity.LIVE)continue;
   int w=roleWeight(tf);total+=w;if(r.bias==IndicatorEngine.Bias.NEUTRAL)got+=w*.5;else if(match(r.bias,d))got+=w;
   else addConflict(cf,tf,role(tf),r.bias,x.structures==null?null:x.structures.get(tf),"Bias indikator bertentangan");
  }return total==0?0:(int)Math.round(got/total*15);
 }
 private static boolean structureSupports(Input x,Direction d){
  if(x.structures==null)return false;boolean main=false,entry=false;
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.W1,CandleTimeframe.D1,CandleTimeframe.H4}){MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity!=null&&r.validity.startsWith("VALID")&&(trend(r.trend,d)||hasBreak(r,d)))main=true;}
  for(CandleTimeframe tf:new CandleTimeframe[]{CandleTimeframe.M5,CandleTimeframe.M3,CandleTimeframe.M2,CandleTimeframe.M1}){MarketStructureEngine.Result r=x.structures.get(tf);if(r!=null&&r.validity!=null&&r.validity.startsWith("VALID")&&(trend(r.trend,d)||hasBreak(r,d)))entry=true;}
  return main&&entry;
 }
 private static double target(Input x,Direction d,double entry){
  if(x.zones==null)return Double.NaN;double best=d==Direction.BUY?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
  for(SupplyDemandEngine.Result r:x.zones.values())if(r!=null)for(SupplyDemandEngine.Zone z:r.activeZones()){
   if(d==Direction.BUY&&z.type==SupplyDemandEngine.ZoneType.SUPPLY&&z.lower>entry)best=Math.min(best,z.lower);
   if(d==Direction.SELL&&z.type==SupplyDemandEngine.ZoneType.DEMAND&&z.upper<entry)best=Math.max(best,z.upper);
  }return Double.isFinite(best)?best:Double.NaN;
 }
 private static double atrM1(Input x){IndicatorEngine.Result r=x.indicators==null?null:x.indicators.get(CandleTimeframe.M1);return r==null||r.atr14==null?0:r.atr14;}
 private static boolean hasBreak(MarketStructureEngine.Result r,Direction d){for(MarketStructureEngine.Event e:r.events)if(d==Direction.BUY?(e.type==MarketStructureEngine.EventType.BOS_BULLISH||e.type==MarketStructureEngine.EventType.CHOCH_BULLISH):(e.type==MarketStructureEngine.EventType.BOS_BEARISH||e.type==MarketStructureEngine.EventType.CHOCH_BEARISH))return true;return false;}
 private static boolean trend(MarketStructureEngine.Trend t,Direction d){return d==Direction.BUY?t==MarketStructureEngine.Trend.BULLISH:t==MarketStructureEngine.Trend.BEARISH;}
 private static boolean opposes(MarketStructureEngine.Trend t,Direction d){return trend(t,other(d));}
 private static boolean match(IndicatorEngine.Bias b,Direction d){return d==Direction.BUY?b==IndicatorEngine.Bias.BULLISH:b==IndicatorEngine.Bias.BEARISH;}
 private static Direction other(Direction d){return d==Direction.BUY?Direction.SELL:Direction.BUY;}
 private static int roleWeight(CandleTimeframe t){switch(t){case W1:case D1:case H4:return 4;case H1:case H8:case H12:case M30:case M15:return 2;default:return 1;}}
 private static String role(CandleTimeframe t){switch(t){case W1:case D1:case H4:return "ARAH UTAMA";case H1:case H8:case H12:case M30:case M15:return "KONFIRMASI";default:return "SETUP/TIMING";}}
 private static void addConflict(List<Conflict> a,CandleTimeframe t,String role,IndicatorEngine.Bias b,MarketStructureEngine.Result s,String why){for(Conflict c:a)if(c.timeframe.equals(t.name())&&c.explanation.equals(why))return;a.add(new Conflict(t,role,b,s==null?null:s.trend,why));}
 static Quality qualityForTest(int n){return quality(n);}
 private static Quality quality(int n){return n>=85?Quality.HIGH_QUALITY:n>=70?Quality.MODERATE_QUALITY:n>=57?Quality.EARLY_LOWER_QUALITY:Quality.NO_TRADE;}
 private static boolean pos(double n){return Double.isFinite(n)&&n>0;}private static String f(double n){return Double.isFinite(n)?String.format(Locale.US,"%.2f",n):"N/A";}
 private static Result empty(Direction d,String why){return new Result(d,0,Quality.NO_TRADE,Status.NO_TRADE,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),Collections.singletonList(why),"","",Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0);}
}