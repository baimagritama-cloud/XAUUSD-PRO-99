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

import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity implements BiquoteConnectionEngine.Listener {
    private TextView price, bidAsk, timestamp, status, source, log;
    private BiquoteConnectionEngine engine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        engine = new BiquoteConnectionEngine(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF101820);

        TextView title = text("XAUUSD PRO 99 — BIQUOTE", 18, 0xFFFFFFFF);
        price = text("--", 36, 0xFFFFFFFF);
        bidAsk = text("Bid: --    Ask: --    Spread: --", 16, 0xFFE0E0E0);
        timestamp = text("Timestamp: menunggu data", 13, 0xFFCCCCCC);
        status = text("DISCONNECTED", 16, 0xFFFFC107);
        source = text("Sumber: BiQuote", 13, 0xFFCCCCCC);
        Button refresh = new Button(this);
        refresh.setText("REFRESH");
        refresh.setOnClickListener(v -> engine.refresh());
        log = text("Log koneksi…", 12, 0xFFB0BEC5);

        root.addView(title, matchWrap());
        root.addView(price, matchWrap());
        root.addView(bidAsk, matchWrap());
        root.addView(timestamp, matchWrap());
        root.addView(status, matchWrap());
        root.addView(source, matchWrap());
        root.addView(refresh, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        engine.connect();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 10, 0, 10);
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

    @Override public void onLog(String message) {
        log.append("\n" + DateFormat.getTimeInstance().format(new Date()) + " " + message);
    }

    private String fmt(Double value) { return value == null ? "--" : String.format(java.util.Locale.US, "%.2f", value); }

    @Override protected void onDestroy() {
        if (engine != null) engine.disconnect();
        super.onDestroy();
    }
}
