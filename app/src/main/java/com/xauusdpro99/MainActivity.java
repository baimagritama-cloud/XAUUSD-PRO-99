package com.xauusdpro99;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.xauusdpro99.data.connection.BiquoteConnectionEngine;
import com.xauusdpro99.data.connection.BiquoteTick;
import com.xauusdpro99.data.candle.CandleEngine;
import com.xauusdpro99.data.candle.CandleTimeframe;
import com.xauusdpro99.data.candle.MultiTimeframeEngine;
import com.xauusdpro99.data.indicator.IndicatorEngine;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

public final class MainActivity extends Activity implements BiquoteConnectionEngine.Listener, MultiTimeframeEngine.Listener {
    private TextView price, bidAsk, timestamp, status, source, log, candleSummary, indicatorSummary;
    private BiquoteConnectionEngine engine;
    private MultiTimeframeEngine candleEngine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        engine = new BiquoteConnectionEngine(this);
        candleEngine = new MultiTimeframeEngine(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF101820);

        TextView title = text("XAUUSD PRO 99 — BIQUOTE", 18, 0xFFFFFFFF);
        price = text("--", 36, 0xFFFFFFFF);
        bidAsk = text("Bid: --    Ask: --    Spread: --", 16, 0xFFE0E0E0);
        timestamp = text("Timestamp: menunggu data", 13, 0xFFCCCCCC);
        status = text("DISCONNECTED", 16, 0xFFFFC107);
        source = text("Sumber: BiQuote", 13, 0xFFCCCCCC);
        Button refresh = new Button(this);
        refresh.setText("REFRESH TICK");
        refresh.setOnClickListener(v -> engine.refresh());
        Button refreshCandles = new Button(this);
        refreshCandles.setText("REFRESH 12 TIMEFRAME");
        refreshCandles.setOnClickListener(v -> candleEngine.refreshAll());
        candleSummary = text("CANDLE ENGINE: belum mengambil data.", 12, 0xFFE0E0E0);
        indicatorSummary = text("INDICATOR ENGINE: menunggu OHLC tertutup yang valid.", 12, 0xFFE0E0E0);
        log = text("Log koneksi dan candle…", 12, 0xFFB0BEC5);

        root.addView(title, matchWrap());
        root.addView(price, matchWrap());
        root.addView(bidAsk, matchWrap());
        root.addView(timestamp, matchWrap());
        root.addView(status, matchWrap());
        root.addView(source, matchWrap());
        root.addView(refresh, matchWrap());
        root.addView(refreshCandles, matchWrap());
        ScrollView candleScroll = new ScrollView(this);
        candleScroll.addView(candleSummary);
        root.addView(candleScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView indicatorScroll = new ScrollView(this);
        indicatorScroll.addView(indicatorSummary);
        root.addView(indicatorScroll, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(log);
        root.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        engine.connect();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 7, 0, 7);
        return view;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override public void onStatus(BiquoteConnectionEngine.Status value, String detail) {
        status.setText(value.name() + " — " + detail);
    }

    @Override public void onTick(BiquoteTick tick, long receivedAt, Long sourceAt) {
        price.setText(String.format(java.util.Locale.US, "%.2f", tick.mid));
        bidAsk.setText("Bid: " + fmt(tick.bid) + "    Ask: " + fmt(tick.ask)
                + "    Spread: " + fmt(tick.spread != null ? tick.spread
                : (tick.ask != null && tick.bid != null ? tick.ask - tick.bid : null)));
        String sourceTime = sourceAt == null ? "tidak tersedia" : DateFormat.getDateTimeInstance().format(new Date(sourceAt));
        timestamp.setText("Diterima: " + DateFormat.getDateTimeInstance().format(new Date(receivedAt))
                + " | Sumber: " + sourceTime);
        source.setText("Sumber data: " + (tick.source == null ? "BiQuote (source tidak disertakan)" : tick.source));
    }

    @Override public void onUpdated(Map<CandleTimeframe, CandleEngine.Snapshot> snapshots) {
        runOnUiThread(() -> {
            StringBuilder out = new StringBuilder("TIMEFRAME | CLOSED | RUNNING | LAST CANDLE UTC | STATUS | TYPE\n");
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                CandleEngine.Snapshot s = snapshots.get(tf);
                String last = s.lastCandleOpenTimeMs == 0 ? "--"
                        : DateFormat.getDateTimeInstance().format(new Date(s.lastCandleOpenTimeMs));
                out.append(tf).append(" | ").append(s.closed.size()).append(" | ")
                        .append(s.running == null ? "NO" : "YES").append(" | ").append(last)
                        .append(" | ").append(s.sourceStatus).append(" | ").append(s.kind)
                        .append(" | gaps=").append(s.gapCount).append(" rejected=").append(s.rejectedCount).append("\n");
            }
            candleSummary.setText(out.toString());

            CandleEngine.Snapshot daily = snapshots.get(CandleTimeframe.D1);
            IndicatorEngine indicatorEngine = new IndicatorEngine();
            StringBuilder indicators = new StringBuilder("INDICATORS (closed candles only)\\n");
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                IndicatorEngine.Result r = indicatorEngine.calculate(snapshots.get(tf), daily, System.currentTimeMillis());
                indicators.append("\\n").append(tf).append(" | ").append(r.validity)
                        .append(" | bias=").append(r.bias)
                        .append(" | momentum=").append(r.momentumDirection)
                        .append(" | trend=").append(r.trendStrength)
                        .append(" | volatility=").append(r.volatility)
                        .append(" | bars=").append(r.closedBars)
                        .append(" | updated=").append(DateFormat.getDateTimeInstance().format(new Date(r.updatedAtMs))).append("\\n")
                        .append("RSI14=").append(fmt(r.rsi14)).append(" MOM14=").append(fmt(r.momentum14))
                        .append(" ADX14=").append(fmt(r.adx14)).append(" +DI=").append(fmt(r.plusDi14))
                        .append(" -DI=").append(fmt(r.minusDi14)).append("\\n")
                        .append("EMA10/20/50/100/200=").append(fmt(r.ema10)).append("/")
                        .append(fmt(r.ema20)).append("/").append(fmt(r.ema50)).append("/")
                        .append(fmt(r.ema100)).append("/").append(fmt(r.ema200)).append("\\n")
                        .append("Tenkan/Kijun/SpanA/SpanB=").append(fmt(r.tenkan9)).append("/")
                        .append(fmt(r.kijun26)).append("/").append(fmt(r.senkouA)).append("/")
                        .append(fmt(r.senkouB52)).append(" ATR14=").append(fmt(r.atr14))
                        .append(" ATR%=").append(fmt(r.atrPercent)).append("\\n")
                        .append("Pivot/R1/R2/R3/S1/S2/S3=").append(fmt(r.pivot)).append("/")
                        .append(fmt(r.r1)).append("/").append(fmt(r.r2)).append("/").append(fmt(r.r3)).append("/")
                        .append(fmt(r.s1)).append("/").append(fmt(r.s2)).append("/").append(fmt(r.s3)).append("\\n")
                        .append("Swing S=").append(r.swingSupports.toString())
                        .append(" R=").append(r.swingResistances.toString()).append("\\n")
                        .append(r.validityDetail).append("\\n");
            }
            indicatorSummary.setText(indicators.toString());
        });
    }

    @Override public void onLog(String message) {
        runOnUiThread(() -> log.append("\n" + DateFormat.getTimeInstance().format(new Date()) + " " + message));
    }

    private String fmt(Double value) { return value == null ? "NOT ENOUGH DATA" : String.format(java.util.Locale.US, "%.2f", value); }

    @Override protected void onDestroy() {
        if (engine != null) engine.disconnect();
        if (candleEngine != null) candleEngine.close();
        super.onDestroy();
    }
}
