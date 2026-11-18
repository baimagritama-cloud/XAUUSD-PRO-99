package com.xauusdpro99.monitoring;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import androidx.core.app.ServiceCompat;
import com.xauusdpro99.data.alert.MarketAlertEngine;
import com.xauusdpro99.data.candle.*;
import com.xauusdpro99.data.connection.*;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.structure.*;
import com.xauusdpro99.data.signal.SignalScoringEngine;
import com.xauusdpro99.data.news.*;
import com.xauusdpro99.notification.*;
import com.xauusdpro99.voice.VoiceEngine;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * User-started foreground market monitor. It never claims to survive Force Stop.
 * Live quote uses SignalR; OHLC/calendar polling is deliberately rate-limited.
 */
public final class MarketMonitoringService extends Service
        implements BiquoteConnectionEngine.Listener, MultiTimeframeEngine.Listener {
    public static final String ACTION_START="com.xauusdpro99.monitor.START";
    public static final String ACTION_STOP="com.xauusdpro99.monitor.STOP";
    public static final String CHANNEL="xauusd_background_monitor";
    private static final int NOTIFICATION_ID=9901;
    private static final String RUNTIME="monitoring_runtime";
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService calendarExecutor=Executors.newSingleThreadExecutor();
    private final MarketAlertEngine alertEngine=new MarketAlertEngine();
    private final SignalScoringEngine signalEngine=new SignalScoringEngine();
    private final NotificationPolicy policy=new NotificationPolicy();
    private BiquoteConnectionEngine quoteEngine;
    private MultiTimeframeEngine candleEngine;
    private NotificationCenter notifications;
    private VoiceEngine voice;
    private EconomicNewsEngine news;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile BiquoteTick latestTick;
    private volatile long latestTickAt;
    private volatile BiquoteConnectionEngine.Status connection=BiquoteConnectionEngine.Status.DISCONNECTED;
    private volatile boolean networkAvailable=true;
    private volatile Map<CandleTimeframe,CandleEngine.Snapshot> snapshots=Collections.emptyMap();
    private volatile boolean stopping;
    private long lastCalendarPoll;

    public static void start(Context c) {
        Intent i=new Intent(c,MarketMonitoringService.class).setAction(ACTION_START);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    public static void stop(Context c) {
        c.startService(new Intent(c,MarketMonitoringService.class).setAction(ACTION_STOP));
    }
    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        notifications=new NotificationCenter(this);
        news=new EconomicNewsEngine();
        voice=new VoiceEngine(this,status->write("voice_status",status));
        quoteEngine=new BiquoteConnectionEngine(this);
        candleEngine=new MultiTimeframeEngine(this);
        registerNetworkCallback();
        getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putBoolean("service_running",true)
            .putString("service_status","STARTING").putLong("service_started_at",System.currentTimeMillis()).apply();
        updateForeground("Memulai pemantauan XAUUSD…");
        quoteEngine.connect();
        candleEngine.refreshAll();
        scheduler.scheduleAtFixedRate(()->{
            if(stopping)return;
            candleEngine.refreshAll();
            dispatchCalendarAlerts(System.currentTimeMillis());
            pollCalendar();
            refreshFreshness();
        },2,2,TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::pollCalendar,1,15,TimeUnit.MINUTES);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        updateForeground("Pemantauan aktif — menunggu data BiQuote");
        return START_STICKY;
    }
    @Override public android.os.IBinder onBind(Intent intent){return null;}
    @Override public void onTaskRemoved(Intent rootIntent){
        write("service_status","TASK REMOVED — foreground service may continue under Android policy");
        super.onTaskRemoved(rootIntent);
    }
    @Override public void onDestroy(){
        stopping=true;
        scheduler.shutdownNow();calendarExecutor.shutdownNow();
        unregisterNetworkCallback();
        if(quoteEngine!=null)quoteEngine.disconnect();
        if(candleEngine!=null)candleEngine.close();
        if(voice!=null)voice.shutdown();
        if(notifications!=null)notifications.close();
        getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putBoolean("service_running",false)
            .putString("service_status","STOPPED").putLong("service_stopped_at",System.currentTimeMillis()).apply();
        super.onDestroy();
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null&&nm.getNotificationChannel(CHANNEL)==null){
                NotificationChannel ch=new NotificationChannel(CHANNEL,"XAUUSD Background Monitoring",NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Notifikasi permanen selama pemantauan pasar aktif");
                ch.setShowBadge(false);nm.createNotificationChannel(ch);
            }
        }
    }
    private Notification buildNotification(String text){
        Intent open=new Intent(this,com.xauusdpro99.MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,9901,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("XAUUSD PRO 99 — Monitoring")
            .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void updateForeground(String text){
        if(Build.VERSION.SDK_INT>=34){
            ServiceCompat.startForeground(this,NOTIFICATION_ID,buildNotification(text),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }else if(Build.VERSION.SDK_INT>=29){
            ServiceCompat.startForeground(this,NOTIFICATION_ID,buildNotification(text),0);
        }else startForeground(NOTIFICATION_ID,buildNotification(text));
        write("service_status","RUNNING");
        write("persistent_notification",text);
    }
    private void updateNotification(String text){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(text));
        write("persistent_notification",text);
    }
    private void registerNetworkCallback(){
        try{
            connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(connectivity==null)return;
            networkCallback=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network n){
                    boolean wasUnavailable=!networkAvailable;
                    networkAvailable=true;log("NETWORK AVAILABLE — koneksi internet terdeteksi");
                    if(wasUnavailable&&quoteEngine!=null)quoteEngine.reconnectNow("network restored");
                }
                @Override public void onLost(Network n){
                    networkAvailable=false;connection=BiquoteConnectionEngine.Status.STALE;
                    log("NETWORK LOST — menunggu jaringan; quote tidak dianggap LIVE");
                    updateNotification("JARINGAN TERPUTUS — menunggu koneksi kembali");
                }
                @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){
                    networkAvailable=c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            };
            Network active=connectivity.getActiveNetwork();
            networkAvailable=active!=null;
            connectivity.registerDefaultNetworkCallback(networkCallback);
        }catch(Exception e){log("NETWORK CALLBACK gagal: "+e.getClass().getSimpleName());}
    }
    private void unregisterNetworkCallback(){
        try{if(connectivity!=null&&networkCallback!=null)connectivity.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}
    }
    private void refreshFreshness(){
        if(latestTickAt>0&&System.currentTimeMillis()-latestTickAt>15000L&&connection==BiquoteConnectionEngine.Status.LIVE){
            connection=BiquoteConnectionEngine.Status.STALE;
            updateNotification("QUOTE STALE — tidak ada tick >15 detik; menunggu reconnect");
        }
    }
    @Override public void onStatus(BiquoteConnectionEngine.Status s,String detail){
        connection=s;write("connection_status",s.name());log("CONNECTION "+s+": "+detail);
        updateNotification("Service RUNNING | BiQuote "+s+" | "+(networkAvailable?"Network tersedia":"Network terputus"));
    }
    @Override public void onTick(BiquoteTick tick,long received,Long sourceTime){
        latestTick=tick;latestTickAt=received;
        boolean valid=tick!=null&&tick.isValidFor("XAUUSD")&&!Boolean.TRUE.equals(tick.stale)
            &&!"closed".equalsIgnoreCase(tick.marketState)&&(tick.quoteAgeSeconds==null||tick.quoteAgeSeconds<=300);
        updateNotification("BiQuote "+(valid?"LIVE":"STALE")+" | XAUUSD "+String.format(Locale.US,"%.2f",tick==null?0:tick.mid)
            +" | diterima "+DateFormat.getTimeInstance().format(new Date(received)));
        write("last_tick_at",Long.toString(received));
        if(valid)pollCalendarIfDue();
    }
    @Override public void onLog(String message){log(message);}
    @Override public void onUpdated(Map<CandleTimeframe,CandleEngine.Snapshot> data){
        snapshots=data;
        try{
            long now=System.currentTimeMillis();
            Map<CandleTimeframe,IndicatorEngine.Result> inds=new EnumMap<>(CandleTimeframe.class);
            Map<CandleTimeframe,MarketStructureEngine.Result> structs=new EnumMap<>(CandleTimeframe.class);
            Map<CandleTimeframe,SupplyDemandEngine.Result> zones=new EnumMap<>(CandleTimeframe.class);
            Map<CandleTimeframe,List<CandlePatternEngine.Pattern>> patterns=new EnumMap<>(CandleTimeframe.class);
            IndicatorEngine indicatorEngine=new IndicatorEngine();
            MarketStructureEngine structureEngine=new MarketStructureEngine();
            SupplyDemandEngine zoneEngine=new SupplyDemandEngine();
            CandlePatternEngine patternEngine=new CandlePatternEngine();
            CandleEngine.Snapshot daily=data.get(CandleTimeframe.D1);
            for(CandleTimeframe tf:CandleTimeframe.values()){
                CandleEngine.Snapshot snap=data.get(tf);
                IndicatorEngine.Result ir=indicatorEngine.calculate(snap,daily,now);inds.put(tf,ir);
                MarketStructureEngine.Result sr=structureEngine.analyze(snap,now);structs.put(tf,sr);
                double price=lastClose(snap);
                SupplyDemandEngine.Result zr=zoneEngine.analyze(snap,price,now);zones.put(tf,zr);
                List<CandlePatternEngine.PriceZone> pz=new ArrayList<>();
                for(SupplyDemandEngine.Zone z:zr.activeZones())pz.add(new CandlePatternEngine.PriceZone(
                    z.type==SupplyDemandEngine.ZoneType.DEMAND?CandlePatternEngine.ZoneType.DEMAND:CandlePatternEngine.ZoneType.SUPPLY,
                    z.lower,z.upper,z.label));
                CandleTimeframe higher=higher(tf);
                IndicatorEngine.Result hi=higher==null?null:indicatorEngine.calculate(data.get(higher),daily,now);
                patterns.put(tf,patternEngine.detect(snap,new CandlePatternEngine.Context(ir,hi,pz),now));
            }
            BiquoteTick t=latestTick;
            boolean quoteValid=t!=null&&t.isValidFor("XAUUSD")&&!Boolean.TRUE.equals(t.stale)
                &&!"closed".equalsIgnoreCase(t.marketState)&&(t.quoteAgeSeconds==null||t.quoteAgeSeconds<=300);
            double bid=t==null||t.bid==null?Double.NaN:t.bid,ask=t==null||t.ask==null?Double.NaN:t.ask;
            double spread=t==null?Double.NaN:(t.spread!=null?t.spread:(t.ask!=null&&t.bid!=null?t.ask-t.bid:Double.NaN));
            List<SignalScoringEngine.Result> signals=signalEngine.evaluate(new SignalScoringEngine.Input(
                data,inds,structs,zones,patterns,connection==BiquoteConnectionEngine.Status.LIVE&&networkAvailable,
                quoteValid,latestTickAt,now,bid,ask,spread,SignalScoringEngine.DEFAULT_MAX_SPREAD));
            List<MarketAlertEngine.Alert> events=alertEngine.evaluate(new MarketAlertEngine.Input(data,inds,structs,zones,patterns,signals,now));
            boolean fresh=quoteValid&&connection==BiquoteConnectionEngine.Status.LIVE&&networkAvailable
                &&latestTickAt>0&&now>=latestTickAt&&now-latestTickAt<=15000L;
            deliverMarketEvents(events,fresh);
            write("monitoring_status","CANDLES_UPDATED "+data.size()+" TF | alerts="+events.size());
        }catch(Throwable ex){log("MONITOR PIPELINE ERROR: "+ex.getClass().getSimpleName()+" "+ex.getMessage());}
    }
    private void deliverMarketEvents(List<MarketAlertEngine.Alert> events,boolean fresh){
        SharedPreferences p=getSharedPreferences("notification_settings",MODE_PRIVATE);
        boolean enabled=p.getBoolean("notifications_enabled",true),voiceOn=p.getBoolean("voice_enabled",true);
        int cooldown=Math.max(0,Math.min(1440,p.getInt("cooldown_minutes",5)));
        Set<String> types=p.getStringSet("alert_types",null);
        boolean quiet=p.getBoolean("quiet_enabled",false)&&NotificationPolicy.isQuietHours(
            NotificationPolicy.currentMinuteOfDay(),p.getInt("quiet_start",1320),p.getInt("quiet_end",420));
        for(MarketAlertEngine.Alert a:events){
            boolean typeOn=types==null||types.contains(a.type.name());
            if(!policy.accept(a.type.name(),a.timeframe,System.currentTimeMillis(),fresh,typeOn,cooldown))continue;
            String title="XAUUSD — "+a.type.name()+" ("+a.timeframe+")";
            String msg="Harga "+String.format(Locale.US,"%.2f",a.price)+". "+a.reason;
            long now=System.currentTimeMillis();
            long id=notifications.recordAlert(title,msg,a.timestampMs,now,cooldown*60000L);
            if(id<=0)continue;
            if(enabled)notifications.show(this,id,title,msg);
            if(voiceOn&&!quiet&&voice!=null)voice.speakAlert(a.type.name(),a.timeframe,a.price,a.reason,a.status,fresh);
        }
    }
    private void pollCalendarIfDue(){if(System.currentTimeMillis()-lastCalendarPoll>15*60_000L)pollCalendar();}
    private void pollCalendar(){
        long now=System.currentTimeMillis();if(now-lastCalendarPoll<60_000L)return;lastCalendarPoll=now;
        SharedPreferences prefs=getSharedPreferences("economic_news_settings",MODE_PRIVATE);
        String endpoint=prefs.getString("calendar_endpoint","");
        String key=CalendarCredentialStore.load(this);
        if(endpoint.isEmpty()||key.isEmpty()){news.markUnavailable("kalender belum dikonfigurasi untuk background monitoring",now);return;}
        calendarExecutor.execute(()->{
            try{
                EconomicCalendarClient.Response r=EconomicCalendarClient.fetch(endpoint,key);
                if(news.updateJson(r.body,r.source,r.receivedAtMs)){
                    log("ECONOMIC CALENDAR LIVE — "+news.getEvents().size()+" event");
                    dispatchCalendarAlerts(r.receivedAtMs);
                }
            }catch(Exception e){news.markUnavailable("calendar fetch gagal: "+e.getClass().getSimpleName(),System.currentTimeMillis());log("CALENDAR FETCH ERROR: "+e.getMessage());}
        });
    }
    private void dispatchCalendarAlerts(long nowMs){
        for(EconomicNewsEngine.Alert a:news.evaluateAlerts(nowMs)){
            SharedPreferences np=getSharedPreferences("notification_settings",MODE_PRIVATE);
            Set<String> types=np.getStringSet("alert_types",null);
            if(types!=null&&!types.contains("NEWS_ALERT"))continue;
            String title="XAUUSD — ECONOMIC NEWS — "+a.title();
            String msg=a.event.name+" | "+a.event.country+" | "+a.event.impact+" | "+a.event.status
                +" | Previous="+(a.event.previous==null?"DATA UNAVAILABLE":a.event.previous)
                +" | Forecast="+(a.event.forecast==null?"DATA UNAVAILABLE":a.event.forecast)
                +" | Actual="+(a.event.actual==null?"DATA UNAVAILABLE":a.event.actual);
            long now=System.currentTimeMillis();
            int cooldown=Math.max(0,Math.min(1440,np.getInt("cooldown_minutes",5)));
            long id=notifications.recordAlert(title,msg,now,now,cooldown*60000L);
            if(id>0&&np.getBoolean("notifications_enabled",true))notifications.show(this,id,title,msg);
            boolean quiet=np.getBoolean("quiet_enabled",false)&&NotificationPolicy.isQuietHours(
                NotificationPolicy.currentMinuteOfDay(),np.getInt("quiet_start",1320),np.getInt("quiet_end",420));
            if(id>0&&np.getBoolean("voice_enabled",true)&&!quiet&&voice!=null)
                voice.speakAlert("NEWS_ALERT","NEWS",Double.NaN,a.title(),"INFO",true);
        }
    }
    private static double lastClose(CandleEngine.Snapshot s){return s==null||s.closed.isEmpty()?Double.NaN:s.closed.get(s.closed.size()-1).close;}
    private static CandleTimeframe higher(CandleTimeframe t){
        switch(t){case M1:case M2:case M3:case M5:case M15:return CandleTimeframe.H1;
        case M30:case H1:return CandleTimeframe.H4;case H4:case H8:case H12:return CandleTimeframe.D1;default:return null;}
    }
    private void log(String message){
        String old=getSharedPreferences(RUNTIME,MODE_PRIVATE).getString("reconnect_log","");
        String line=DateFormat.getDateTimeInstance().format(new Date())+" "+message;
        String combined=(old.isEmpty()?line:old+"\n"+line);
        String[] lines=combined.split("\n");StringBuilder keep=new StringBuilder();
        for(int i=Math.max(0,lines.length-40);i<lines.length;i++){if(keep.length()>0)keep.append("\n");keep.append(lines[i]);}
        getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putString("reconnect_log",keep.toString()).apply();
    }
    private void write(String key,String value){getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putString(key,value).apply();}
    private void write(String key,long value){getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putLong(key,value).apply();}
}
