package com.xauusdpro99.data.candle;

public enum CandleTimeframe {
    M1(60_000L, "1m", true), M2(120_000L, null, false), M3(180_000L, null, false),
    M5(300_000L, "5m", true), M15(900_000L, "15m", true), M30(1_800_000L, "30m", true),
    H1(3_600_000L, "1h", true), H4(14_400_000L, "4h", true),
    H8(28_800_000L, null, false), H12(43_200_000L, null, false),
    D1(86_400_000L, "1d", true), W1(604_800_000L, null, false);

    public final long durationMs;
    public final String biquoteInterval;
    public final boolean nativeSupported;

    CandleTimeframe(long durationMs, String biquoteInterval, boolean nativeSupported) {
        this.durationMs = durationMs;
        this.biquoteInterval = biquoteInterval;
        this.nativeSupported = nativeSupported;
    }

    public boolean isAggregatedFromM1() { return this == M2 || this == M3; }

    public boolean isAligned(long epochMs) {
        if (this == W1) return Math.floorMod(epochMs + 3L * 86_400_000L, durationMs) == 0L;
        return Math.floorMod(epochMs, durationMs) == 0L;
    }
}
