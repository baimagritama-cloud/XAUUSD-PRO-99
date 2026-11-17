package com.xauusdpro99.data.pattern;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Closed-OHLC rule scanner. Pattern detection is evidence, never an automatic trade instruction. */
public final class CandlePatternEngine {
    public enum Direction { BULLISH, BEARISH }
    public enum Status { DETECTED, LOCATION_CONFIRMED, ENTRY_CONFIRMED, INVALIDATED }
    public enum ZoneType { DEMAND, SUPPLY }

    public static final class PriceZone {
        public final ZoneType type;
        public final double low, high;
        public final String label;
        public PriceZone(ZoneType type,double low,double high,String label) {
            if(type==null||!Double.isFinite(low)||!Double.isFinite(high)||low<=0||high<low) throw new IllegalArgumentException("Invalid zone");
            this.type=type;this.low=low;this.high=high;this.label=label==null?type.name():label;
        }
        boolean contains(double p,double tolerance){return p>=low-tolerance&&p<=high+tolerance;}
    }
    public static final class Context {
        public final IndicatorEngine.Result sameTimeframe,higherTimeframe;
        public final List<PriceZone> zones;
        public Context(IndicatorEngine.Result same,IndicatorEngine.Result higher,List<PriceZone> zones){
            sameTimeframe=same;higherTimeframe=higher;
            this.zones=zones==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(zones));
        }
        public static Context empty(){return new Context(null,null,Collections.emptyList());}
    }
    public static final class Pattern {
        public final String name,timeframe,location,invalidationCondition;
        public final Direction direction;
        public final Status status;
        public final double formationPrice,patternHigh,patternLow,body,upperWick,lowerWick,range,
                bodyToRange,wickToBody,atrRelative,closePosition;
        public final long timestampMs;
        public final int strength;
        private Pattern(String name,Direction direction,String timeframe,double price,long time,String location,
                        int strength,Status status,String invalidation,double high,double low,Metrics m){
            this.name=name;this.direction=direction;this.timeframe=timeframe;formationPrice=price;timestampMs=time;
            this.location=location;this.strength=strength;this.status=status;invalidationCondition=invalidation;
            patternHigh=high;patternLow=low;body=m.body;upperWick=m.upper;lowerWick=m.lower;range=m.range;
            bodyToRange=m.bodyRatio;wickToBody=m.wickBody;atrRelative=m.atrRelative;closePosition=m.closePosition;
        }
    }
    private enum P {
        BULL_ENGULF,BULL_HARAMI,PIERCING,MORNING_STAR,THREE_SOLDIERS,HAMMER,INVERTED_HAMMER,DRAGONFLY,
        TWEEZER_BOTTOM,BULL_MARUBOZU,BULL_KICKER,THREE_INSIDE_UP,THREE_OUTSIDE_UP,BULL_PIN,BULL_REJECTION,
        BEAR_ENGULF,BEAR_HARAMI,DARK_CLOUD,EVENING_STAR,THREE_CROWS,SHOOTING_STAR,HANGING_MAN,GRAVESTONE,
        TWEEZER_TOP,BEAR_MARUBOZU,BEAR_KICKER,THREE_INSIDE_DOWN,THREE_OUTSIDE_DOWN,BEAR_PIN,BEAR_REJECTION
    }
    private static final String[] NAMES={
        "Bullish Engulfing","Bullish Harami","Piercing Line","Morning Star","Three White Soldiers","Hammer",
        "Inverted Hammer","Dragonfly Doji","Tweezer Bottom","Bullish Marubozu","Bullish Kicker","Three Inside Up",
        "Three Outside Up","Bullish Pin Bar","Bullish Rejection Candle","Bearish Engulfing","Bearish Harami",
        "Dark Cloud Cover","Evening Star","Three Black Crows","Shooting Star","Hanging Man","Gravestone Doji",
        "Tweezer Top","Bearish Marubozu","Bearish Kicker","Three Inside Down","Three Outside Down",
        "Bearish Pin Bar","Bearish Rejection Candle"
    };

    public List<Pattern> detect(CandleEngine.Snapshot snapshot,Context context,long nowMs){
        if(snapshot==null||snapshot.sourceStatus!=CandleEngine.SourceStatus.LIVE||snapshot.closed==null||snapshot.closed.isEmpty())
            return Collections.emptyList();
        List<Candle> bars=new ArrayList<>();
        for(Candle c:snapshot.closed) if(c!=null&&!c.isOpen&&c.timeframe==snapshot.timeframe&&c.isClosedAt(nowMs)&&CandleEngine.isValid(c,nowMs))bars.add(c);
        bars.sort(Comparator.comparingLong(c->c.openTimeMs));
        for(int i=1;i<bars.size();i++)if(bars.get(i).openTimeMs<=bars.get(i-1).openTimeMs)return Collections.emptyList();
        if(bars.size()<2)return Collections.emptyList();
        double[] atr=atrSeries(bars,14);
        Context ctx=context==null?Context.empty():context;
        List<Pattern> out=new ArrayList<>();
        int start=Math.max(1,bars.size()-8);
        for(int i=start;i<bars.size();i++)for(P p:P.values()){
            int need=requiredBars(p);
            if(i-need+1<0||!matches(p,bars,i,atr[i]))continue;
            Pattern found=build(p,bars,i,atr[i],ctx);
            if(i+1<bars.size())found=resolveNext(found,bars.get(i+1));
            out.add(found);
        }
        out.sort((a,b)->Long.compare(b.timestampMs,a.timestampMs));
        return Collections.unmodifiableList(out);
    }
    private static int requiredBars(P p){
        switch(p){
            case MORNING_STAR:case EVENING_STAR:case THREE_SOLDIERS:case THREE_CROWS:
            case THREE_INSIDE_UP:case THREE_INSIDE_DOWN:case THREE_OUTSIDE_UP:case THREE_OUTSIDE_DOWN:return 3;
            default:return 2;
        }
    }
    private static boolean matches(P p,List<Candle>b,int i,double atr){
        Candle c=b.get(i),prev=b.get(i-1);Metrics m=metrics(c,atr),pm=metrics(prev,atr);
        double tol=Math.max(atr*0.10,c.close*0.00001);
        switch(p){
            case BULL_ENGULF:return bearish(prev)&&bullish(c)&&bodyLow(c)<=bodyLow(prev)+tol&&bodyHigh(c)>=bodyHigh(prev)-tol&&m.bodyRatio>=0.45;
            case BEAR_ENGULF:return bullish(prev)&&bearish(c)&&bodyLow(c)<=bodyLow(prev)+tol&&bodyHigh(c)>=bodyHigh(prev)-tol&&m.bodyRatio>=0.45;
            case BULL_HARAMI:return bearish(prev)&&bullish(c)&&bodyLow(c)>bodyLow(prev)&&bodyHigh(c)<bodyHigh(prev)&&m.body<=pm.body*0.60;
            case BEAR_HARAMI:return bullish(prev)&&bearish(c)&&bodyLow(c)>bodyLow(prev)&&bodyHigh(c)<bodyHigh(prev)&&m.body<=pm.body*0.60;
            case PIERCING:return bearish(prev)&&bullish(c)&&c.open<prev.close+tol&&c.close>bodyLow(prev)+pm.body*0.50&&c.close<prev.open&&m.bodyRatio>=0.35;
            case DARK_CLOUD:return bullish(prev)&&bearish(c)&&c.open>prev.close-tol&&c.close<bodyLow(prev)+pm.body*0.50&&c.close>prev.open&&m.bodyRatio>=0.35;
            case MORNING_STAR:return star(b.get(i-2),prev,c,true);
            case EVENING_STAR:return star(b.get(i-2),prev,c,false);
            case THREE_SOLDIERS:return soldiers(b.get(i-2),prev,c);
            case THREE_CROWS:return crows(b.get(i-2),prev,c);
            case HAMMER:return priorDowntrend(b,i)&&m.bodyRatio<=0.38&&m.lower>=2*m.body&&m.upper<=0.45*m.body&&m.closePosition>=0.60&&atr>0&&m.atrRelative>=0.45&&m.atrRelative<=2.8;
            case HANGING_MAN:return priorUptrend(b,i)&&m.bodyRatio<=0.38&&m.lower>=2*m.body&&m.upper<=0.45*m.body&&m.closePosition>=0.60&&atr>0&&m.atrRelative>=0.45&&m.atrRelative<=2.8;
            case INVERTED_HAMMER:return priorDowntrend(b,i)&&m.bodyRatio<=0.38&&m.upper>=2*m.body&&m.lower<=0.45*m.body&&m.closePosition<=0.40&&atr>0&&m.atrRelative>=0.45&&m.atrRelative<=2.8;
            case SHOOTING_STAR:return priorUptrend(b,i)&&m.bodyRatio<=0.38&&m.upper>=2*m.body&&m.lower<=0.45*m.body&&m.closePosition<=0.40&&atr>0&&m.atrRelative>=0.45&&m.atrRelative<=2.8;
            case DRAGONFLY:return m.bodyRatio<=0.10&&m.upper<=m.range*0.10&&m.lower>=m.range*0.60;
            case GRAVESTONE:return m.bodyRatio<=0.10&&m.lower<=m.range*0.10&&m.upper>=m.range*0.60;
            case TWEEZER_BOTTOM:return bearish(prev)&&bullish(c)&&Math.abs(prev.low-c.low)<=tol&&m.bodyRatio>=0.20&&pm.bodyRatio>=0.20;
            case TWEEZER_TOP:return bullish(prev)&&bearish(c)&&Math.abs(prev.high-c.high)<=tol&&m.bodyRatio>=0.20&&pm.bodyRatio>=0.20;
            case BULL_MARUBOZU:return bullish(c)&&m.bodyRatio>=0.90&&m.upper<=m.range*0.06&&m.lower<=m.range*0.06;
            case BEAR_MARUBOZU:return bearish(c)&&m.bodyRatio>=0.90&&m.upper<=m.range*0.06&&m.lower<=m.range*0.06;
            case BULL_KICKER:return bearish(prev)&&bullish(c)&&c.open>bodyHigh(prev)+tol&&m.bodyRatio>=0.50;
            case BEAR_KICKER:return bullish(prev)&&bearish(c)&&c.open<bodyLow(prev)-tol&&m.bodyRatio>=0.50;
            case THREE_INSIDE_UP:return bearish(b.get(i-2))&&bullish(prev)&&bodyLow(prev)>bodyLow(b.get(i-2))&&bodyHigh(prev)<bodyHigh(b.get(i-2))&&bullish(c)&&c.close>b.get(i-2).open;
            case THREE_INSIDE_DOWN:return bullish(b.get(i-2))&&bearish(prev)&&bodyLow(prev)>bodyLow(b.get(i-2))&&bodyHigh(prev)<bodyHigh(b.get(i-2))&&bearish(c)&&c.close<b.get(i-2).open;
            case THREE_OUTSIDE_UP:return bearish(b.get(i-2))&&bullish(prev)&&bodyLow(prev)<=bodyLow(b.get(i-2))&&bodyHigh(prev)>=bodyHigh(b.get(i-2))&&bullish(c)&&c.close>prev.close;
            case THREE_OUTSIDE_DOWN:return bullish(b.get(i-2))&&bearish(prev)&&bodyLow(prev)<=bodyLow(b.get(i-2))&&bodyHigh(prev)>=bodyHigh(b.get(i-2))&&bearish(c)&&c.close<prev.close;
            case BULL_PIN:return m.lower>=2.5*m.body&&m.upper<=0.40*m.body&&m.closePosition>=0.70&&m.bodyRatio<=0.30&&m.atrRelative>=0.35;
            case BULL_REJECTION:return m.lower>=2.5*m.body&&m.upper<=0.50*m.body&&m.closePosition>=0.65&&m.bodyRatio<=0.35&&m.atrRelative>=0.35;
            case BEAR_PIN:return m.upper>=2.5*m.body&&m.lower<=0.40*m.body&&m.closePosition<=0.30&&m.bodyRatio<=0.30&&m.atrRelative>=0.35;
            case BEAR_REJECTION:return m.upper>=2.5*m.body&&m.lower<=0.50*m.body&&m.closePosition<=0.35&&m.bodyRatio<=0.35&&m.atrRelative>=0.35;
            default:return false;
        }
    }
    private static boolean star(Candle a,Candle b,Candle c,boolean bull){
        Metrics mb=metrics(b,0),mc=metrics(c,0);double mid=(a.open+a.close)/2d;
        if(bull)return bearish(a)&&mb.bodyRatio<=0.32&&bullish(c)&&c.close>mid&&c.close<a.open&&mc.bodyRatio>=0.40;
        return bullish(a)&&mb.bodyRatio<=0.32&&bearish(c)&&c.close<mid&&c.close>a.open&&mc.bodyRatio>=0.40;
    }
    private static boolean soldiers(Candle a,Candle b,Candle c){
        Metrics x=metrics(a,0),y=metrics(b,0),z=metrics(c,0);
        return bullish(a)&&bullish(b)&&bullish(c)&&a.close<b.close&&b.close<c.close
                &&insideBody(b,a)&&insideBody(c,b)&&x.bodyRatio>=0.5&&y.bodyRatio>=0.5&&z.bodyRatio>=0.5
                &&x.upper<=x.body*0.45&&y.upper<=y.body*0.45&&z.upper<=z.body*0.45;
    }
    private static boolean crows(Candle a,Candle b,Candle c){
        Metrics x=metrics(a,0),y=metrics(b,0),z=metrics(c,0);
        return bearish(a)&&bearish(b)&&bearish(c)&&a.close>b.close&&b.close>c.close
                &&insideBody(b,a)&&insideBody(c,b)&&x.bodyRatio>=0.5&&y.bodyRatio>=0.5&&z.bodyRatio>=0.5
                &&x.lower<=x.body*0.45&&y.lower<=y.body*0.45&&z.lower<=z.body*0.45;
    }
    private static boolean insideBody(Candle inner,Candle outer){return inner.open>=bodyLow(outer)&&inner.open<=bodyHigh(outer);}
    private static boolean priorDowntrend(List<Candle> b,int i){return i>=3&&b.get(i-1).close<b.get(i-3).close;}
    private static boolean priorUptrend(List<Candle> b,int i){return i>=3&&b.get(i-1).close>b.get(i-3).close;}
    private static Pattern build(P p,List<Candle> bars,int i,double atr,Context ctx){
        Candle c=bars.get(i);int n=requiredBars(p);double high=-Double.MAX_VALUE,low=Double.MAX_VALUE;
        for(int k=i-n+1;k<=i;k++){high=Math.max(high,bars.get(k).high);low=Math.min(low,bars.get(k).low);}
        Direction dir=p.ordinal()<=P.BULL_REJECTION.ordinal()?Direction.BULLISH:Direction.BEARISH;
        Metrics m=metrics(c,atr);String location="NO STRUCTURAL LOCATION";boolean located=false;
        double tolerance=Math.max(atr*0.35,c.close*0.0001);
        for(PriceZone z:ctx.zones){
            boolean correctType=(dir==Direction.BULLISH&&z.type==ZoneType.DEMAND)||(dir==Direction.BEARISH&&z.type==ZoneType.SUPPLY);
            if(correctType&&(z.contains(c.close,tolerance)||z.contains(dir==Direction.BULLISH?c.low:c.high,tolerance))){
                location=z.label;located=true;break;
            }
        }
        IndicatorEngine.Result ind=ctx.sameTimeframe;
        if(!located&&ind!=null){
            List<Double> levels=dir==Direction.BULLISH?ind.swingSupports:ind.swingResistances;
            for(Double level:levels)if(level!=null&&Math.abs((dir==Direction.BULLISH?c.low:c.high)-level)<=tolerance){
                location=(dir==Direction.BULLISH?"SUPPORT ":"RESISTANCE ")+fmt(level);located=true;break;
            }
        }
        IndicatorEngine.Bias higher=ctx.higherTimeframe==null?IndicatorEngine.Bias.NEUTRAL:ctx.higherTimeframe.bias;
        boolean aligned=dir==Direction.BULLISH?higher==IndicatorEngine.Bias.BULLISH:higher==IndicatorEngine.Bias.BEARISH;
        int strength=baseStrength(p,m)+(located?20:0)+(aligned?10:0);
        if(ctx.higherTimeframe!=null&&!aligned&&higher!=IndicatorEngine.Bias.NEUTRAL)strength-=5;
        strength=Math.max(0,Math.min(100,strength));
        Status status=located?Status.LOCATION_CONFIRMED:Status.DETECTED;
        String invalidation=dir==Direction.BULLISH?"Close candle di bawah low pola ("+fmt(low)+")":"Close candle di atas high pola ("+fmt(high)+")";
        return new Pattern(NAMES[p.ordinal()],dir,c.timeframe.name(),c.close,c.closeTimeMs(),location,strength,status,invalidation,high,low,m);
    }
    private static Pattern resolveNext(Pattern p,Candle next){
        if(p.direction==Direction.BULLISH){if(next.close<p.patternLow)return copy(p,Status.INVALIDATED);if(next.close>p.patternHigh)return copy(p,Status.ENTRY_CONFIRMED);}
        else {if(next.close>p.patternHigh)return copy(p,Status.INVALIDATED);if(next.close<p.patternLow)return copy(p,Status.ENTRY_CONFIRMED);}
        return p;
    }
    private static Pattern copy(Pattern p,Status s){
        Metrics m=new Metrics(p.body,p.upperWick,p.lowerWick,p.range,p.bodyToRange,p.wickToBody,p.atrRelative,p.closePosition);
        return new Pattern(p.name,p.direction,p.timeframe,p.formationPrice,p.timestampMs,p.location,p.strength,s,p.invalidationCondition,p.patternHigh,p.patternLow,m);
    }
    private static int baseStrength(P p,Metrics m){
        int score=45+(int)Math.round(Math.min(25,m.bodyRatio*25));
        if(m.atrRelative>=0.5&&m.atrRelative<=2.0)score+=10;
        if(requiredBars(p)==3)score+=8;
        return Math.min(75,score);
    }
    private static final class Metrics {
        final double body,upper,lower,range,bodyRatio,wickBody,atrRelative,closePosition;
        Metrics(double b,double u,double l,double r,double br,double wb,double ar,double cp){body=b;upper=u;lower=l;range=r;bodyRatio=br;wickBody=wb;atrRelative=ar;closePosition=cp;}
    }
    private static Metrics metrics(Candle c,double atr){
        double range=Math.max(0,c.high-c.low),body=Math.abs(c.close-c.open);
        double upper=c.high-Math.max(c.open,c.close),lower=Math.min(c.open,c.close)-c.low;
        double ratio=range==0?0:body/range,wick=body==0?(upper+lower==0?0:Double.POSITIVE_INFINITY):(upper+lower)/body;
        return new Metrics(body,upper,lower,range,ratio,wick,atr<=0?0:range/atr,range==0?0.5:(c.close-c.low)/range);
    }
    private static double[] atrSeries(List<Candle> bars,int period){
        double[] out=new double[bars.size()];if(bars.size()<=period)return out;
        double sum=0;for(int i=1;i<=period;i++)sum+=tr(bars.get(i),bars.get(i-1).close);
        double value=sum/period;out[period]=value;
        for(int i=period+1;i<bars.size();i++){value=(value*(period-1)+tr(bars.get(i),bars.get(i-1).close))/period;out[i]=value;}
        for(int i=0;i<period;i++)out[i]=out[period];return out;
    }
    private static double tr(Candle c,double prev){return Math.max(c.high-c.low,Math.max(Math.abs(c.high-prev),Math.abs(c.low-prev)));}
    private static boolean bullish(Candle c){return c.close>c.open;}
    private static boolean bearish(Candle c){return c.close<c.open;}
    private static double bodyLow(Candle c){return Math.min(c.open,c.close);}
    private static double bodyHigh(Candle c){return Math.max(c.open,c.close);}
    private static String fmt(double v){return String.format(java.util.Locale.US,"%.2f",v);}
}
