package com.xauusdpro99.data.connection;

/** Fields mirror the documented BiQuote tick response. Nullable fields remain nullable. */
public final class BiquoteTick {
    public String symbol;
    public String description;
    public Double bid;
    public Double ask;
    public Double mid;
    public Double spread;
    public Double last;
    public String timestamp;
    public String time;
    public String source;
    public String marketState;
    public Boolean stale;
    public Double quoteAgeSeconds;

    public boolean isValidFor(String expectedSymbol) {
        return symbol != null && expectedSymbol.equalsIgnoreCase(symbol)
                && mid != null && Double.isFinite(mid) && mid > 0
                && (bid == null || (Double.isFinite(bid) && bid > 0))
                && (ask == null || (Double.isFinite(ask) && ask > 0));
    }
}
