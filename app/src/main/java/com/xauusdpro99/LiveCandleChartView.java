package com.xauusdpro99;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ScaleGestureDetector;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
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
    private float lastX,downX,downY;
    private boolean gestureMoved;
    private int panBars=0, visibleBars=300;
    private float dragRemainder;
    private final ScaleGestureDetector zoom;
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private final List<Float> labelRows=new ArrayList<>();
    private int omittedLabels;
    private final List<String> fullLabels=new ArrayList<>();
    private final int bg=0xFF0B1220, grid=0xFF263244, up=0xFF20C997, down=0xFFFF5C70;
    public LiveCandleChartView(Context c){super(c);setBackgroundColor(bg);setMinimumHeight((int)dp(100));zoom=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){visibleBars=Math.max(10,Math.min(300,Math.round(visibleBars/d.getScaleFactor())));invalidate();return true;}});}
    public void setData(List<Candle> data,List<Zone> z,String tf,double s,double r){
        candles=data==null?Collections.emptyList():new ArrayList<>(data);
        zones=z==null?Collections.emptyList():new ArrayList<>(z);
        String next=tf==null?"--":tf;if(!next.equals(timeframe))panBars=0;timeframe=next;panBars=Math.min(panBars,Math.max(0,candles.size()-1));support=s;resistance=r;
        invalidate();
    }
    public void setEma(String value){ema=value==null?"OFF":value;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w<80||h<100)return;
        float left=dp(6),right=w-Math.min(dp(156),w*.40f),top=dp(20),bottom=h-dp(25);if(right<=left||bottom<=top)return;labelRows.clear();fullLabels.clear();omittedLabels=0;float plotW=right-left,plotH=bottom-top;
        p.setColor(0xFFB9C7D8);p.setTextSize(dp(10));p.setStyle(Paint.Style.FILL);c.drawText(timeframe+"  |  "+candles.size()+" CLOSED CANDLES  |  DRAG / PINCH",left,dp(13),p);
        for(int i=0;i<=4;i++){float y=top+plotH*i/4f;p.setColor(grid);p.setStrokeWidth(1);c.drawLine(left,y,right,y,p);}
        if(candles.isEmpty()){p.setColor(0xFFB9C7D8);p.setTextSize(14);c.drawText("DATA UNAVAILABLE — menunggu OHLC",left+8,top+plotH/2,p);return;}
        int end=Math.max(0,candles.size()-panBars),start=Math.max(0,end-visibleBars);if(end<=start)end=Math.min(candles.size(),start+1);
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(int i=start;i<end;i++){Candle b=candles.get(i);min=Math.min(min,b.low);max=Math.max(max,b.high);}
        for(Zone z:zones){min=Math.min(min,z.lower);max=Math.max(max,z.upper);}
        if(Double.isFinite(support))min=Math.min(min,support);if(Double.isFinite(resistance))max=Math.max(max,resistance);
        double pad=Math.max((max-min)*.08,.01);min-=pad;max+=pad;final double range=max-min;
        for(int i=0;i<5;i++){double v=max-range*i/4d;float y=map(v,min,max,top,bottom);p.setColor(0xFF9AA9BC);p.setTextSize(dp(10));c.drawText(String.format(Locale.US,"%.2f",v),right+dp(3),y+dp(3),p);}
        for(Zone z:zones){float y1=map(z.upper,min,max,top,bottom),y2=map(z.lower,min,max,top,bottom);p.setColor(z.demand?0x3320A8FF:0x33FF5268);c.drawRect(left,Math.min(y1,y2),right,Math.max(y1,y2),p);p.setColor(z.demand?0xFF55B8FF:0xFFFF7888);label(c,String.format(Locale.US,"%s %.2f–%.2f",z.label==null?(z.demand?"DEMAND":"SUPPLY"):z.label,z.lower,z.upper),right+dp(52),Math.min(y1,y2)+dp(10),top,bottom);}
        if(Double.isFinite(resistance))level(c,left,right,map(resistance,min,max,top,bottom),0xFFFF6677,"R "+String.format(Locale.US,"%.2f",resistance),top,bottom);
        if(Double.isFinite(support))level(c,left,right,map(support,min,max,top,bottom),0xFF55B8FF,"S "+String.format(Locale.US,"%.2f",support),top,bottom);
        float step=plotW/Math.max(1,(end-start));float body=Math.max(0.5f,Math.min(8f,step*.62f));
        for(int i=start;i<end;i++){Candle b=candles.get(i);float x=left+(i-start+.5f)*step;float yh=map(b.high,min,max,top,bottom),yl=map(b.low,min,max,top,bottom),yo=map(b.open,min,max,top,bottom),yc=map(b.close,min,max,top,bottom);int color=b.close>=b.open?up:down;p.setColor(color);p.setStrokeWidth(1.2f);c.drawLine(x,yh,x,yl,p);p.setStyle(Paint.Style.FILL);c.drawRect(x-body/2,Math.min(yo,yc),x+body/2,Math.max(Math.max(yo,yc),Math.min(yo,yc)+1),p);}
        c.save();c.clipRect(left,top,right,bottom);drawEma(c,start,end,left,step,min,max,top,bottom);c.restore();
        p.setColor(0xFF9AA9BC);p.setTextSize(dp(9));SimpleDateFormat fmt=new SimpleDateFormat("MM-dd HH:mm",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));float lastRight=-1;for(int j=0;j<3;j++){int i=start+(end-start-1)*j/2;String t=fmt.format(new Date(candles.get(i).openTimeMs));float x=Math.max(left,Math.min(right-p.measureText(t),left+(i-start+.5f)*step-p.measureText(t)/2));if(x>lastRight+dp(3)){c.drawText(t,x,bottom+dp(12),p);lastRight=x+p.measureText(t);}}c.drawText("UTC | "+(end-start)+"/"+candles.size()+" bars",left,bottom+dp(23),p);if(!fullLabels.isEmpty()){p.setColor(0xFFB9C7D8);c.drawText("LEVELS ("+fullLabels.size()+")"+(omittedLabels>0?" +":""),right+dp(3),bottom+dp(15),p);setContentDescription("LIVE_CANDLE_CHART");}
    }
    private void drawEma(Canvas c,int start,int end,float left,float step,double min,double max,float top,float bottom){
        int period;try{period=Integer.parseInt(ema);}catch(Exception ex){return;}if(period<2)return;
        double alpha=2d/(period+1d),value=Double.NaN;Path path=new Path();boolean begun=false;
        for(int i=0;i<end;i++){double close=candles.get(i).close;value=Double.isNaN(value)?close:close*alpha+value*(1-alpha);if(i>=start){float x=left+(i-start+.5f)*step,y=map(value,min,max,top,bottom);if(!begun){path.moveTo(x,y);begun=true;}else path.lineTo(x,y);}}
        p.setColor(0xFFFFD166);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void level(Canvas c,float l,float r,float y,int color,String label,float top,float bottom){p.setColor(color);p.setStrokeWidth(1.2f);p.setStyle(Paint.Style.STROKE);c.drawLine(l,y,r,y,p);p.setStyle(Paint.Style.FILL);label(c,label,r+dp(52),y,top,bottom);}
    private void label(Canvas c,String value,float x,float desired,float top,float bottom){
        if(value==null)return;fullLabels.add(value);p.setTextSize(dp(9));float spacing=dp(12),best=Float.NaN,dist=Float.MAX_VALUE;
        for(float y=top+dp(9);y<=bottom;y+=spacing){boolean free=true;for(float used:labelRows)if(Math.abs(used-y)<spacing*.9f){free=false;break;}if(free&&Math.abs(y-desired)<dist){best=y;dist=Math.abs(y-desired);}}
        if(Float.isNaN(best)){omittedLabels++;return;}labelRows.add(best);float width=getWidth()-x-dp(2);String t=value;if(p.measureText(t)>width){while(t.length()>0&&p.measureText(t+"…")>width)t=t.substring(0,t.length()-1);t=t+"…";}c.drawText(t,x,best,p);
    }
    private float map(double v,double min,double max,float top,float bottom){return bottom-(float)((v-min)/(max-min))*(bottom-top);}
    @Override public boolean onTouchEvent(MotionEvent e){
        zoom.onTouchEvent(e);
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){lastX=e.getX();downX=lastX;downY=e.getY();gestureMoved=false;dragRemainder=0;return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){gestureMoved=true;return true;}
        if(action==MotionEvent.ACTION_POINTER_UP){lastX=e.getX(e.getActionIndex()==0?1:0);dragRemainder=0;return true;}
        if(action==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>=dp(8)||Math.abs(e.getY()-downY)>=dp(8))gestureMoved=true;float dx=e.getX()-lastX;lastX=e.getX();if(!zoom.isInProgress()&&e.getPointerCount()==1&&!candles.isEmpty()){dragRemainder+=dx/Math.max(1,(getWidth()-Math.min(dp(156),getWidth()*.40f)-dp(6))/Math.min(visibleBars,candles.size()));int shift=(int)dragRemainder;dragRemainder-=shift;panBars=Math.max(0,Math.min(candles.size()-1,panBars+shift));invalidate();}return true;}
        if(action==MotionEvent.ACTION_UP&&!gestureMoved&&Math.abs(e.getX()-downX)<dp(8)&&Math.abs(e.getY()-downY)<dp(8)&&downX>getWidth()-Math.min(dp(156),getWidth()*.40f)&&!fullLabels.isEmpty()){performClick();new android.app.AlertDialog.Builder(getContext()).setTitle("Chart levels").setMessage(android.text.TextUtils.join("\n",fullLabels)).setPositiveButton("OK",null).show();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
