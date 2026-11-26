package com.xauusdpro99.data.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;

/** Strict economic-calendar ingestion. Missing provider values remain null; no synthetic events. */
public final class EconomicNewsEngine {
    public enum DataStatus { DATA_UNAVAILABLE, LIVE, STALE, INVALID }
    public enum Impact { HIGH, MEDIUM, LOW, UNKNOWN }
    public enum EventStatus { SCHEDULED, RELEASED, CANCELLED, UNKNOWN }
    public enum AlertKind { MINUTES_120, MINUTES_45, MINUTES_30, RELEASE, ACTUAL_AVAILABLE }

    public static final class Event {
        public final String id, name, country, source;
        public final long releaseAtMs, updatedAtMs;
        public final Impact impact;
        public final String previous, forecast, actual;
        public final EventStatus status;
        Event(String id, String name, String country, String source, long releaseAtMs,
              long updatedAtMs, Impact impact, String previous, String forecast,
              String actual, EventStatus status) {
            this.id=id; this.name=name; this.country=country; this.source=source;
            this.releaseAtMs=releaseAtMs; this.updatedAtMs=updatedAtMs; this.impact=impact;
            this.previous=previous; this.forecast=forecast; this.actual=actual; this.status=status;
        }
    }
    public static final class Alert {
        public final Event event; public final AlertKind kind;
        Alert(Event event, AlertKind kind) { this.event=event; this.kind=kind; }
        public String key() { return event.id + ":" + kind.name(); }
        public String title() { return event.name + " — " + kind.name(); }
    }
    public static final class Reaction {
        public final String status, initialDirection, persistence;
        public final Double before, firstClose, laterClose, change, reactionRange;
        Reaction(String status,String direction,String persistence,Double before,Double firstClose,
                 Double laterClose,Double change,Double reactionRange) {
            this.status=status; this.initialDirection=direction; this.persistence=persistence;
            this.before=before; this.firstClose=firstClose; this.laterClose=laterClose;
            this.change=change; this.reactionRange=reactionRange;
        }
        public static Reaction unavailable(String why) {
            return new Reaction(why,"UNKNOWN","UNKNOWN",null,null,null,null,null);
        }
    }

    private DataStatus dataStatus=DataStatus.DATA_UNAVAILABLE;
    private String source="Belum dikonfigurasi", detail="DATA UNAVAILABLE — sumber kalender belum dikonfigurasi";
    private long updatedAtMs;
    private List<Event> events=Collections.emptyList();
    private final Set<String> emitted=new HashSet<>();

    public synchronized DataStatus getDataStatus(){return dataStatus;}
    public synchronized String getSource(){return source;}
    public synchronized String getDetail(){return detail;}
    public synchronized long getUpdatedAtMs(){return updatedAtMs;}
    public synchronized List<Event> getEvents(){return events;}

    public synchronized boolean updateJson(String json,String sourceUrl,long nowMs) {
        if (json==null || json.trim().isEmpty() || !isHttps(sourceUrl)) {
            markUnavailable("URL HTTPS atau respons kalender tidak valid", nowMs); return false;
        }
        try {
            JsonElement root=JsonParser.parseString(json);
            JsonArray array=root.isJsonArray()?root.getAsJsonArray()
                    :(root.isJsonObject() && root.getAsJsonObject().has("data")
                    && root.getAsJsonObject().get("data").isJsonArray()
                    ?root.getAsJsonObject().getAsJsonArray("data"):null);
            if(array==null) throw new IllegalArgumentException("Format JSON bukan array kalender");
            List<Event> parsed=new ArrayList<>();
            String host=sourceUrl;
            for(JsonElement el:array){
                if(!el.isJsonObject()) continue;
                JsonObject o=el.getAsJsonObject();
                String country=str(o,"Country","country");
                String name=str(o,"Event","event","Name","name","Category","category");
                if(name==null || !isUsdCountry(country) || !isRelevant(name)) continue;
                Long time=parseTime(str(o,"Date","date","datetime","Time","time"));
                if(time==null) continue;
                String id=first(str(o,"CalendarId","CalendarID","calendarId","id","Ticker","ticker"),
                        country+"|"+name+"|"+time);
                String releaseSource=first(str(o,"Source","source"),sourceUrl);
                String previous=value(o,"Previous","previous");
                String forecast=value(o,"Forecast","forecast");
                String actual=value(o,"Actual","actual");
                String importance=str(o,"Importance","importance","Impact","impact");
                Impact impact=parseImpact(importance);
                String rawStatus=str(o,"Status","status");
                EventStatus status=actual!=null&&!actual.trim().isEmpty()?EventStatus.RELEASED:
                        (rawStatus!=null&&rawStatus.toLowerCase(Locale.US).contains("cancel")?EventStatus.CANCELLED:
                        (time>=nowMs?EventStatus.SCHEDULED:EventStatus.UNKNOWN));
                Long update=parseTime(str(o,"LastUpdate","lastUpdate","Updated","updated"));
                parsed.add(new Event(id,name,country,releaseSource,time,update==null?nowMs:update,impact,
                        previous,forecast,actual,status));
            }
            Collections.sort(parsed,(a,b)->Long.compare(a.releaseAtMs,b.releaseAtMs));
            if(parsed.isEmpty()) {
                events=Collections.emptyList(); dataStatus=DataStatus.INVALID;
                source=sourceUrl; detail="INVALID / EMPTY — tidak ada event USD relevan yang lolos validasi";
                updatedAtMs=nowMs; return false;
            }
            events=Collections.unmodifiableList(parsed);
            source=sourceUrl; updatedAtMs=nowMs; dataStatus=DataStatus.LIVE;
            detail="LIVE — "+parsed.size()+" event USD relevan; nilai kosong tetap tidak tersedia";
            return true;
        } catch(Exception ex) {
            markInvalid("JSON kalender gagal divalidasi: "+ex.getClass().getSimpleName(),sourceUrl,nowMs);
            return false;
        }
    }

    public synchronized void markUnavailable(String why,long nowMs) {
        dataStatus=DataStatus.DATA_UNAVAILABLE; detail="DATA UNAVAILABLE — "+why;
        if(updatedAtMs==0) source="Belum dikonfigurasi";
        updatedAtMs=nowMs; events=Collections.emptyList();
    }
    public synchronized void markStale(long nowMs) {
        if(dataStatus==DataStatus.LIVE && nowMs-updatedAtMs>30*60_000L) {
            dataStatus=DataStatus.STALE; detail="STALE — pembaruan kalender lebih dari 30 menit";
        }
    }
    private void markInvalid(String why,String url,long nowMs) {
        dataStatus=DataStatus.INVALID; source=url; detail="INVALID — "+why;
        updatedAtMs=nowMs; events=Collections.emptyList();
    }

    /** Emits each 120/45/30-minute alert once in a 60-second window; release/actual alerts are deduplicated. */
    public synchronized List<Alert> evaluateAlerts(long nowMs) {
        List<Alert> out=new ArrayList<>();
        markStale(nowMs);
        if(dataStatus!=DataStatus.LIVE) return out;
        for(Event e:events) {
            if(e.status==EventStatus.CANCELLED) continue;
            long mins=(e.releaseAtMs-nowMs)/60_000L;
            if(Math.abs(e.releaseAtMs-(nowMs+120*60_000L))<=30_000L)
                add(out,e,AlertKind.MINUTES_120);
            if(Math.abs(e.releaseAtMs-(nowMs+45*60_000L))<=30_000L)
                add(out,e,AlertKind.MINUTES_45);
            if(Math.abs(e.releaseAtMs-(nowMs+30*60_000L))<=30_000L)
                add(out,e,AlertKind.MINUTES_30);
            if(Math.abs(e.releaseAtMs-nowMs)<=30_000L)
                add(out,e,AlertKind.RELEASE);
            if(e.actual!=null && !e.actual.trim().isEmpty() && e.releaseAtMs<=nowMs+60_000L)
                add(out,e,AlertKind.ACTUAL_AVAILABLE);
        }
        return out;
    }
    private void add(List<Alert> out,Event e,AlertKind k) {
        Alert a=new Alert(e,k); if(emitted.add(a.key())) out.add(a);
    }

    public Reaction analyzeReaction(Event event,CandleEngine.Snapshot m1) {
        if(event==null || m1==null || m1.sourceStatus!=CandleEngine.SourceStatus.LIVE
                || m1.closed==null || m1.closed.size()<4)
            return Reaction.unavailable("M1 live belum cukup");
        if(m1.timeframe!=com.xauusdpro99.data.candle.CandleTimeframe.M1)
            return Reaction.unavailable("Timeframe reaksi harus M1");
        Candle before=null, first=null, later=null;
        int reactionBars=0;
        double hi=Double.NEGATIVE_INFINITY,lo=Double.POSITIVE_INFINITY;
        for(Candle c:m1.closed) {
            if(c.closeTimeMs()<=event.releaseAtMs) before=c;
            if(c.openTimeMs>=event.releaseAtMs && first==null) first=c;
            if(first!=null && c.openTimeMs>=first.openTimeMs
                    && c.openTimeMs<=first.openTimeMs+2*m1.timeframe.durationMs) {
                if(c.openTimeMs!=first.openTimeMs+reactionBars*m1.timeframe.durationMs || c.isOpen)
                    return Reaction.unavailable("Candle reaksi tidak berurutan atau belum tertutup");
                reactionBars++;
                hi=Math.max(hi,c.high); lo=Math.min(lo,c.low);
                if(c.openTimeMs>=first.openTimeMs+2*m1.timeframe.durationMs) later=c;
            }
        }
        if(before==null || first==null || later==null || reactionBars!=3
                || first.openTimeMs-event.releaseAtMs>=m1.timeframe.durationMs
                || event.releaseAtMs-before.closeTimeMs()>=m1.timeframe.durationMs)
            return Reaction.unavailable("Belum tersedia candle sebelum dan tiga candle sesudah rilis");
        double change=first.close-before.close;
        String direction=change>0?"NAIK":change<0?"TURUN":"DATAR";
        double laterChange=later.close-before.close;
        String persistence=(change==0 || laterChange==0)?"BELUM JELAS":
                (Math.signum(change)==Math.signum(laterChange)?"BERTAHAN":"BERBALIK");
        return new Reaction("ANALYZED",direction,persistence,before.close,first.close,later.close,
                change,hi-lo);
    }

    private static boolean isHttps(String u){return u!=null&&u.trim().toLowerCase(Locale.US).startsWith("https://");}
    private static boolean isUsdCountry(String c){return c!=null&&(c.equalsIgnoreCase("US")||c.equalsIgnoreCase("USA")||c.toLowerCase(Locale.US).contains("united states"));}
    private static boolean isRelevant(String n){
        String s=n.toLowerCase(Locale.US);
        String[] keys={"fomc","federal funds","interest rate","powell","cpi","consumer price","pce","personal consumption","nonfarm","non-farm","unemployment","average hourly earnings","employment situation","gross domestic product","gdp","ism manufacturing","ism services","retail sales","initial jobless","jobless claims","durable goods","producer price","ppi","consumer confidence","jolts"};
        for(String k:keys) if(s.contains(k)) return true; return false;
    }
    private static Impact parseImpact(String s){if(s==null)return Impact.UNKNOWN;String v=s.toLowerCase(Locale.US);if(v.contains("3")||v.contains("high"))return Impact.HIGH;if(v.contains("2")||v.contains("medium"))return Impact.MEDIUM;if(v.contains("1")||v.contains("low"))return Impact.LOW;return Impact.UNKNOWN;}
    private static String str(JsonObject o,String...keys){for(String k:keys)if(o.has(k)&&!o.get(k).isJsonNull())return o.get(k).getAsString();return null;}
    private static String value(JsonObject o,String...keys){
        String s=str(o,keys); if(s==null)return null; s=s.trim();
        return s.isEmpty()||s.equalsIgnoreCase("null")||s.equalsIgnoreCase("n/a")||s.equals("--")?null:s;
    }
    private static String first(String a,String b){return a==null||a.trim().isEmpty()?b:a;}
    private static Long parseTime(String s){
        if(s==null||s.trim().isEmpty())return null;
        try{return java.time.Instant.parse(s).toEpochMilli();}catch(Exception ignored){}
        String[] patterns={"yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd"};
        for(String p:patterns)try{SimpleDateFormat f=new SimpleDateFormat(p,Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));Date d=f.parse(s);if(d!=null)return d.getTime();}catch(ParseException ignored){}
        return null;
    }
}
