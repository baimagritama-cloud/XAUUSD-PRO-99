package com.xauusdpro99;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.xauusdpro99.data.candle.Candle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lightweight native candlestick chart; keeps a dedicated right-side price scale clear of candles. */
public final class LiveCandleChartView extends View {
    public static final class Zone {
        public final double lower, upper;
        public final boolean demand;
        public final String label;
        public Zone(double lower,double upper,boolean demand,String label){this.lower=lower;this.upper=upper;this.demand=demand;this.label=label;}
    }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Candle> candles=Collections.emptyList();
    private List<Zone> zones=Collections.emptyList();
    private double support=Double.NaN,resistance=Double.NaN;
    private String timeframe="M1",ema="OFF";
    private float lastX;
    private int panBars=0;
    private final int bg=0xFF0B1220, grid=0xFF263244, up=0xFF20C997, down=0xFFFF5C70;
    public LiveCandleChartView(Context c){super(c);setBackgroundColor(bg);setMinimumHeight(260);}
    public void setData(List<Candle> data,List<Zone> z,String tf,double s,double r){
        candles=data==null?Collections.emptyList():new ArrayList<>(data);
        zones=z==null?Collections.emptyList():new ArrayList<>(z);
        timeframe=tf==null?"--":tf;support=s;resistance=r;
        invalidate();
    }
    public void setEma(String value){ema=value==null?"OFF":value;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w<80||h<100)return;
        float left=12,right=w-66,top=18,bottom=h-25;float plotW=right-left,plotH=bottom-top;
        p.setColor(0xFFB9C7D8);p.setTextSize(11);p.setStyle(Paint.Style.FILL);c.drawText(timeframe+"  |  "+candles.size()+" CLOSED CANDLES  |  DRAG TO PAN",left,12,p);
        for(int i=0;i<=4;i++){float y=top+plotH*i/4f;p.setColor(grid);p.setStrokeWidth(1);c.drawLine(left,y,right,y,p);}
        if(candles.isEmpty()){p.setColor(0xFFB9C7D8);p.setTextSize(14);c.drawText("DATA UNAVAILABLE — menunggu OHLC",left+8,top+plotH/2,p);return;}
        int end=Math.max(0,candles.size()-panBars),start=Math.max(0,end-300);if(end<=start)end=Math.min(candles.size(),start+1);
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(int i=start;i<end;i++){Candle b=candles.get(i);min=Math.min(min,b.low);max=Math.max(max,b.high);}
        for(Zone z:zones){min=Math.min(min,z.lower);max=Math.max(max,z.upper);}
        if(Double.isFinite(support))min=Math.min(min,support);if(Double.isFinite(resistance))max=Math.max(max,resistance);
        double pad=Math.max((max-min)*.08,.01);min-=pad;max+=pad;final double range=max-min;
        for(int i=0;i<5;i++){double v=max-range*i/4d;float y=map(v,min,max,top,bottom);p.setColor(0xFF9AA9BC);p.setTextSize(10);c.drawText(String.format(Locale.US,"%.2f",v),right+5,y+4,p);}
        for(Zone z:zones){float y1=map(z.upper,min,max,top,bottom),y2=map(z.lower,min,max,top,bottom);p.setColor(z.demand?0x3320A8FF:0x33FF5268);c.drawRect(left,Math.min(y1,y2),right,Math.max(y1,y2),p);p.setColor(z.demand?0xFF55B8FF:0xFFFF7888);p.setTextSize(9);c.drawText(z.label,left+4,Math.min(y1,y2)+11,p);}
        if(Double.isFinite(resistance))level(c,left,right,map(resistance,min,max,top,bottom),0xFFFF6677,"R "+String.format(Locale.US,"%.2f",resistance));
        if(Double.isFinite(support))level(c,left,right,map(support,min,max,top,bottom),0xFF55B8FF,"S "+String.format(Locale.US,"%.2f",support));
        float step=plotW/Math.max(1,(end-start));float body=Math.max(0.5f,Math.min(8f,step*.62f));
        for(int i=start;i<end;i++){Candle b=candles.get(i);float x=left+(i-start+.5f)*step;float yh=map(b.high,min,max,top,bottom),yl=map(b.low,min,max,top,bottom),yo=map(b.open,min,max,top,bottom),yc=map(b.close,min,max,top,bottom);int color=b.close>=b.open?up:down;p.setColor(color);p.setStrokeWidth(1.2f);c.drawLine(x,yh,x,yl,p);p.setStyle(Paint.Style.FILL);c.drawRect(x-body/2,Math.min(yo,yc),x+body/2,Math.max(Math.max(yo,yc),Math.min(yo,yc)+1),p);}
        drawEma(c,start,end,left,step,min,max,top,bottom);
        p.setColor(0xFF718096);p.setTextSize(9);c.drawText("300 bar window",left,bottom+17,p);
    }
    private void drawEma(Canvas c,int start,int end,float left,float step,double min,double max,float top,float bottom){
        int period;try{period=Integer.parseInt(ema);}catch(Exception ex){return;}if(period<2)return;
        double alpha=2d/(period+1d),value=Double.NaN;Path path=new Path();boolean begun=false;
        for(int i=0;i<end;i++){double close=candles.get(i).close;value=Double.isNaN(value)?close:close*alpha+value*(1-alpha);if(i>=start){float x=left+(i-start+.5f)*step,y=map(value,min,max,top,bottom);if(!begun){path.moveTo(x,y);begun=true;}else path.lineTo(x,y);}}
        p.setColor(0xFFFFD166);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void level(Canvas c,float l,float r,float y,int color,String label){p.setColor(color);p.setStrokeWidth(1.2f);p.setStyle(Paint.Style.STROKE);c.drawLine(l,y,r,y,p);p.setStyle(Paint.Style.FILL);p.setTextSize(9);c.drawText(label,r+3,y-3,p);}
    private float map(double v,double min,double max,float top,float bottom){return bottom-(float)((v-min)/(max-min))*(bottom-top);}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX;lastX=e.getX();if(Math.abs(dx)>2&&!candles.isEmpty()){panBars=Math.max(0,Math.min(candles.size()-1,panBars+Math.round(dx/Math.max(2,(getWidth()-80)/300f))));invalidate();}return true;}return e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL;}
}
