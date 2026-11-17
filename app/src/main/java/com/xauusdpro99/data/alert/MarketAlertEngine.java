package com.xauusdpro99.data.alert;
import com.xauusdpro99.data.candle.*;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.signal.SignalScoringEngine;
import com.xauusdpro99.data.structure.*;
import java.util.*;

/** Detects market events exclusively from validated closed candles. */
public final class MarketAlertEngine {
 public enum Type { EMA_BULLISH_CROSSOVER,EMA_BEARISH_CROSSOVER,BULLISH_ALIGNMENT,BEARISH_ALIGNMENT,EMA_RETEST_SUCCESS,EMA_RETEST_FAILED,EMA_CROSSOVER_FAILED,BREAK_SUPPORT,BREAK_RESISTANCE,BREAKOUT_SUPPLY,BREAKOUT_DEMAND,FALSE_BREAKOUT,BOS,CHOCH,BULLISH_REVERSAL_CONFIRMED,BEARISH_REVERSAL_CONFIRMED,BUY_CONFIRMED,SELL_CONFIRMED }
 public static final long COOLDOWN_MS=300000L;
 public static final class Alert { public final Type type; public final String timeframe,status,reason,invalidation; public final double price; public final long timestampMs;
  Alert(Type t,String f,double p,long ts,String s,String r,String v){type=t;timeframe=f;price=p;timestampMs=ts;status=s;reason=r;invalidation=v;} }
 public static final class Input {
  public final Map<CandleTimeframe,CandleEngine.Snapshot> candles; public final Map<CandleTimeframe,IndicatorEngine.Result> indicators;
  public final Map<CandleTimeframe,MarketStructureEngine.Result> structures; public final Map<CandleTimeframe,SupplyDemandEngine.Result> zones;
  public final Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> patterns; public final List<SignalScoringEngine.Result> signals; public final long nowMs;
  public Input(Map<CandleTimeframe,CandleEngine.Snapshot> c,Map<CandleTimeframe,IndicatorEngine.Result> i,Map<CandleTimeframe,MarketStructureEngine.Result>s,
   Map<CandleTimeframe,SupplyDemandEngine.Result>z,Map<CandleTimeframe,List<CandlePatternEngine.Pattern>>p,List<SignalScoringEngine.Result>g,long n){
   candles=c;indicators=i;structures=s;zones=z;patterns=p;signals=g;nowMs=n;
  }
 }
 private final Map<String,Long> processed=new HashMap<>(),emitted=new HashMap<>();
 private final Map<String,Cross> pending=new HashMap<>();
 private static final class Cross {final boolean bull;final long time;Cross(boolean b,long t){bull=b;time=t;}}
 public synchronized List<Alert> evaluate(Input in){
  if(in==null||in.candles==null)return Collections.emptyList(); List<Alert> out=new ArrayList<>();
  for(CandleTimeframe tf:CandleTimeframe.values()){
   CandleEngine.Snapshot s=in.candles.get(tf); if(s==null||s.sourceStatus!=CandleEngine.SourceStatus.LIVE)continue;
   List<Candle>b=new ArrayList<>();for(Candle c:s.closed)if(c!=null&&!c.isOpen&&c.timeframe==tf&&c.isClosedAt(in.nowMs)&&CandleEngine.isValid(c,in.nowMs))b.add(c);
   b.sort(Comparator.comparingLong(c->c.openTimeMs));if(b.size()<51)continue;
   Candle c=b.get(b.size()-1);String tfName=tf.name(),key=tfName+":"+c.openTimeMs;
   if(processed.containsKey(tfName)&&processed.get(tfName)>=c.openTimeMs)continue;processed.put(tfName,c.openTimeMs);
   double e20=ema(b,b.size(),20),e50=ema(b,b.size(),50),p20=ema(b,b.size()-1,20),p50=ema(b,b.size()-1,50);
   boolean up=p20<=p50&&e20>e50,down=p20>=p50&&e20<e50;
   if(up){emit(out,key,Type.EMA_BULLISH_CROSSOVER,tfName,c.close,c.closeTimeMs(),"CONFIRMED","EMA20 cross ke atas EMA50 pada candle tertutup","Close kembali di bawah EMA50",in.nowMs);pending.put(tfName,new Cross(true,c.closeTimeMs()));}
   if(down){emit(out,key,Type.EMA_BEARISH_CROSSOVER,tfName,c.close,c.closeTimeMs(),"CONFIRMED","EMA20 cross ke bawah EMA50 pada candle tertutup","Close kembali di atas EMA50",in.nowMs);pending.put(tfName,new Cross(false,c.closeTimeMs()));}
   if(e20>e50&&c.close>e20)emit(out,key,Type.BULLISH_ALIGNMENT,tfName,c.close,c.closeTimeMs(),"ACTIVE","EMA20 > EMA50 dan close > EMA20","EMA20 cross kembali ke bawah EMA50",in.nowMs);
   if(e20<e50&&c.close<e20)emit(out,key,Type.BEARISH_ALIGNMENT,tfName,c.close,c.closeTimeMs(),"ACTIVE","EMA20 < EMA50 dan close < EMA20","EMA20 cross kembali ke atas EMA50",in.nowMs);
   Cross x=pending.get(tfName);if(x!=null){long age=c.closeTimeMs()-x.time;
    if(age>0&&age<=3*tf.durationMs&&((x.bull&&e20<e50)||(!x.bull&&e20>e50))){emit(out,key,Type.EMA_CROSSOVER_FAILED,tfName,c.close,c.closeTimeMs(),"FAILED","Urutan EMA berbalik dalam tiga candle tertutup","EMA ordering kembali searah crossover awal",in.nowMs);pending.remove(tfName);}
    else if(age>3*tf.durationMs)pending.remove(tfName);}
   double atr=atr(b),tol=Math.max(atr*.05,c.close*.000001);boolean t20=c.low<=e20+tol&&c.high>=e20-tol,t50=c.low<=e50+tol&&c.high>=e50-tol;
   if(t20||t50){double ref=t20?e20:e50;boolean bullish=e20>e50;
    boolean reject=bullish?c.close>ref+tol&&lower(c)>=body(c)*.8:c.close<ref-tol&&upper(c)>=body(c)*.8;
    boolean fail=bullish?c.close<ref-tol:c.close>ref+tol;
    if(reject)emit(out,key,Type.EMA_RETEST_SUCCESS,tfName,c.close,c.closeTimeMs(),"CONFIRMED","Sentuh EMA"+(t20?"20":"50")+", wick rejection dan close kembali searah alignment",bullish?"Close di bawah EMA50":"Close di atas EMA50",in.nowMs);
    else if(fail)emit(out,key,Type.EMA_RETEST_FAILED,tfName,c.close,c.closeTimeMs(),"CONFIRMED","Close candle menembus EMA retest ke sisi berlawanan",bullish?"Close di bawah EMA50":"Close di atas EMA50",in.nowMs);
   }
   IndicatorEngine.Result ind=in.indicators==null?null:in.indicators.get(tf);
   if(ind!=null&&ind.validity==IndicatorEngine.Validity.LIVE){Double sup=below(ind.swingSupports,c.close),res=above(ind.swingResistances,c.close);
    if(sup!=null&&b.get(b.size()-2).close>=sup-tol&&c.close<sup-tol)emit(out,key,Type.BREAK_SUPPORT,tfName,c.close,c.closeTimeMs(),"CONFIRMED","Close menembus swing support terdekat","Close kembali di atas "+fmt(sup),in.nowMs);
    if(res!=null&&b.get(b.size()-2).close<=res+tol&&c.close>res+tol)emit(out,key,Type.BREAK_RESISTANCE,tfName,c.close,c.closeTimeMs(),"CONFIRMED","Close menembus swing resistance terdekat","Close kembali di bawah "+fmt(res),in.nowMs);
   }
   MarketStructureEngine.Result ms=in.structures==null?null:in.structures.get(tf);
   if(ms!=null)for(MarketStructureEngine.Event e:ms.events)if(e.timestampMs==c.closeTimeMs()){
    if(e.type==MarketStructureEngine.EventType.BOS_BULLISH||e.type==MarketStructureEngine.EventType.BOS_BEARISH)emit(out,key,Type.BOS,tfName,e.close,e.timestampMs,"CONFIRMED",e.detail,"Close kembali melewati "+fmt(e.level),in.nowMs);
    if(e.type==MarketStructureEngine.EventType.CHOCH_BULLISH||e.type==MarketStructureEngine.EventType.CHOCH_BEARISH)emit(out,key,Type.CHOCH,tfName,e.close,e.timestampMs,"CONFIRMED",e.detail,"Close kembali melewati "+fmt(e.level),in.nowMs);
    if(e.type==MarketStructureEngine.EventType.FALSE_BREAKOUT_UP||e.type==MarketStructureEngine.EventType.FALSE_BREAKOUT_DOWN)emit(out,key,Type.FALSE_BREAKOUT,tfName,e.close,e.timestampMs,"CONFIRMED",e.detail,"Close valid di luar "+fmt(e.level),in.nowMs);
   }
   SupplyDemandEngine.Result sd=in.zones==null?null:in.zones.get(tf);
   if(sd!=null)for(SupplyDemandEngine.Breakout e:sd.breakouts)if(e.timestampMs==c.closeTimeMs()){
    Type t=e.type==SupplyDemandEngine.BreakoutType.VALID_UP?Type.BREAKOUT_SUPPLY:e.type==SupplyDemandEngine.BreakoutType.VALID_DOWN?Type.BREAKOUT_DEMAND:
      (e.type==SupplyDemandEngine.BreakoutType.FALSE_UP||e.type==SupplyDemandEngine.BreakoutType.FALSE_DOWN)?Type.FALSE_BREAKOUT:null;
    if(t!=null)emit(out,key,t,tfName,e.close,e.timestampMs,"CONFIRMED",e.note,"Close kembali ke dalam level "+fmt(e.level),in.nowMs);
   }
   List<CandlePatternEngine.Pattern> ps=in.patterns==null?null:in.patterns.get(tf);
   if(ps!=null)for(CandlePatternEngine.Pattern p:ps)if(p.timestampMs==c.closeTimeMs()&&p.status==CandlePatternEngine.Status.ENTRY_CONFIRMED){
    if(p.direction==CandlePatternEngine.Direction.BULLISH)emit(out,key,Type.BULLISH_REVERSAL_CONFIRMED,tfName,p.formationPrice,p.timestampMs,"CONFIRMED",p.name+"; close konfirmasi di atas formation high",p.invalidationCondition,in.nowMs);
    if(p.direction==CandlePatternEngine.Direction.BEARISH)emit(out,key,Type.BEARISH_REVERSAL_CONFIRMED,tfName,p.formationPrice,p.timestampMs,"CONFIRMED",p.name+"; close konfirmasi di bawah formation low",p.invalidationCondition,in.nowMs);
   }
  }
  if(in.signals!=null)for(SignalScoringEngine.Result s:in.signals)if(s.status==SignalScoringEngine.Status.BUY||s.status==SignalScoringEngine.Status.SELL){
   Type t=s.status==SignalScoringEngine.Status.BUY?Type.BUY_CONFIRMED:Type.SELL_CONFIRMED;
   emit(out,"TRADE:"+s.setupKey+":"+s.evaluatedAtMs,t,"M1/M2/M3/M5",s.entry,s.evaluatedAtMs,"CONFIRMED","Seluruh hard gate signal terpenuhi; score "+s.totalScore+"/100","Setup dibatalkan oleh engine sinyal",in.nowMs);
  }
  return Collections.unmodifiableList(out);
 }
 private void emit(List<Alert>o,String k,Type t,String tf,double p,long ts,String st,String r,String v,long now){String id=t+":"+k;Long last=emitted.get(id);if(last!=null&&(now<last||now-last<COOLDOWN_MS))return;emitted.put(id,now);o.add(new Alert(t,tf,p,ts,st,r,v));}
 private static double ema(List<Candle>b,int end,int n){if(end<n)return Double.NaN;double v=0;for(int i=0;i<n;i++)v+=b.get(i).close;v/=n;double a=2d/(n+1);for(int i=n;i<end;i++)v=a*b.get(i).close+(1-a)*v;return v;}
 private static double atr(List<Candle>b){double v=0;for(int i=b.size()-14;i<b.size();i++){Candle c=b.get(i),p=b.get(i-1);v+=Math.max(c.high-c.low,Math.max(Math.abs(c.high-p.close),Math.abs(c.low-p.close)));}return v/14;}
 private static double body(Candle c){return Math.abs(c.close-c.open);}private static double lower(Candle c){return Math.min(c.open,c.close)-c.low;}private static double upper(Candle c){return c.high-Math.max(c.open,c.close);}
 private static Double below(List<Double>x,double p){Double v=null;for(Double n:x)if(n!=null&&n<p&&(v==null||n>v))v=n;return v;}private static Double above(List<Double>x,double p){Double v=null;for(Double n:x)if(n!=null&&n>p&&(v==null||n<v))v=n;return v;}
 private static String fmt(double x){return String.format(Locale.US,"%.2f",x);}
}
