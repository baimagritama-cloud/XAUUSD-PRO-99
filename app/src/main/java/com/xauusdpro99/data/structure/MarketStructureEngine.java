package com.xauusdpro99.data.structure;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import java.util.*;

/** Causal market-structure scanner. A pivot is published only on its right-confirmation candle. */
public final class MarketStructureEngine {
    public enum SwingType { SWING_HIGH, SWING_LOW }
    public enum Label { HH, HL, LH, LL, UNCLASSIFIED }
    public enum EventType { BOS_BULLISH, BOS_BEARISH, CHOCH_BULLISH, CHOCH_BEARISH, FALSE_BREAKOUT_UP, FALSE_BREAKOUT_DOWN }
    public enum Trend { BULLISH, BEARISH, CONSOLIDATION, UNKNOWN }
    public static final int LEFT = 2, RIGHT = 2;

    public static final class Swing {
        public final SwingType type; public final Label label;
        public final double price; public final long pivotTimeMs, confirmedAtMs;
        public final int pivotIndex, confirmationIndex;
        Swing(SwingType t, Label l, double p, long pt, long ct, int pi, int ci) {
            type=t; label=l; price=p; pivotTimeMs=pt; confirmedAtMs=ct; pivotIndex=pi; confirmationIndex=ci;
        }
    }
    public static final class Event {
        public final EventType type; public final double level, close;
        public final long timestampMs; public final String detail;
        Event(EventType t,double l,double c,long ts,String d){type=t;level=l;close=c;timestampMs=ts;detail=d;}
    }
    public static final class Result {
        public final String timeframe; public final List<Swing> swings; public final List<Event> events;
        public final Trend trend; public final Swing lastSwingHigh,lastSwingLow;
        public final int closedBars; public final long updatedAtMs; public final String validity;
        Result(String tf,List<Swing>s,List<Event>e,Trend t,Swing h,Swing l,int n,long u,String v){
            timeframe=tf;swings=Collections.unmodifiableList(s);events=Collections.unmodifiableList(e);
            trend=t;lastSwingHigh=h;lastSwingLow=l;closedBars=n;updatedAtMs=u;validity=v;
        }
    }

    public Result analyze(CandleEngine.Snapshot snapshot,long nowMs) {
        if(snapshot==null||snapshot.sourceStatus!=CandleEngine.SourceStatus.LIVE)
            return new Result(snapshot==null?"UNKNOWN":snapshot.timeframe.name(),new ArrayList<>(),new ArrayList<>(),
                    Trend.UNKNOWN,null,null,0,nowMs,"DATA NOT LIVE");
        List<Candle>b=validBars(snapshot,nowMs);
        if(b.size()<LEFT+RIGHT+3)return new Result(snapshot.timeframe.name(),new ArrayList<>(),new ArrayList<>(),
                Trend.UNKNOWN,null,null,b.size(),nowMs,"NOT ENOUGH DATA");
        List<Swing> swings=new ArrayList<>(); List<Event> events=new ArrayList<>();
        Swing prevHigh=null,prevLow=null,lastHigh=null,lastLow=null;
        Trend trend=Trend.UNKNOWN; Set<String> broken=new HashSet<>();
        double atr=atr(b,Math.max(0,b.size()-1),14);
        double tol=Math.max(atr*0.05,b.get(b.size()-1).close*0.000001);
        for(int i=LEFT+RIGHT;i<b.size();i++) {
            double candleAtr=atr(b,i,14);
            double candleTol=Math.max(candleAtr*0.05,b.get(i).close*0.000001);
            int pivot=i-RIGHT;
            Candle p=b.get(pivot);
            if(isHigh(b,pivot) || isLow(b,pivot)) {
                if(isHigh(b,pivot)) {
                    Label label=prevHigh==null?Label.UNCLASSIFIED:(p.high>prevHigh.price+candleTol?Label.HH:(p.high<prevHigh.price-candleTol?Label.LH:Label.UNCLASSIFIED));
                    Swing s=new Swing(SwingType.SWING_HIGH,label,p.high,p.openTimeMs,b.get(i).closeTimeMs(),pivot,i);
                    swings.add(s); prevHigh=s; lastHigh=s;
                }
                if(isLow(b,pivot)) {
                    Label label=prevLow==null?Label.UNCLASSIFIED:(p.low>prevLow.price+candleTol?Label.HL:(p.low<prevLow.price-candleTol?Label.LL:Label.UNCLASSIFIED));
                    Swing s=new Swing(SwingType.SWING_LOW,label,p.low,p.openTimeMs,b.get(i).closeTimeMs(),pivot,i);
                    swings.add(s); prevLow=s; lastLow=s;
                }
            }
            Candle c=b.get(i);
            if(lastHigh!=null && c.high>lastHigh.price+candleTol && c.close<=lastHigh.price+candleTol) {
                events.add(new Event(EventType.FALSE_BREAKOUT_UP,lastHigh.price,c.close,c.closeTimeMs(),"Wick melewati swing high, close kembali di bawah level"));
            } else if(lastHigh!=null && c.close>lastHigh.price+candleTol && broken.add("H"+lastHigh.confirmationIndex)) {
                EventType type=trend==Trend.BEARISH?EventType.CHOCH_BULLISH:EventType.BOS_BULLISH;
                events.add(new Event(type,lastHigh.price,c.close,c.closeTimeMs(),"Candle tertutup di atas swing high terkonfirmasi"));
                trend=Trend.BULLISH;
            }
            if(lastLow!=null && c.low<lastLow.price-candleTol && c.close>=lastLow.price-candleTol) {
                events.add(new Event(EventType.FALSE_BREAKOUT_DOWN,lastLow.price,c.close,c.closeTimeMs(),"Wick melewati swing low, close kembali di atas level"));
            } else if(lastLow!=null && c.close<lastLow.price-candleTol && broken.add("L"+lastLow.confirmationIndex)) {
                EventType type=trend==Trend.BULLISH?EventType.CHOCH_BEARISH:EventType.BOS_BEARISH;
                events.add(new Event(type,lastLow.price,c.close,c.closeTimeMs(),"Candle tertutup di bawah swing low terkonfirmasi"));
                trend=Trend.BEARISH;
            }
        }
        if(trend==Trend.UNKNOWN && b.size()>=20) {
            double hi=-Double.MAX_VALUE,lo=Double.MAX_VALUE;
            for(int i=b.size()-20;i<b.size();i++){hi=Math.max(hi,b.get(i).high);lo=Math.min(lo,b.get(i).low);}
            if(atr>0 && hi-lo<=atr*4.0)trend=Trend.CONSOLIDATION;
        }
        if(lastHigh!=null&&lastLow!=null&&trend==Trend.UNKNOWN) {
            if(lastHigh.label==Label.HH&&lastLow.label==Label.HL)trend=Trend.BULLISH;
            else if(lastHigh.label==Label.LH&&lastLow.label==Label.LL)trend=Trend.BEARISH;
        }
        return new Result(snapshot.timeframe.name(),swings,events,trend,lastHigh,lastLow,b.size(),nowMs,"VALID CLOSED OHLC; pivot right-confirmation="+RIGHT);
    }

    private static List<Candle> validBars(CandleEngine.Snapshot s,long now){
        List<Candle> out=new ArrayList<>();
        for(Candle c:s.closed)if(c!=null&&!c.isOpen&&c.timeframe==s.timeframe&&c.isClosedAt(now)&&CandleEngine.isValid(c,now))out.add(c);
        out.sort(Comparator.comparingLong(x->x.openTimeMs));
        for(int i=1;i<out.size();i++)if(out.get(i).openTimeMs<=out.get(i-1).openTimeMs)return new ArrayList<>();
        return out;
    }
    private static boolean isHigh(List<Candle>b,int i){double v=b.get(i).high;for(int k=i-LEFT;k<=i+RIGHT;k++)if(k!=i&&b.get(k).high>=v)return false;return true;}
    private static boolean isLow(List<Candle>b,int i){double v=b.get(i).low;for(int k=i-LEFT;k<=i+RIGHT;k++)if(k!=i&&b.get(k).low<=v)return false;return true;}
    static double atr(List<Candle>b,int end,int p){
        if(end<p)return 0;double sum=0;for(int i=end-p+1;i<=end;i++){Candle c=b.get(i),prev=b.get(i-1);sum+=Math.max(c.high-c.low,Math.max(Math.abs(c.high-prev.close),Math.abs(c.low-prev.close)));}return sum/p;
    }
}