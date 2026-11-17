package com.xauusdpro99.data.structure;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import java.util.*;

/** Price-action Supply/Demand zones. No Fibonacci inputs or projections are used. */
public final class SupplyDemandEngine {
    public enum ZoneType { SUPPLY, DEMAND }
    public enum ZoneStatus { FRESH, TESTED, WEAKENED, INVALIDATED }
    public enum BreakoutType { VALID_UP, VALID_DOWN, FALSE_UP, FALSE_DOWN, RETEST_UP, RETEST_DOWN }
    public enum RetestStatus { NONE, RETESTED_REJECTED, RETESTED_ACCEPTED, WAITING }
    public static final class Zone {
        public final String id,timeframe,label,color;
        public final ZoneType type; public final double upper,lower,distance;
        public final long formedAtMs,lastUpdatedAtMs; public final int tests;
        public final ZoneStatus status; public final String invalidationCondition;
        Zone(String id,String tf,ZoneType type,double upper,double lower,long formed,int tests,
             ZoneStatus status,double distance,long updated,String invalidation){
            this.id=id;timeframe=tf;this.type=type;this.upper=upper;this.lower=lower;formedAtMs=formed;
            this.tests=tests;this.status=status;this.distance=distance;lastUpdatedAtMs=updated;
            label=tf+" "+type.name();color=tfColor(tf,type);
            invalidationCondition=invalidation;
        }
        public boolean active(){return status!=ZoneStatus.INVALIDATED;}
    }
    public static final class Breakout {
        public final BreakoutType type; public final double level,close;
        public final long timestampMs; public final RetestStatus retest; public final String note;
        Breakout(BreakoutType t,double l,double c,long ts,RetestStatus r,String n){type=t;level=l;close=c;timestampMs=ts;retest=r;note=n;}
    }
    public static final class Result {
        public final String timeframe; public final List<Zone> zones; public final List<Breakout> breakouts;
        public final long updatedAtMs; public final String validity;
        Result(String tf,List<Zone>z,List<Breakout>b,long t,String v){timeframe=tf;zones=Collections.unmodifiableList(z);breakouts=Collections.unmodifiableList(b);updatedAtMs=t;validity=v;}
        public List<Zone> activeZones(){List<Zone>x=new ArrayList<>();for(Zone z:zones)if(z.active())x.add(z);return Collections.unmodifiableList(x);}
    }
    private static final int BASE_MAX=3, IMPULSE_LOOKAHEAD=3;
    public Result analyze(CandleEngine.Snapshot snapshot,double currentPrice,long nowMs){
        if(snapshot==null||snapshot.sourceStatus!=CandleEngine.SourceStatus.LIVE||!Double.isFinite(currentPrice)||currentPrice<=0)
            return new Result(snapshot==null?"UNKNOWN":snapshot.timeframe.name(),new ArrayList<>(),new ArrayList<>(),nowMs,"DATA NOT LIVE / INVALID PRICE");
        List<Candle>b=new ArrayList<>();
        for(Candle c:snapshot.closed)if(c!=null&&!c.isOpen&&c.timeframe==snapshot.timeframe&&c.isClosedAt(nowMs)&&CandleEngine.isValid(c,nowMs))b.add(c);
        b.sort(Comparator.comparingLong(c->c.openTimeMs));
        for(int i=1;i<b.size();i++)if(b.get(i).openTimeMs<=b.get(i-1).openTimeMs)return new Result(snapshot.timeframe.name(),new ArrayList<>(),new ArrayList<>(),nowMs,"DUPLICATE / UNSORTED DATA");
        if(b.size()<8)return new Result(snapshot.timeframe.name(),new ArrayList<>(),new ArrayList<>(),nowMs,"NOT ENOUGH DATA");
        double atr=atr(b,14); double tol=Math.max(atr*0.05,currentPrice*0.000001);
        List<Zone> zones=new ArrayList<>(); List<Breakout> events=new ArrayList<>();
        int serial=0;
        for(int i=1;i<b.size()-IMPULSE_LOOKAHEAD;i++){
            int baseStart=i; int baseEnd=i;
            while(baseEnd+1<b.size()&&baseEnd-baseStart+1<BASE_MAX&&bodyRatio(b.get(baseEnd+1))<=0.45)baseEnd++;
            double top=-Double.MAX_VALUE,bottom=Double.MAX_VALUE;
            for(int k=baseStart;k<=baseEnd;k++){top=Math.max(top,b.get(k).high);bottom=Math.min(bottom,b.get(k).low);}
            int impulseIndex=-1; ZoneType type=null;
            for(int j=baseEnd+1;j<=Math.min(b.size()-1,baseEnd+IMPULSE_LOOKAHEAD);j++){
                Candle c=b.get(j);
                double impulseAtr=atrAt(b,j,14);
                if(impulseAtr>0&&c.close<bottom-impulseAtr*0.8){type=ZoneType.SUPPLY;impulseIndex=j;break;}
                if(impulseAtr>0&&c.close>top+impulseAtr*0.8){type=ZoneType.DEMAND;impulseIndex=j;break;}
            }
            if(type==null)continue;
            long formed=b.get(baseEnd).closeTimeMs();
            boolean duplicate=false;for(Zone z:zones)if(z.type==type&&Math.abs(z.upper-top)<=tol&&Math.abs(z.lower-bottom)<=tol){duplicate=true;break;}
            if(!duplicate){Zone z=evaluateZone("Z"+(++serial),snapshot.timeframe.name(),type,top,bottom,formed,b,impulseIndex,atr,tol,currentPrice,nowMs,events);zones.add(z);}
            i=baseEnd;
        }
        return new Result(snapshot.timeframe.name(),zones,events,nowMs,"VALID CLOSED OHLC; base <= "+BASE_MAX+" candles; displacement >= 0.8 ATR");
    }
    private static Zone evaluateZone(String id,String tf,ZoneType type,double top,double bottom,long formed,List<Candle>b,
                                     int impulse,double atr,double tol,double price,long now,List<Breakout> events){
        int tests=0;ZoneStatus status=ZoneStatus.FRESH;boolean broken=false;RetestStatus retest=RetestStatus.NONE;
        double level=type==ZoneType.DEMAND?bottom:top;
        for(int i=impulse+1;i<b.size();i++){
            Candle c=b.get(i);
            double candleTol=Math.max(atrAt(b,i,14)*0.05,c.close*0.000001);
            boolean intersects=c.high>=bottom-candleTol&&c.low<=top+candleTol;
            if(type==ZoneType.DEMAND&&c.close<bottom-candleTol){
                events.add(new Breakout(BreakoutType.VALID_DOWN,bottom,c.close,c.closeTimeMs(),RetestStatus.WAITING,"Close candle di bawah Demand; zona INVALIDATED"));
                addRetestEvent(events,b,i+1,bottom,ZoneType.DEMAND,tol);
                broken=true;status=ZoneStatus.INVALIDATED;break;
            }
            if(type==ZoneType.SUPPLY&&c.close>top+candleTol){
                events.add(new Breakout(BreakoutType.VALID_UP,top,c.close,c.closeTimeMs(),RetestStatus.WAITING,"Close candle di atas Supply; zona INVALIDATED"));
                addRetestEvent(events,b,i+1,top,ZoneType.SUPPLY,tol);
                broken=true;status=ZoneStatus.INVALIDATED;break;
            }
            boolean wickBreak=type==ZoneType.DEMAND?c.low<bottom-candleTol&&c.close>=bottom-candleTol:c.high>top+candleTol&&c.close<=top+candleTol;
            if(wickBreak)events.add(new Breakout(type==ZoneType.DEMAND?BreakoutType.FALSE_DOWN:BreakoutType.FALSE_UP,level,c.close,c.closeTimeMs(),RetestStatus.NONE,"Wick menembus tetapi close kembali; false breakout"));
            if(intersects){
                tests++;
                if(tests==1)status=ZoneStatus.TESTED;
                else status=ZoneStatus.WEAKENED;
                boolean rejection=type==ZoneType.DEMAND?(lowerWick(c)>=body(c)*1.5&&closePosition(c)>=0.60)
                        :(upperWick(c)>=body(c)*1.5&&closePosition(c)<=0.40);
                if(rejection)retest=RetestStatus.RETESTED_REJECTED;
                else retest=RetestStatus.RETESTED_ACCEPTED;
            }
        }
        if(!broken&&tests>1)status=ZoneStatus.WEAKENED;
        double distance=price<bottom?bottom-price:(price>top?price-top:0d);
        String invalid=type==ZoneType.DEMAND?"Candle close < "+fmt(bottom)+" - tolerance":"Candle close > "+fmt(top)+" + tolerance";
        return new Zone(id,tf,type,top,bottom,formed,tests,status,distance,now,invalid);
    }
    private static void addRetestEvent(List<Breakout> events,List<Candle> bars,int start,double level,ZoneType brokenZone,double tolerance){
        for(int j=start;j<bars.size();j++){
            Candle c=bars.get(j);
            double tol=Math.max(atrAt(bars,j,14)*0.05,c.close*0.000001);
            boolean touched=brokenZone==ZoneType.DEMAND?c.high>=level-tol:c.low<=level+tol;
            if(!touched)continue;
            boolean rejected=brokenZone==ZoneType.DEMAND?c.close<level-tol:c.close>level+tol;
            RetestStatus state=rejected?RetestStatus.RETESTED_REJECTED:RetestStatus.RETESTED_ACCEPTED;
            events.add(new Breakout(brokenZone==ZoneType.DEMAND?BreakoutType.RETEST_DOWN:BreakoutType.RETEST_UP,
                    level,c.close,c.closeTimeMs(),state,rejected?"Retest ditolak dari bawah/atas level breakout":"Retest diterima kembali melewati level breakout"));
            return;
        }
    }
    private static double atr(List<Candle>b,int p){return atrAt(b,b.size()-1,p);}
    private static double atrAt(List<Candle>b,int end,int p){if(end<p)return 0;double s=0;for(int i=end-p+1;i<=end;i++)s+=tr(b.get(i),b.get(i-1).close);return s/p;}
    private static double tr(Candle c,double prev){return Math.max(c.high-c.low,Math.max(Math.abs(c.high-prev),Math.abs(c.low-prev)));}
    private static double body(Candle c){return Math.abs(c.close-c.open);}
    private static double bodyRatio(Candle c){return c.high==c.low?0:body(c)/(c.high-c.low);}
    private static double upperWick(Candle c){return c.high-Math.max(c.open,c.close);}
    private static double lowerWick(Candle c){return Math.min(c.open,c.close)-c.low;}
    private static double closePosition(Candle c){return c.high==c.low?0.5:(c.close-c.low)/(c.high-c.low);}
    private static String fmt(double x){return String.format(Locale.US,"%.2f",x);}
    public static String timeframeColor(String tf,ZoneType type){
        if("H4".equals(tf))return type==ZoneType.SUPPLY?"#8000FF":"#8000FF";
        if("D1".equals(tf))return type==ZoneType.SUPPLY?"#1677FF":"#1677FF";
        if("W1".equals(tf))return "#FF4FA3";
        return type==ZoneType.SUPPLY?"#FF3030":"#168CFF";
    }
}