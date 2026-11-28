package com.xauusdpro99.data.candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CandleEngine {
    public enum DataKind { NATIVE, AGGREGATED, UNAVAILABLE }
    public enum SourceStatus { LIVE, STALE, UNAVAILABLE }

    public static final int REQUEST_LIMIT = 1000;
    public static final int RETAIN_CLOSED = 300;

    public static final class Snapshot {
        public final CandleTimeframe timeframe;
        public final List<Candle> closed;
        public final Candle running;
        public final DataKind kind;
        public final SourceStatus sourceStatus;
        public final String source;
        public final long lastResponseAtMs;
        public final long lastCandleOpenTimeMs;
        public final int rejectedCount;
        public final int gapCount;

        Snapshot(CandleTimeframe timeframe, List<Candle> closed, Candle running,
                 DataKind kind, SourceStatus sourceStatus, String source, long lastResponseAtMs,
                 long lastCandleOpenTimeMs, int rejectedCount, int gapCount) {
            this.timeframe = timeframe;
            this.closed = Collections.unmodifiableList(closed);
            this.running = running;
            this.kind = kind;
            this.sourceStatus = sourceStatus;
            this.source = source;
            this.lastResponseAtMs = lastResponseAtMs;
            this.lastCandleOpenTimeMs = lastCandleOpenTimeMs;
            this.rejectedCount = rejectedCount;
            this.gapCount = gapCount;
        }
    }

    private final CandleTimeframe timeframe;
    private final TreeMap<Long, Candle> closed = new TreeMap<>();
    private Candle running;
    private DataKind kind = DataKind.UNAVAILABLE;
    private String source = "Belum ada sumber";
    private long lastResponseAtMs;
    private int rejectedCount;
    private long latestResponseClosedAtMs;

    public CandleEngine(CandleTimeframe timeframe) { this.timeframe = timeframe; }

    public synchronized void markUnavailable(String reason) {
        lastResponseAtMs = 0;
        latestResponseClosedAtMs = 0;
        running = null;
        if (closed.isEmpty()) kind = DataKind.UNAVAILABLE;
        source = reason == null ? "Tidak tersedia" : reason;
    }

    public synchronized void replaceFromServer(List<Candle> bars, long receivedAtMs, String sourceName) {
        if (!timeframe.nativeSupported) throw new IllegalArgumentException("Timeframe bukan native BiQuote: " + timeframe);
        TreeMap<Long, Candle> nextClosed = new TreeMap<>();
        Candle nextRunning = null;
        int rejected = 0;
        if (bars != null) {
            for (Candle c : bars) {
                if (!isValid(c, receivedAtMs) || c.timeframe != timeframe) { rejected++; continue; }
                if (c.isOpen) {
                    if (nextRunning == null || c.openTimeMs > nextRunning.openTimeMs) nextRunning = c;
                } else if (c.isClosedAt(receivedAtMs)) {
                    if (nextClosed.put(c.openTimeMs, c) != null) rejected++;
                } else {
                    rejected++;
                }
            }
        }
        latestResponseClosedAtMs = nextClosed.isEmpty() ? 0 : nextClosed.lastEntry().getValue().closeTimeMs();
        closed.putAll(nextClosed);
        while (closed.size() > RETAIN_CLOSED) closed.pollFirstEntry();
        running = nextRunning;
        kind = DataKind.NATIVE;
        source = sourceName == null ? "BiQuote OHLC REST" : sourceName;
        lastResponseAtMs = (nextRunning != null || latestResponseClosedAtMs > 0) ? receivedAtMs : 0;
        rejectedCount = rejected;
    }

    public synchronized void replaceAggregated(List<Candle> bars, long receivedAtMs, String sourceName) {
        replaceAggregated(bars, receivedAtMs, sourceName, receivedAtMs);
    }

    public synchronized void replaceAggregated(List<Candle> bars, long receivedAtMs, String sourceName, long networkResponseAtMs) {
        if (!timeframe.isAggregatedFromM1() && !timeframe.isAggregatedFromH4() && !timeframe.isAggregatedFromD1()) throw new IllegalArgumentException("Bukan timeframe agregasi");
        TreeMap<Long, Candle> next = new TreeMap<>();
        int rejected = 0;
        if (bars != null) for (Candle c : bars) {
            if (c == null || c.timeframe != timeframe || c.isOpen || !isValid(c, receivedAtMs)
                    || !c.isClosedAt(receivedAtMs) || next.put(c.openTimeMs, c) != null) {
                rejected++;
            }
        }
        while (next.size() > RETAIN_CLOSED) next.pollFirstEntry();
        closed.clear();
        closed.putAll(next);
        running = null;
        kind = DataKind.AGGREGATED;
        source = sourceName == null ? "Agregasi dari M1" : sourceName;
        lastResponseAtMs = next.isEmpty() ? 0 : networkResponseAtMs;
        rejectedCount = rejected;
    }

    /** Cache is historical only; never advances network freshness. */
    public synchronized void restoreNativeHistory(List<Candle> bars, long nowMs) {
        if (!timeframe.nativeSupported || bars == null) return;
        for (Candle bar : bars) {
            if (bar != null && bar.timeframe == timeframe && !bar.isOpen
                    && "BiQuote OHLC SQLite".equals(bar.source) && isValid(bar, nowMs)
                    && bar.receivedAtMs >= bar.closeTimeMs() && bar.receivedAtMs <= nowMs)
                closed.putIfAbsent(bar.openTimeMs, bar);
        }
        while (closed.size() > RETAIN_CLOSED) closed.pollFirstEntry();
        if (!closed.isEmpty() && lastResponseAtMs == 0) {
            kind = DataKind.NATIVE;
            source = "BiQuote OHLC SQLite (historical)";
        }
    }

    public synchronized Snapshot snapshot(long nowMs) {
        List<Candle> list = new ArrayList<>(closed.values());
        long latest = list.isEmpty() ? 0L : list.get(list.size() - 1).openTimeMs;
        int gaps = 0;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).openTimeMs - list.get(i - 1).openTimeMs > timeframe.durationMs) gaps++;
        }
        long freshnessLimit = Math.max(120_000L, timeframe.durationMs * 2L);
        boolean responseFresh = nowMs >= lastResponseAtMs && nowMs - lastResponseAtMs <= freshnessLimit;
        boolean candleFresh = running != null && running.openTimeMs <= nowMs && running.closeTimeMs() > nowMs;
        if (!candleFresh && kind == DataKind.NATIVE) {
            candleFresh = latestResponseClosedAtMs > 0 && nowMs >= latestResponseClosedAtMs
                    && nowMs - latestResponseClosedAtMs <= freshnessLimit;
        } else if (!candleFresh && !list.isEmpty()) {
            candleFresh = nowMs - list.get(list.size() - 1).closeTimeMs() <= freshnessLimit;
        }
        SourceStatus status = kind == DataKind.UNAVAILABLE
                ? SourceStatus.UNAVAILABLE
                : (lastResponseAtMs > 0 && responseFresh && candleFresh ? SourceStatus.LIVE : SourceStatus.STALE);
        return new Snapshot(timeframe, list, running, kind, status, source,
                lastResponseAtMs, latest, rejectedCount, gaps);
    }

    public static boolean isValid(Candle c, long nowMs) {
        if (c == null || !"XAUUSD".equalsIgnoreCase(c.symbol) || c.timeframe == null) return false;
        if (!Double.isFinite(c.open) || !Double.isFinite(c.high) || !Double.isFinite(c.low)
                || !Double.isFinite(c.close) || c.open <= 0 || c.high <= 0 || c.low <= 0 || c.close <= 0) return false;
        if (c.high < c.open || c.high < c.close || c.low > c.open || c.low > c.close || c.high < c.low) return false;
        if (c.openTimeMs <= 0 || c.openTimeMs > nowMs || !c.timeframe.isAligned(c.openTimeMs)) return false;
        if (!Double.isFinite(c.volume) || c.volume < 0 || c.tickVolume < 0) return false;
        if (c.isOpen && c.closeTimeMs() <= nowMs) return false;
        if (!c.isOpen && c.closeTimeMs() > nowMs) return false;
        return c.closeTimeMs() > c.openTimeMs;
    }
}
