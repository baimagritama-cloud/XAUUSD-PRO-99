package com.xauusdpro99.data.signal;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Frequency governor: cross-timeframe deduplication, per-direction cooldown,
 * candidate audit and honest session-only frequency statistics.
 * It never upgrades WAIT/NO_TRADE to an entry.
 */
public final class SignalFrequencyController {
    public static final long DEDUP_WINDOW_MS = 5L * 60L * 1000L;
    public static final long COOLDOWN_MS = 30L * 60L * 1000L;
    public static final int MAX_AUDIT_RECORDS = 2000;

    public static final class Decision {
        public final SignalScoringEngine.Result result;
        public final boolean published;
        public final String reason;
        Decision(SignalScoringEngine.Result result, boolean published, String reason) {
            this.result = result; this.published = published; this.reason = reason;
        }
    }

    private static final class Seen {
        final SignalScoringEngine.Direction direction;
        final String pattern;
        final long timestamp;
        Seen(SignalScoringEngine.Direction d, String p, long t) {
            direction=d; pattern=p; timestamp=t;
        }
    }

    private final List<Seen> seen = new ArrayList<>();
    private final List<Decision> audit = new ArrayList<>();
    private final EnumMap<SignalScoringEngine.Direction, Long> lastPublished =
            new EnumMap<>(SignalScoringEngine.Direction.class);

    public synchronized List<Decision> process(List<SignalScoringEngine.Result> results, long nowMs) {
        List<Decision> out = new ArrayList<>();
        if (results == null) return Collections.unmodifiableList(out);
        for (SignalScoringEngine.Result r : results) {
            if (r == null) continue;
            String fingerprint = patternFingerprint(r.setupKey);
            if (fingerprint.isEmpty()) {
                out.add(new Decision(r, false, "Tidak ada identitas pola untuk audit/deduplikasi"));
                continue;
            }
            long patternTime = patternTimestamp(r.setupKey);
            prune(nowMs);
            boolean duplicate = isDuplicate(r.direction, fingerprint, patternTime);
            if (duplicate) {
                out.add(new Decision(r, false, "Deduplikasi lintas timeframe: pola yang sama sudah tercatat"));
                continue;
            }
            seen.add(new Seen(r.direction, fingerprint, patternTime));
            boolean scoreOk = r.totalScore >= SignalScoringEngine.MIN_CANDIDATE_SCORE;
            boolean engineEntry = r.status == SignalScoringEngine.Status.BUY
                    || r.status == SignalScoringEngine.Status.SELL;
            String reason = "";
            boolean publish = false;
            if (!scoreOk) {
                reason = "Skor " + r.totalScore + " di bawah 57 — NO TRADE";
            } else if (!engineEntry) {
                reason = r.rejectedReasons.isEmpty()
                        ? "Hard gate engine belum lolos" : join(r.rejectedReasons);
            } else if (r.patternName == null || r.patternName.trim().isEmpty()) {
                reason = "Pola tidak teridentifikasi";
            } else if (r.rejectedReasons.stream().anyMatch(s -> s != null && s.toUpperCase(Locale.ROOT).contains("INVALIDATED"))) {
                reason = "Pola terinvalidasi";
            } else {
                Long last = lastPublished.get(r.direction);
                if (last != null && nowMs >= last && nowMs - last < COOLDOWN_MS) {
                    reason = "Cooldown " + (COOLDOWN_MS - (nowMs-last))/60000L + " menit untuk " + r.direction;
                } else {
                    publish = true;
                    reason = "Skor >=57, status entry engine lolos, tidak invalid, dedup dan cooldown lolos";
                    lastPublished.put(r.direction, nowMs);
                }
            }
            Decision decision = new Decision(r, publish, reason);
            out.add(decision);
            audit.add(decision);
            while (audit.size() > MAX_AUDIT_RECORDS) audit.remove(0);
        }
        return Collections.unmodifiableList(out);
    }

    private boolean isDuplicate(SignalScoringEngine.Direction d, String pattern, long timestamp) {
        for (Seen s : seen) {
            if (s.direction == d && s.pattern.equals(pattern)
                    && Math.abs(s.timestamp - timestamp) <= DEDUP_WINDOW_MS) return true;
        }
        return false;
    }

    private void prune(long nowMs) {
        long cutoff = nowMs - 24L*60L*60L*1000L;
        seen.removeIf(s -> s.timestamp < cutoff);
    }

    private static String patternFingerprint(String key) {
        if (key == null || key.isEmpty()) return "";
        String[] parts = key.split("\\|", 4);
        return parts.length == 4 ? parts[0] + "|" + parts[2] : "";
    }

    private static long patternTimestamp(String key) {
        if (key == null) return 0L;
        String[] parts = key.split("\\|", 4);
        if (parts.length != 4) return 0L;
        try { return Long.parseLong(parts[3]); } catch (NumberFormatException ex) { return 0L; }
    }

    private static String join(List<String> reasons) {
        StringBuilder b = new StringBuilder();
        for (String s : reasons) if (s != null && !s.isEmpty()) {
            if (b.length() > 0) b.append("; ");
            b.append(s);
        }
        return b.length() == 0 ? "Hard gate belum lolos" : b.toString();
    }

    public synchronized String report(long nowMs) {
        TimeZone tz = TimeZone.getTimeZone("Asia/Jakarta");
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(tz);
        TreeMap<String,Integer> perDay = new TreeMap<>();
        int buy=0, sell=0, rejected=0;
        EnumMap<SignalScoringEngine.Quality,Integer> quality = new EnumMap<>(SignalScoringEngine.Quality.class);
        EnumMap<Session,Integer> sessions = new EnumMap<>(Session.class);
        for (Decision d : audit) {
            if (d.published) {
                String date=day.format(new Date(d.result.evaluatedAtMs));
                perDay.put(date, perDay.getOrDefault(date,0)+1);
                if (d.result.direction == SignalScoringEngine.Direction.BUY) buy++; else sell++;
                quality.put(d.result.quality, quality.getOrDefault(d.result.quality,0)+1);
                Session session=session(d.result.evaluatedAtMs,tz);
                sessions.put(session,sessions.getOrDefault(session,0)+1);
            } else rejected++;
        }
        int total=buy+sell, days=perDay.size();
        double average=days==0?0d:(double)total/days;
        List<Integer> counts=new ArrayList<>(perDay.values());
        Collections.sort(counts);
        double median=counts.isEmpty()?0d:(counts.size()%2==1?counts.get(counts.size()/2):
                (counts.get(counts.size()/2-1)+counts.get(counts.size()/2))/2d);
        return "FREQUENCY AUDIT (catatan sesi aplikasi; WIB)\n"
                +"Sinyal diterbitkan: "+total+" | BUY: "+buy+" | SELL: "+sell+" | Kandidat ditolak: "+rejected+"\n"
                +"Rata-rata/hari aktif: "+fmt(average)+" | Median/hari aktif: "+fmt(median)
                +" | Hari tanpa sinyal: belum dapat dihitung sebelum rentang kalender penuh tersedia\n"
                +"Sinyal per timeframe: mengikuti timeframe pola pada detail kandidat; rekap agregat menunggu penyimpanan histori lintas sesi\n"
                +"Sinyal per sesi WIB: Asia 07-14="+sessions.getOrDefault(Session.ASIA,0)
                +", Eropa 14-19="+sessions.getOrDefault(Session.EUROPE,0)
                +", AS/overlap 19-03="+sessions.getOrDefault(Session.US,0)
                +", lainnya="+sessions.getOrDefault(Session.OTHER,0)+"\n"
                +"Kategori skor: HIGH="+quality.getOrDefault(SignalScoringEngine.Quality.HIGH_QUALITY,0)
                +", MODERATE="+quality.getOrDefault(SignalScoringEngine.Quality.MODERATE_QUALITY,0)
                +", EARLY="+quality.getOrDefault(SignalScoringEngine.Quality.EARLY_LOWER_QUALITY,0)+"\n"
                +"Win rate / expectancy / Profit Factor / max drawdown: BELUM TERSEDIA — belum ada outcome transaksi/backtest historis tervalidasi.";
    }

    private enum Session { ASIA, EUROPE, US, OTHER }
    private static Session session(long ms, TimeZone tz) {
        Calendar c=Calendar.getInstance(tz,Locale.US); c.setTimeInMillis(ms);
        int h=c.get(Calendar.HOUR_OF_DAY);
        if(h>=7&&h<14)return Session.ASIA;
        if(h>=14&&h<19)return Session.EUROPE;
        if(h>=19||h<3)return Session.US;
        return Session.OTHER;
    }
    private static String fmt(double n) { return String.format(Locale.US,"%.2f",n); }
}
