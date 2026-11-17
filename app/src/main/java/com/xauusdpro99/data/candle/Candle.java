package com.xauusdpro99.data.candle;

public final class Candle {
    public final String symbol;
    public final CandleTimeframe timeframe;
    public final long openTimeMs;
    public final double open, high, low, close;
    public final double volume;
    public final long tickVolume;
    public final boolean isOpen;
    public final String source;
    public final long receivedAtMs;

    public Candle(String symbol, CandleTimeframe timeframe, long openTimeMs,
                  double open, double high, double low, double close,
                  double volume, long tickVolume, boolean isOpen,
                  String source, long receivedAtMs) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.openTimeMs = openTimeMs;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.tickVolume = tickVolume;
        this.isOpen = isOpen;
        this.source = source;
        this.receivedAtMs = receivedAtMs;
    }

    public long closeTimeMs() { return openTimeMs + timeframe.durationMs; }
    public boolean isClosedAt(long nowMs) { return !isOpen && closeTimeMs() <= nowMs; }
}
