package com.xauusdpro99.data.candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MultiTimeframeEngine {
    public interface Listener {
        void onUpdated(Map<CandleTimeframe, CandleEngine.Snapshot> snapshots);
        void onLog(String message);
    }

    private final EnumMap<CandleTimeframe, CandleEngine> engines = new EnumMap<>(CandleTimeframe.class);
    private final BiquoteOhlcClient client = new BiquoteOhlcClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final CandleStore store;

    public MultiTimeframeEngine(Listener listener) {
        this(null, listener);
    }

    public MultiTimeframeEngine(android.content.Context context, Listener listener) {
        this.listener = listener;
        this.store = context == null ? null : new CandleStore(context);
        for (CandleTimeframe tf : CandleTimeframe.values()) engines.put(tf, new CandleEngine(tf));
        for (CandleTimeframe tf : CandleTimeframe.values()) {
            if (!tf.nativeSupported && !tf.isAggregatedFromM1()) {
                engines.get(tf).markUnavailable("UNSUPPORTED — interval tidak tercantum pada dokumentasi BiQuote");
            }
        }
    }

    public void refreshAll() {
        worker.execute(() -> {
            List<Candle> m1 = null;
            if (store != null) {
                for (CandleTimeframe tf : CandleTimeframe.values()) {
                    if (!tf.nativeSupported) continue;
                    try { engines.get(tf).restoreNativeHistory(store.loadNative(tf, CandleEngine.RETAIN_CLOSED, System.currentTimeMillis()), System.currentTimeMillis()); }
                    catch (Exception error) { listener.onLog(tf + ": SQLite restore failed: " + error.getMessage()); }
                }
            }
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                if (!tf.nativeSupported) continue;
                try {
                    List<Candle> bars = client.fetch(tf);
                    long received = System.currentTimeMillis();
                    engines.get(tf).replaceFromServer(bars, received, "BiQuote OHLC REST (" + tf.biquoteInterval + ")");
                    if (store != null) {
                        try { store.saveNative(tf, bars, received); }
                        catch (Exception error) { listener.onLog(tf + ": SQLite save failed: " + error.getMessage()); }
                    }
                    if (tf == CandleTimeframe.M1) m1 = bars;
                    listener.onLog(tf + ": respons " + bars.size() + " bar; disimpan "
                            + engines.get(tf).snapshot(received).closed.size() + " candle tertutup.");
                } catch (Exception error) {
                    engines.get(tf).markUnavailable("ERROR OHLC: " + error.getMessage());
                    listener.onLog(tf + ": gagal mengambil OHLC — " + error.getMessage());
                }
            }
            long now = System.currentTimeMillis();
            List<Candle> history = new ArrayList<>();
            if (store != null) {
                try { history.addAll(store.loadNative(CandleTimeframe.M1, 10000, now)); }
                catch (Exception error) { listener.onLog("M1: SQLite aggregation history failed: " + error.getMessage()); }
            }
            history.addAll(engines.get(CandleTimeframe.M1).snapshot(now).closed);
            if (m1 != null) history.addAll(m1);
            CandleEngine.Snapshot m1State = engines.get(CandleTimeframe.M1).snapshot(now);
            if (!history.isEmpty()) {
                List<Candle> sorted = mergeM1History(history, now);
                for (CandleTimeframe tf : new CandleTimeframe[]{CandleTimeframe.M2, CandleTimeframe.M3}) {
                    List<Candle> aggregated = aggregateClosedM1(sorted, tf, now);
                    engines.get(tf).replaceAggregated(aggregated, now, "AGGREGATED dari BiQuote M1 REST/SQLite (bukan candle native)",
                            m1State.sourceStatus == CandleEngine.SourceStatus.LIVE ? m1State.lastResponseAtMs : 0);
                    listener.onLog(tf + ": hasil agregasi lengkap " + aggregated.size() + " candle.");
                }
            } else {
                engines.get(CandleTimeframe.M2).markUnavailable("UNAVAILABLE — data M1 tidak berhasil diambil");
                engines.get(CandleTimeframe.M3).markUnavailable("UNAVAILABLE — data M1 tidak berhasil diambil");
            }
            listener.onUpdated(snapshotAll(System.currentTimeMillis()));
        });
    }

    static List<Candle> mergeM1History(List<Candle> input, long nowMs) {
        java.util.TreeMap<Long,Candle> unique = new java.util.TreeMap<>();
        for (Candle c : input) {
            if (c == null || c.timeframe != CandleTimeframe.M1 || c.isOpen
                    || !CandleEngine.isValid(c, nowMs) || c.receivedAtMs < c.closeTimeMs()
                    || c.receivedAtMs > nowMs
                    || !("BiQuote OHLC REST".equals(c.source) || "BiQuote OHLC SQLite".equals(c.source))) continue;
            Candle previous = unique.get(c.openTimeMs);
            if (previous == null || c.receivedAtMs >= previous.receivedAtMs) unique.put(c.openTimeMs, c);
        }
        return new ArrayList<>(unique.values());
    }

    static List<Candle> aggregateClosedM1(List<Candle> input, CandleTimeframe target, long nowMs) {
        if (target != CandleTimeframe.M2 && target != CandleTimeframe.M3) {
            throw new IllegalArgumentException("Hanya M2/M3 yang diagregasi dari M1");
        }
        int groupSize = target == CandleTimeframe.M2 ? 2 : 3;
        java.util.TreeMap<Long,Candle> unique = new java.util.TreeMap<>();
        for (Candle c : input) {
            if (c != null && c.timeframe == CandleTimeframe.M1 && !c.isOpen
                    && c.isClosedAt(nowMs) && CandleEngine.isValid(c, nowMs)) unique.put(c.openTimeMs,c);
        }
        List<Candle> m1 = new ArrayList<>(unique.values());
        List<Candle> output = new ArrayList<>();
        for (int i = 0; i + groupSize <= m1.size();) {
            Candle first = m1.get(i);
            long bucket = Math.floorDiv(first.openTimeMs, target.durationMs) * target.durationMs;
            if (first.openTimeMs != bucket) { i++; continue; }
            boolean complete = true;
            double high = first.high, low = first.low, volume = 0d;
            long tickVolume = 0L;
            for (int j = 0; j < groupSize; j++) {
                Candle part = m1.get(i + j);
                if (part.openTimeMs != bucket + j * CandleTimeframe.M1.durationMs) {
                    complete = false; break;
                }
                high = Math.max(high, part.high);
                low = Math.min(low, part.low);
                volume += part.volume;
                tickVolume += part.tickVolume;
            }
            if (complete && bucket + target.durationMs <= nowMs) {
                Candle last = m1.get(i + groupSize - 1);
                output.add(new Candle("XAUUSD", target, bucket, first.open, high, low, last.close,
                        volume, tickVolume, false, "AGGREGATED dari BiQuote M1", nowMs));
                i += groupSize;
            } else {
                i++;
            }
        }
        if (output.size() > CandleEngine.RETAIN_CLOSED) {
            return new ArrayList<>(output.subList(output.size() - CandleEngine.RETAIN_CLOSED, output.size()));
        }
        return output;
    }

    public Map<CandleTimeframe, CandleEngine.Snapshot> snapshotAll(long nowMs) {
        EnumMap<CandleTimeframe, CandleEngine.Snapshot> result = new EnumMap<>(CandleTimeframe.class);
        for (Map.Entry<CandleTimeframe, CandleEngine> entry : engines.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot(nowMs));
        }
        return Collections.unmodifiableMap(result);
    }

    public void close() {
        if (store != null && !worker.isShutdown()) worker.execute(store::close);
        worker.shutdown();
    }
}
