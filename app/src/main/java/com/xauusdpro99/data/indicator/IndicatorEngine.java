package com.xauusdpro99.data.indicator;

import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Closed-candle-only technical indicators.
 * RSI/ATR/ADX use Wilder RMA/SMMA; EMA is SMA-seeded; Momentum is Close/Close[n]*100.
 */
public final class IndicatorEngine {
    public static final int PERIOD = 14;
    private static final int SWING_LEFT = 2;
    private static final int SWING_RIGHT = 2;

    public enum Validity { LIVE, STALE, UNAVAILABLE, NOT_ENOUGH_DATA }
    public enum Bias { BULLISH, BEARISH, NEUTRAL }
    public enum Strength { STRONG, MODERATE, WEAK, NOT_ENOUGH_DATA }
    public enum Volatility { LOW, NORMAL, HIGH, NOT_ENOUGH_DATA }

    public static final class Result {
        public final String timeframe;
        public final Validity validity;
        public final String validityDetail;
        public final long updatedAtMs;
        public final int closedBars;
        public final Double rsi14, momentum14, adx14, plusDi14, minusDi14;
        public final Double ema10, ema20, ema50, ema100, ema200;
        public final Double tenkan9, kijun26, senkouA, senkouB52;
        public final Double atr14, atrPercent, atrVsAverage;
        public final Double pivot, r1, r2, r3, s1, s2, s3;
        public final List<Double> swingSupports, swingResistances;
        public final Bias bias;
        public final Bias momentumDirection;
        public final Strength trendStrength;
        public final Volatility volatility;

        private Result(String timeframe, Validity validity, String validityDetail, long updatedAtMs,
                       int closedBars, Double rsi14, Double momentum14, Double adx14,
                       Double plusDi14, Double minusDi14, Double ema10, Double ema20,
                       Double ema50, Double ema100, Double ema200, Double tenkan9,
                       Double kijun26, Double senkouA, Double senkouB52, Double atr14,
                       Double atrPercent, Double atrVsAverage, Double pivot, Double r1,
                       Double r2, Double r3, Double s1, Double s2, Double s3,
                       List<Double> swingSupports, List<Double> swingResistances,
                       Bias bias, Bias momentumDirection, Strength trendStrength,
                       Volatility volatility) {
            this.timeframe=timeframe; this.validity=validity; this.validityDetail=validityDetail;
            this.updatedAtMs=updatedAtMs; this.closedBars=closedBars;
            this.rsi14=rsi14; this.momentum14=momentum14; this.adx14=adx14;
            this.plusDi14=plusDi14; this.minusDi14=minusDi14;
            this.ema10=ema10; this.ema20=ema20; this.ema50=ema50; this.ema100=ema100; this.ema200=ema200;
            this.tenkan9=tenkan9; this.kijun26=kijun26; this.senkouA=senkouA; this.senkouB52=senkouB52;
            this.atr14=atr14; this.atrPercent=atrPercent; this.atrVsAverage=atrVsAverage;
            this.pivot=pivot; this.r1=r1; this.r2=r2; this.r3=r3; this.s1=s1; this.s2=s2; this.s3=s3;
            this.swingSupports=Collections.unmodifiableList(swingSupports);
            this.swingResistances=Collections.unmodifiableList(swingResistances);
            this.bias=bias; this.momentumDirection=momentumDirection;
            this.trendStrength=trendStrength; this.volatility=volatility;
        }

        static Result unavailable(String tf, Validity validity, String detail, long now, int bars) {
            return new Result(tf, validity, detail, now, bars, null,null,null,null,null,
                    null,null,null,null,null,null,null,null,null,null,null,null,
                    null,null,null,null,null,null,null, new ArrayList<>(), new ArrayList<>(),
                    Bias.NEUTRAL, Bias.NEUTRAL, Strength.NOT_ENOUGH_DATA, Volatility.NOT_ENOUGH_DATA);
        }
    }

    public Result calculate(CandleEngine.Snapshot snapshot, CandleEngine.Snapshot dailySnapshot, long nowMs) {
        return calculateInternal(snapshot,dailySnapshot,nowMs,false);
    }
    /** Historical chart only; STALE remains STALE. */
    public Result calculateForChart(CandleEngine.Snapshot snapshot, CandleEngine.Snapshot dailySnapshot, long nowMs) {
        return calculateInternal(snapshot,dailySnapshot,nowMs,true);
    }
    private Result calculateInternal(CandleEngine.Snapshot snapshot, CandleEngine.Snapshot dailySnapshot, long nowMs, boolean chartOnly) {
        if (snapshot == null) return Result.unavailable("UNKNOWN", Validity.UNAVAILABLE, "NO CANDLE SNAPSHOT", nowMs, 0);
        String tf = snapshot.timeframe.name();
        if (snapshot.sourceStatus == CandleEngine.SourceStatus.STALE && !chartOnly)
            return Result.unavailable(tf, Validity.STALE, "STALE DATA — calculation blocked", nowMs, snapshot.closed.size());
        if (snapshot.sourceStatus == CandleEngine.SourceStatus.UNAVAILABLE)
            return Result.unavailable(tf, Validity.UNAVAILABLE, "SOURCE UNAVAILABLE", nowMs, snapshot.closed.size());

        List<Candle> bars = new ArrayList<>();
        for (Candle c : snapshot.closed) {
            if (c != null && !c.isOpen && c.isClosedAt(nowMs)
                    && c.timeframe == snapshot.timeframe && CandleEngine.isValid(c, nowMs)) bars.add(c);
        }
        bars.sort(Comparator.comparingLong(c -> c.openTimeMs));
        for (int i=1;i<bars.size();i++) {
            if (bars.get(i).openTimeMs <= bars.get(i-1).openTimeMs)
                return Result.unavailable(tf, Validity.UNAVAILABLE, "DUPLICATE/UNSORTED CANDLE TIMESTAMPS", nowMs, bars.size());
        }
        double[] close = new double[bars.size()], high = new double[bars.size()], low = new double[bars.size()];
        for (int i=0;i<bars.size();i++) { close[i]=bars.get(i).close; high[i]=bars.get(i).high; low[i]=bars.get(i).low; }
        int last=bars.size()-1;
        Double rsi = rsi(close, PERIOD);
        Double mom = momentum(close, PERIOD);
        AdxValues adx = adx(high,low,close,PERIOD);
        Double e10=ema(close,10), e20=ema(close,20), e50=ema(close,50), e100=ema(close,100), e200=ema(close,200);
        Double tenkan=midpoint(high,low,9,last), kijun=midpoint(high,low,26,last), spanB=midpoint(high,low,52,last);
        Double spanA=tenkan==null||kijun==null?null:(tenkan+kijun)/2d;
        AtrValues atr=atrSeries(high,low,close,PERIOD);
        Double atrValue=atr.last;
        Double atrPercent=atrValue==null?null:atrValue/close[last]*100d;
        Double atrVsAverage=atr.relativeAverage;
        PivotValues pivots = pivots(dailySnapshot, nowMs);
        List<Double> supports = new ArrayList<>(), resistances = new ArrayList<>();
        collectSwings(high,low,close,supports,resistances);

        Bias momentumDir = mom==null?Bias.NEUTRAL:(mom>100.0000001?Bias.BULLISH:(mom<99.9999999?Bias.BEARISH:Bias.NEUTRAL));
        int bull=0,bear=0;
        if (rsi!=null) { if(rsi>55) bull++; else if(rsi<45) bear++; }
        if (adx!=null && adx.plusDi!=null && adx.minusDi!=null) { if(adx.plusDi>adx.minusDi) bull++; else if(adx.minusDi>adx.plusDi) bear++; }
        if (e50!=null && e200!=null) { if(close[last]>e200 && e50>e200) bull++; else if(close[last]<e200 && e50<e200) bear++; }
        if (spanA!=null && spanB!=null) { double cloud=Math.max(spanA,spanB); double floor=Math.min(spanA,spanB); if(close[last]>cloud) bull++; else if(close[last]<floor) bear++; }
        Bias bias=bull>bear?Bias.BULLISH:(bear>bull?Bias.BEARISH:Bias.NEUTRAL);
        Strength strength=adx==null?Strength.NOT_ENOUGH_DATA:(adx.adx>=25?Strength.STRONG:(adx.adx>=20?Strength.MODERATE:Strength.WEAK));
        Volatility vol=atrVsAverage==null?Volatility.NOT_ENOUGH_DATA:(atrVsAverage<0.8?Volatility.LOW:(atrVsAverage>1.2?Volatility.HIGH:Volatility.NORMAL));

        boolean enoughForAll = bars.size() >= 200 && rsi != null && mom != null && adx != null
                && e10 != null && e20 != null && e50 != null && e100 != null && e200 != null
                && tenkan != null && kijun != null && spanB != null && atrValue != null
                && pivots.p != null;
        String detail = enoughForAll ? "VALID CLOSED OHLC"
                : "NOT ENOUGH DATA untuk satu atau lebih indikator; nilai yang sudah memenuhi periode tetap ditampilkan";
        return new Result(tf, snapshot.sourceStatus == CandleEngine.SourceStatus.STALE ? Validity.STALE : (enoughForAll ? Validity.LIVE : Validity.NOT_ENOUGH_DATA), snapshot.sourceStatus == CandleEngine.SourceStatus.STALE ? "STALE HISTORICAL CHART ONLY" : detail, nowMs, bars.size(),
                rsi,mom, adx==null?null:adx.adx, adx==null?null:adx.plusDi, adx==null?null:adx.minusDi,
                e10,e20,e50,e100,e200,tenkan,kijun,spanA,spanB,atrValue,atrPercent,atrVsAverage,
                pivots.p,pivots.r1,pivots.r2,pivots.r3,pivots.s1,pivots.s2,pivots.s3,
                supports,resistances,bias,momentumDir,strength,vol);
    }

    private static Double momentum(double[] c,int p) {
        if(c.length<=p || c[c.length-1-p]==0) return null;
        return c[c.length-1]/c[c.length-1-p]*100d;
    }

    private static Double rsi(double[] c,int p) {
        if(c.length<=p) return null;
        double gain=0,loss=0;
        for(int i=1;i<=p;i++){double d=c[i]-c[i-1]; if(d>0)gain+=d; else loss-=d;}
        gain/=p; loss/=p;
        for(int i=p+1;i<c.length;i++){double d=c[i]-c[i-1]; gain=(gain*(p-1)+Math.max(d,0))/p; loss=(loss*(p-1)+Math.max(-d,0))/p;}
        if(loss==0) return gain==0?50d:100d;
        if(gain==0) return 0d;
        double rs=gain/loss;
        return 100d-100d/(1d+rs);
    }

    private static Double ema(double[] c,int p) {
        if(c.length<p) return null;
        double value=0;
        for(int i=0;i<p;i++) value+=c[i];
        value/=p;
        double alpha=2d/(p+1d);
        for(int i=p;i<c.length;i++) value=alpha*c[i]+(1d-alpha)*value;
        return value;
    }

    private static Double midpoint(double[] h,double[] l,int p,int end) {
        if(end+1<p) return null;
        double hi=-Double.MAX_VALUE,lo=Double.MAX_VALUE;
        for(int i=end-p+1;i<=end;i++){hi=Math.max(hi,h[i]);lo=Math.min(lo,l[i]);}
        return (hi+lo)/2d;
    }

    private static final class AtrValues { final Double last,relativeAverage; AtrValues(Double l,Double r){last=l;relativeAverage=r;} }
    private static AtrValues atrSeries(double[] h,double[] l,double[] c,int p) {
        if(c.length<=p) return new AtrValues(null,null);
        double[] tr=new double[c.length]; tr[0]=h[0]-l[0];
        for(int i=1;i<c.length;i++) tr[i]=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-c[i-1]),Math.abs(l[i]-c[i-1])));
        double v=0; for(int i=1;i<=p;i++)v+=tr[i]; v/=p;
        List<Double> values=new ArrayList<>(); values.add(v);
        for(int i=p+1;i<c.length;i++){v=(v*(p-1)+tr[i])/p;values.add(v);}
        int start=Math.max(0,values.size()-50); double sum=0;
        for(int i=start;i<values.size();i++)sum+=values.get(i);
        double avg=sum/(values.size()-start);
        return new AtrValues(v,avg==0?null:v/avg);
    }

    private static final class AdxValues { final Double adx,plusDi,minusDi; AdxValues(Double a,Double p,Double m){adx=a;plusDi=p;minusDi=m;} }
    private static AdxValues adx(double[] h,double[] l,double[] c,int p) {
        if(c.length<2*p) return null;
        double tr=0,plus=0,minus=0;
        for(int i=1;i<=p;i++){
            double up=h[i]-h[i-1], down=l[i-1]-l[i];
            tr+=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-c[i-1]),Math.abs(l[i]-c[i-1])));
            if(up>down && up>0)plus+=up; else if(down>up && down>0)minus+=down;
        }
        List<Double> dx=new ArrayList<>();
        double plusDi=0,minusDi=0;
        for(int i=p;i<c.length;i++){
            if(i>p){
                double up=h[i]-h[i-1],down=l[i-1]-l[i];
                double trNow=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-c[i-1]),Math.abs(l[i]-c[i-1])));
                tr=tr-tr/p+trNow;
                double pNow=(up>down && up>0)?up:0d;
                double mNow=(down>up && down>0)?down:0d;
                plus=plus-plus/p+pNow; minus=minus-minus/p+mNow;
            }
            plusDi=tr==0?0:100d*plus/tr;
            minusDi=tr==0?0:100d*minus/tr;
            double denom=plusDi+minusDi;
            dx.add(denom==0?0d:100d*Math.abs(plusDi-minusDi)/denom);
        }
        if(dx.size()<p) return null;
        double adx=0;
        for(int i=0;i<p;i++)adx+=dx.get(i);
        adx/=p;
        for(int i=p;i<dx.size();i++)adx=(adx*(p-1)+dx.get(i))/p;
        return new AdxValues(adx,plusDi,minusDi);
    }

    private static final class PivotValues {
        final Double p,r1,r2,r3,s1,s2,s3;
        PivotValues(Double p,Double r1,Double r2,Double r3,Double s1,Double s2,Double s3){this.p=p;this.r1=r1;this.r2=r2;this.r3=r3;this.s1=s1;this.s2=s2;this.s3=s3;}
    }
    private static PivotValues pivots(CandleEngine.Snapshot daily,long now) {
        if(daily==null || daily.sourceStatus != CandleEngine.SourceStatus.LIVE || daily.closed.isEmpty())
            return new PivotValues(null,null,null,null,null,null,null);
        Candle prev=daily.closed.get(daily.closed.size()-1);
        if(prev==null || prev.isOpen || !prev.isClosedAt(now) || !CandleEngine.isValid(prev,now))return new PivotValues(null,null,null,null,null,null,null);
        double p=(prev.high+prev.low+prev.close)/3d, range=prev.high-prev.low;
        return new PivotValues(p,2*p-prev.low,p+range,p*2+(prev.high-2*prev.low),
                2*p-prev.high,p-range,p*2-(2*prev.high-prev.low));
    }

    private static void collectSwings(double[] h,double[] l,double[] c,List<Double> supports,List<Double> resistances) {
        for(int i=SWING_LEFT;i<h.length-SWING_RIGHT;i++){
            boolean highPivot=true,lowPivot=true;
            for(int j=i-SWING_LEFT;j<=i+SWING_RIGHT;j++){
                if(j==i)continue;
                if(h[j]>=h[i])highPivot=false;
                if(l[j]<=l[i])lowPivot=false;
            }
            if(highPivot)resistances.add(h[i]);
            if(lowPivot)supports.add(l[i]);
        }
        trimLatest(supports,5); trimLatest(resistances,5);
        Collections.reverse(supports); Collections.reverse(resistances);
    }
    private static void trimLatest(List<Double> values,int max){while(values.size()>max)values.remove(0);}
}
