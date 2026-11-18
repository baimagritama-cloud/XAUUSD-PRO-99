package com.xauusdpro99.data.connection;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-instance-per-owner BiQuote stream coordinator.
 * SignalR Java client supports WebSockets + JSON only; reconnect is deliberately
 * managed here with exponential backoff rather than assumed to be automatic.
 */
public final class BiquoteConnectionEngine {
    public enum Status { LIVE, STALE, RECONNECTING, DISCONNECTED, ERROR }

    public interface Listener {
        void onStatus(Status status, String detail);
        void onTick(BiquoteTick tick, long receivedAtEpochMs, Long sourceTimestampEpochMs);
        void onLog(String message);
    }

    private static final String HUB_URL = "https://biquote.io/hubs/tick";
    private static final String REST_URL = "https://biquote.io/api/XAUUSD?allowStale=false";
    private static final String SYMBOL = "XAUUSD";
    private static final long STALE_AFTER_MS = 15_000L;
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    private volatile HubConnection hub;
    private volatile Status status = Status.DISCONNECTED;
    private volatile long lastReceiveAtMs;
    private volatile long nextBackoffMs = INITIAL_BACKOFF_MS;

    public BiquoteConnectionEngine(Listener listener) {
        this.listener = listener;
        scheduler.scheduleAtFixedRate(this::checkFreshness, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void connect() {
        if (closed.get() || !connecting.compareAndSet(false, true)) return;
        setStatus(Status.RECONNECTING, "Menghubungkan ke BiQuote SignalR…");
        io.execute(() -> {
            try {
                HubConnection connection = HubConnectionBuilder.create(HUB_URL).build();
                connection.on("ReceiveTick", (BiquoteTick tick) -> acceptTick(tick), BiquoteTick.class);
                connection.onClosed(error -> {
                    started.set(false);
                    if (!closed.get()) scheduleReconnect("Hub ditutup: " + messageOf(error));
                });
                hub = connection;
                connection.start().blockingAwait();
                connection.send("Subscribe", (Object) new String[]{SYMBOL});
                started.set(true);
                connecting.set(false);
                nextBackoffMs = INITIAL_BACKOFF_MS;
                log("SignalR connected; Subscribe([XAUUSD]) terkirim.");
                setStatus(lastReceiveAtMs == 0 ? Status.STALE : Status.LIVE,
                        "Terhubung; menunggu ReceiveTick XAUUSD.");
            } catch (Throwable error) {
                connecting.set(false);
                started.set(false);
                setStatus(Status.ERROR, "Koneksi gagal: " + messageOf(error));
                scheduleReconnect("Gagal start/subscribe: " + messageOf(error));
            }
        });
    }

    /** REFRESH invokes the documented GetLatestTick hub method; REST is a fallback only. */
    public void refresh() {
        io.execute(() -> {
            HubConnection current = hub;
            if (current != null && started.get()) {
                try {
                    BiquoteTick tick = current.invoke(BiquoteTick.class, "GetLatestTick", SYMBOL).blockingGet();
                    acceptTick(tick);
                    log("REFRESH berhasil melalui SignalR GetLatestTick.");
                    return;
                } catch (Throwable error) {
                    log("GetLatestTick gagal: " + messageOf(error) + "; mencoba REST snapshot.");
                }
            }
            fetchRestSnapshot();
        });
    }

    private void fetchRestSnapshot() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(REST_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IllegalStateException("HTTP " + code + " tanpa body");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + ": " + body);
            }
            BiquoteTick tick = gson.fromJson(body.toString(), BiquoteTick.class);
            acceptTick(tick);
            log("REFRESH berhasil melalui REST snapshot.");
        } catch (Throwable error) {
            setStatus(Status.ERROR, "Refresh gagal: " + messageOf(error));
            log("REST refresh error: " + messageOf(error));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void acceptTick(BiquoteTick tick) {
        if (tick == null || !tick.isValidFor(SYMBOL)) {
            setStatus(Status.ERROR, "Payload tick tidak valid atau simbol bukan XAUUSD.");
            log("Payload ReceiveTick ditolak: null/field harga tidak valid/simbol salah.");
            return;
        }
        long received = System.currentTimeMillis();
        Long sourceTime = parseTimestamp(tick.timestamp != null ? tick.timestamp : tick.time);
        if (Boolean.TRUE.equals(tick.stale)
                || "closed".equalsIgnoreCase(tick.marketState)
                || (tick.quoteAgeSeconds != null && tick.quoteAgeSeconds > 300)) {
            lastReceiveAtMs = received;
            setStatus(Status.STALE, "BiQuote menyatakan quote stale/market closed.");
        } else {
            lastReceiveAtMs = received;
            setStatus(Status.LIVE, "Tick diterima dari " + safe(tick.source));
        }
        main.post(() -> listener.onTick(tick, received, sourceTime));
    }

    private Long parseTimestamp(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return Instant.parse(value).toEpochMilli(); }
        catch (Exception ignored) { return null; }
    }

    private void checkFreshness() {
        if (closed.get()) return;
        long last = lastReceiveAtMs;
        if (last > 0 && System.currentTimeMillis() - last > STALE_AFTER_MS
                && status == Status.LIVE) {
            setStatus(Status.STALE, "Tidak ada tick baru selama lebih dari 15 detik.");
        }
    }

    private void scheduleReconnect(String reason) {
        if (closed.get()) return;
        long delay = nextBackoffMs;
        nextBackoffMs = Math.min(MAX_BACKOFF_MS, nextBackoffMs * 2);
        log(reason + " Reconnect dalam " + delay + " ms.");
        setStatus(Status.RECONNECTING, "Mencoba kembali dalam " + delay + " ms.");
        scheduler.schedule(() -> {
            if (!closed.get() && !started.get()) connect();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void setStatus(Status next, String detail) {
        status = next;
        main.post(() -> listener.onStatus(next, detail));
    }

    private void log(String message) {
        main.post(() -> listener.onLog(message));
    }

    private String messageOf(Throwable error) {
        return error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private String safe(String value) { return value == null ? "sumber tidak diberikan" : value; }

    /** Called by ConnectivityManager after network restoration. */
    public void reconnectNow(String reason) {
        if(closed.get()||!connecting.compareAndSet(false,true))return;
        io.execute(()->{
            HubConnection current=hub;
            started.set(false);
            if(current!=null)try{current.stop().blockingAwait();}catch(Throwable ignored){}
            hub=null;
            connecting.set(false);
            log("Reconnect paksa: "+reason);
            connect();
        });
    }

    public Status getStatus() { return status; }

    public void disconnect() {
        if (!closed.compareAndSet(false, true)) return;
        HubConnection current = hub;
        if (current != null) io.execute(() -> {
            try { current.stop().blockingAwait(); } catch (Throwable ignored) { }
        });
        scheduler.shutdownNow();
        io.shutdown();
        setStatus(Status.DISCONNECTED, "Koneksi dihentikan.");
    }
}
