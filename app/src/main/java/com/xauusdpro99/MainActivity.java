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
import com.xauusdpro99.data.alert.MarketAlertEngine;
import com.xauusdpro99.data.connection.BiquoteTick;
import com.xauusdpro99.data.candle.Candle;
import com.xauusdpro99.data.candle.CandleEngine;
import com.xauusdpro99.data.candle.CandleTimeframe;
import com.xauusdpro99.data.candle.MultiTimeframeEngine;
import com.xauusdpro99.data.indicator.IndicatorEngine;
import com.xauusdpro99.data.pattern.CandlePatternEngine;
import com.xauusdpro99.data.structure.MarketStructureEngine;
import com.xauusdpro99.data.structure.SupplyDemandEngine;
import com.xauusdpro99.data.signal.SignalScoringEngine;
import com.xauusdpro99.data.signal.SignalFrequencyController;
import com.xauusdpro99.data.signal.EntryRiskEngine;
import com.xauusdpro99.data.signal.MarketJourneyEngine;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

public final class MainActivity extends Activity implements BiquoteConnectionEngine.Listener, MultiTimeframeEngine.Listener {
    private TextView price, bidAsk, timestamp, status, source, log, candleSummary, indicatorSummary, patternSummary, structureSummary, supplySummary, signalSummary, entryRiskSummary, marketJourneySummary, alertSummary;
    private volatile BiquoteConnectionEngine.Status connectionStatus = BiquoteConnectionEngine.Status.DISCONNECTED;
    private volatile BiquoteTick latestTick;
    private volatile long latestQuoteReceivedAtMs;
    private final SignalScoringEngine signalEngine = new SignalScoringEngine();
    private final SignalFrequencyController frequencyController = new SignalFrequencyController();
    private final EntryRiskEngine entryRiskEngine = new EntryRiskEngine();
    private final MarketJourneyEngine marketJourneyEngine = new MarketJourneyEngine();
    private final MarketAlertEngine marketAlertEngine = new MarketAlertEngine();
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
        patternSummary = text("CANDLE PATTERN ENGINE: menunggu candle tertutup yang valid.", 12, 0xFFE0E0E0);
        structureSummary = text("MARKET STRUCTURE: menunggu data.", 12, 0xFFE0E0E0);
        supplySummary = text("SUPPLY / DEMAND: menunggu data.", 12, 0xFFE0E0E0);
        signalSummary = text("MULTI-TIMEFRAME SIGNAL SCORING: menunggu data valid dari 12 timeframe.", 13, 0xFFFFFFFF);
        entryRiskSummary = text("ENTRY / RISK PLAN: menunggu data tervalidasi.", 13, 0xFFFFFFFF);
        marketJourneySummary = text("MARKET JOURNEY: menunggu quote dan level aktual.", 13, 0xFFFFFFFF);
        alertSummary = text("MARKET ALERT: menunggu candle tertutup valid.", 13, 0xFFFFFFFF);
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
        ScrollView patternScroll = new ScrollView(this);
        patternScroll.addView(patternSummary);
        root.addView(patternScroll, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView structureScroll = new ScrollView(this);
        structureScroll.addView(structureSummary);
        root.addView(structureScroll, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView supplyScroll = new ScrollView(this);
        supplyScroll.addView(supplySummary);
        root.addView(supplyScroll, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView signalScroll = new ScrollView(this);
        signalScroll.addView(signalSummary);
        root.addView(signalScroll, new LinearLayout.LayoutParams(-1, 0, 3));
        ScrollView riskScroll = new ScrollView(this); riskScroll.addView(entryRiskSummary);
        root.addView(riskScroll, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView journeyScroll = new ScrollView(this); journeyScroll.addView(marketJourneySummary);
        root.addView(journeyScroll, new LinearLayout.LayoutParams(-1, 0, 3));
        ScrollView alertScroll = new ScrollView(this); alertScroll.addView(alertSummary);
        root.addView(alertScroll, new LinearLayout.LayoutParams(-1, 0, 2));
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
        connectionStatus = value;
        status.setText(value.name() + " — " + detail);
    }

    @Override public void onTick(BiquoteTick tick, long receivedAt, Long sourceAt) {
        latestTick = tick;
        latestQuoteReceivedAtMs = receivedAt;
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
            java.util.Map<CandleTimeframe, IndicatorEngine.Result> indicatorResults = new java.util.EnumMap<>(CandleTimeframe.class);
            StringBuilder indicators = new StringBuilder("INDICATORS (closed candles only)\n");
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                IndicatorEngine.Result r = indicatorEngine.calculate(snapshots.get(tf), daily, System.currentTimeMillis());
                indicatorResults.put(tf, r);
                indicators.append("\n").append(tf).append(" | ").append(r.validity)
                        .append(" | bias=").append(r.bias)
                        .append(" | momentum=").append(r.momentumDirection)
                        .append(" | trend=").append(r.trendStrength)
                        .append(" | volatility=").append(r.volatility)
                        .append(" | bars=").append(r.closedBars)
                        .append(" | updated=").append(DateFormat.getDateTimeInstance().format(new Date(r.updatedAtMs))).append("\n")
                        .append("RSI14=").append(fmt(r.rsi14)).append(" MOM14=").append(fmt(r.momentum14))
                        .append(" ADX14=").append(fmt(r.adx14)).append(" +DI=").append(fmt(r.plusDi14))
                        .append(" -DI=").append(fmt(r.minusDi14)).append("\n")
                        .append("EMA10/20/50/100/200=").append(fmt(r.ema10)).append("/")
                        .append(fmt(r.ema20)).append("/").append(fmt(r.ema50)).append("/")
                        .append(fmt(r.ema100)).append("/").append(fmt(r.ema200)).append("\n")
                        .append("Tenkan/Kijun/SpanA/SpanB=").append(fmt(r.tenkan9)).append("/")
                        .append(fmt(r.kijun26)).append("/").append(fmt(r.senkouA)).append("/")
                        .append(fmt(r.senkouB52)).append(" ATR14=").append(fmt(r.atr14))
                        .append(" ATR%=").append(fmt(r.atrPercent)).append("\n")
                        .append("Pivot/R1/R2/R3/S1/S2/S3=").append(fmt(r.pivot)).append("/")
                        .append(fmt(r.r1)).append("/").append(fmt(r.r2)).append("/").append(fmt(r.r3)).append("/")
                        .append(fmt(r.s1)).append("/").append(fmt(r.s2)).append("/").append(fmt(r.s3)).append("\n")
                        .append("Swing S=").append(r.swingSupports.toString())
                        .append(" R=").append(r.swingResistances.toString()).append("\n")
                        .append(r.validityDetail).append("\n");
            }
            indicatorSummary.setText(indicators.toString());

            MarketStructureEngine structureEngine = new MarketStructureEngine();
            SupplyDemandEngine supplyEngine = new SupplyDemandEngine();
            java.util.Map<CandleTimeframe, MarketStructureEngine.Result> structureResults = new java.util.EnumMap<>(CandleTimeframe.class);
            java.util.Map<CandleTimeframe, SupplyDemandEngine.Result> supplyResults = new java.util.EnumMap<>(CandleTimeframe.class);
            StringBuilder structures = new StringBuilder("MARKET STRUCTURE — CLOSED CANDLES ONLY\n");
            StringBuilder supplies = new StringBuilder("SUPPLY / DEMAND — NO FIBONACCI\n");
            java.util.Map<CandleTimeframe, java.util.List<CandlePatternEngine.PriceZone>> zonesByTf = new java.util.EnumMap<>(CandleTimeframe.class);
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                CandleEngine.Snapshot snap = snapshots.get(tf);
                MarketStructureEngine.Result ms = structureEngine.analyze(snap, System.currentTimeMillis());
                structureResults.put(tf, ms);
                structures.append("\n").append(tf).append(" | trend=").append(ms.trend).append(" | ").append(ms.validity).append("\n");
                structures.append("Last swing high=").append(ms.lastSwingHigh == null ? "--" : ms.lastSwingHigh.label + " " + fmt(ms.lastSwingHigh.price) + " confirmed " + DateFormat.getDateTimeInstance().format(new Date(ms.lastSwingHigh.confirmedAtMs)))
                        .append(" | low=").append(ms.lastSwingLow == null ? "--" : ms.lastSwingLow.label + " " + fmt(ms.lastSwingLow.price) + " confirmed " + DateFormat.getDateTimeInstance().format(new Date(ms.lastSwingLow.confirmedAtMs))).append("\n");
                for (MarketStructureEngine.Event e : ms.events) structures.append(e.type).append(" level=").append(fmt(e.level)).append(" close=").append(fmt(e.close)).append(" at=").append(DateFormat.getDateTimeInstance().format(new Date(e.timestampMs))).append("\n");
                Candle latest = snap == null || snap.closed.isEmpty() ? null : snap.closed.get(snap.closed.size()-1);
                double current = latest == null ? 0d : latest.close;
                SupplyDemandEngine.Result sd = supplyEngine.analyze(snap, current, System.currentTimeMillis());
                supplyResults.put(tf, sd);
                java.util.List<CandlePatternEngine.PriceZone> patternZones = new java.util.ArrayList<>();
                supplies.append("\n").append(tf).append(" | ").append(sd.validity).append("\n");
                for (SupplyDemandEngine.Zone z : sd.zones) {
                    supplies.append(z.label).append(" [").append(fmt(z.lower)).append(" - ").append(fmt(z.upper)).append("] | status=").append(z.status)
                            .append(" | tests=").append(z.tests).append(" | distance=").append(fmt(z.distance))
                            .append(" | formed=").append(DateFormat.getDateTimeInstance().format(new Date(z.formedAtMs)))
                            .append(" | color=").append(z.color).append(" | invalidate: ").append(z.invalidationCondition).append("\n");
                    if (z.active()) patternZones.add(new CandlePatternEngine.PriceZone(
                            z.type == SupplyDemandEngine.ZoneType.DEMAND ? CandlePatternEngine.ZoneType.DEMAND : CandlePatternEngine.ZoneType.SUPPLY,
                            z.lower, z.upper, z.label + " " + z.status));
                }
                for (SupplyDemandEngine.Breakout e : sd.breakouts) supplies.append(e.type).append(" level=").append(fmt(e.level)).append(" close=").append(fmt(e.close)).append(" retest=").append(e.retest).append(" ").append(e.note).append("\n");
                zonesByTf.put(tf, patternZones);
            }
            java.util.Map<CandleTimeframe, java.util.List<CandlePatternEngine.Pattern>> patternResults = new java.util.EnumMap<>(CandleTimeframe.class);
            StringBuilder patterns = new StringBuilder("CANDLE PATTERNS — closed OHLC only\n");
            CandlePatternEngine patternEngine = new CandlePatternEngine();
            for (CandleTimeframe tf : CandleTimeframe.values()) {
                CandleEngine.Snapshot snap = snapshots.get(tf);
                CandleTimeframe higherTf = higherTimeframe(tf);
                IndicatorEngine.Result same = indicatorEngine.calculate(snap, daily, System.currentTimeMillis());
                IndicatorEngine.Result higher = higherTf == null ? null
                        : indicatorEngine.calculate(snapshots.get(higherTf), daily, System.currentTimeMillis());
                CandlePatternEngine.Context context = new CandlePatternEngine.Context(same, higher, zonesByTf.get(tf));
                java.util.List<CandlePatternEngine.Pattern> detected = patternEngine.detect(snap, context, System.currentTimeMillis());
                patternResults.put(tf, detected);
                patterns.append("\n").append(tf).append(" | ");
                if (detected.isEmpty()) {
                    patterns.append("NO CONFIRMED PATTERN / DATA NOT LIVE\n");
                } else {
                    for (CandlePatternEngine.Pattern p : detected) {
                        patterns.append(p.name).append(" | ").append(p.direction)
                                .append(" | price=").append(fmt(p.formationPrice))
                                .append(" | time=").append(DateFormat.getDateTimeInstance().format(new Date(p.timestampMs)))
                                .append(" | location=").append(p.location)
                                .append(" | strength=").append(p.strength).append("/100")
                                .append(" | status=").append(p.status)
                                .append(" | invalidate: ").append(p.invalidationCondition).append("\n");
                    }
                }
            }
            patternSummary.setText(patterns.toString());
            structureSummary.setText(structures.toString());
             supplySummary.setText(supplies.toString());

            BiquoteTick tick = latestTick;
            boolean quoteValid = tick != null && tick.isValidFor("XAUUSD")
                    && !Boolean.TRUE.equals(tick.stale)
                    && !"closed".equalsIgnoreCase(tick.marketState)
                    && (tick.quoteAgeSeconds == null || tick.quoteAgeSeconds <= 300);
            double bidValue = tick == null || tick.bid == null ? Double.NaN : tick.bid;
            double askValue = tick == null || tick.ask == null ? Double.NaN : tick.ask;
            double spreadValue = tick == null ? Double.NaN : (tick.spread != null ? tick.spread
                    : (tick.ask != null && tick.bid != null ? tick.ask - tick.bid : Double.NaN));
            SignalScoringEngine.Input scoringInput = new SignalScoringEngine.Input(
                    snapshots, indicatorResults, structureResults, supplyResults, patternResults,
                    connectionStatus == BiquoteConnectionEngine.Status.LIVE, quoteValid,
                    latestQuoteReceivedAtMs, System.currentTimeMillis(), bidValue, askValue,
                    spreadValue, SignalScoringEngine.DEFAULT_MAX_SPREAD);
            StringBuilder signals = new StringBuilder("SIGNAL QUALITY — SKOR BUKAN PROBABILITAS WINRATE\n");
            java.util.List<SignalScoringEngine.Result> scoredResults = signalEngine.evaluate(scoringInput);
            java.util.List<SignalFrequencyController.Decision> frequencyDecisions =
                    frequencyController.process(scoredResults, System.currentTimeMillis());
            for (SignalFrequencyController.Decision decision : frequencyDecisions) {
                SignalScoringEngine.Result result = decision.result;
                signals.append("\n").append(result.direction).append(" | SCORE ")
                        .append(result.totalScore).append("/100 | ").append(result.quality)
                        .append(" | ENGINE ").append(result.status)
                        .append(" | FREQUENCY ").append(decision.published ? "PUBLISHED" : "BLOCKED")
                        .append(" — ").append(decision.reason).append("\n");
                signals.append("Pattern: ").append(result.patternName)
                        .append(" | Entry=").append(fmt(result.entry))
                        .append(" | SL=").append(fmt(result.stopLoss))
                        .append(" | Target=").append(fmt(result.target))
                        .append(" | RR=").append(fmt(result.riskReward))
                        .append(" | Spread=").append(fmt(result.spread)).append("\n");
                for (SignalScoringEngine.Component component : result.components)
                    signals.append(component.name).append(": ").append(component.points).append("/")
                            .append(component.weight).append(" — ").append(component.explanation).append("\n");
                for (SignalScoringEngine.Conflict conflict : result.conflicts)
                    signals.append("CONFLICT ").append(conflict.timeframe).append(" [").append(conflict.role)
                            .append("]: ").append(conflict.explanation).append("\n");
                for (String reason : result.acceptedReasons) signals.append("OK: ").append(reason).append("\n");
                for (String reason : result.rejectedReasons) signals.append("BLOCK: ").append(reason).append("\n");
            }
            signals.append("\n").append(frequencyController.report(System.currentTimeMillis()));
            signals.append("\nCatatan: 12 kandidat/hari adalah target evaluasi, bukan kuota. Batas skor 57 dan seluruh hard gate tetap wajib. Statistik performa belum tersedia tanpa hasil historis/outcome tervalidasi.");
            signalSummary.setText(signals.toString());
            java.util.List<MarketAlertEngine.Alert> alerts = marketAlertEngine.evaluate(new MarketAlertEngine.Input(
                    snapshots, indicatorResults, structureResults, supplyResults, patternResults, scoredResults, System.currentTimeMillis()));
            StringBuilder alertText = new StringBuilder("EMA20/EMA50 + MARKET ALERT — CLOSED CANDLES ONLY\\n");
            if (alerts.isEmpty()) alertText.append("Belum ada peristiwa baru yang memenuhi syarat.\\n");
            for (MarketAlertEngine.Alert a : alerts) alertText.append(a.type).append(" | TF=").append(a.timeframe)
                    .append(" | Harga=").append(fmt(a.price)).append(" | Waktu=")
                    .append(DateFormat.getDateTimeInstance().format(new Date(a.timestampMs))).append(" | ").append(a.status)
                    .append("\\nAlasan: ").append(a.reason).append("\\nInvalidasi: ").append(a.invalidation).append("\\n\\n");
            alertText.append("Timeframe: M1 M2 M3 M5 M15 M30 H1 H4 H8 H12 D1 W1 | cooldown 5 menit.");
            alertSummary.setText(alertText.toString());

            StringBuilder riskText = new StringBuilder("ENTRY / STOP LOSS / TAKE PROFIT — LEVEL TEKNIS AKTUAL\\n");
            for (SignalScoringEngine.Result scored : scoredResults) {
                EntryRiskEngine.Result plan = entryRiskEngine.calculate(new EntryRiskEngine.Input(scored,
                        snapshots, indicatorResults, structureResults, supplyResults, patternResults,
                        bidValue, askValue, spreadValue, tick == null ? Double.NaN : tick.mid,
                        latestQuoteReceivedAtMs, System.currentTimeMillis(),
                        connectionStatus == BiquoteConnectionEngine.Status.LIVE, quoteValid));
                riskText.append("\\n").append(scored.direction).append(" | SCORE ").append(scored.totalScore)
                        .append(" | ").append(plan.summary).append("\\nEntry=").append(fmt(plan.entry))
                        .append(" SL=").append(fmt(plan.sl)).append(" Risk=").append(fmt(plan.risk))
                        .append(" ATR=").append(fmt(plan.atr)).append(" RR=").append(fmt(plan.rr)).append("\\n");
                for (EntryRiskEngine.Target t : plan.targets)
                    riskText.append(t.label).append("=").append(fmt(t.price)).append(" | ").append(t.timeframe)
                            .append(" | ").append(t.source).append(" | RR=").append(fmt(t.rr)).append("\\n");
                for (String reason : plan.accepted) riskText.append("OK: ").append(reason).append("\\n");
                for (String reason : plan.rejected) riskText.append("BLOCK: ").append(reason).append("\\n");
            }
            entryRiskSummary.setText(riskText.toString());

            MarketJourneyEngine.Result journey = marketJourneyEngine.analyze(new MarketJourneyEngine.Input(
                    tick == null ? Double.NaN : tick.mid, bidValue, askValue, spreadValue,
                    connectionStatus == BiquoteConnectionEngine.Status.LIVE, quoteValid,
                    latestQuoteReceivedAtMs, System.currentTimeMillis(), snapshots,
                    indicatorResults, structureResults, supplyResults, patternResults));
            StringBuilder journeyText = new StringBuilder("MARKET JOURNEY — DATA AKTUAL\\n");
            journeyText.append("Harga=").append(fmt(journey.currentPrice)).append(" | Arah=").append(journey.mainDirection)
                    .append(" | State=").append(journey.state).append(" | Area=").append(journey.safety)
                    .append("\\nPosisi=").append(journey.position).append("\\nDemand=").append(journey.nearestDemand)
                    .append("\\nSupply=").append(journey.nearestSupply).append("\\nTujuan=").append(journey.nextDestination)
                    .append("\\nArea Aman=").append(journey.safeArea).append("\\nArea Waspada=").append(journey.cautionArea)
                    .append("\\nSkenario bullish:\\n");
            for (String s : journey.bullishScenario) journeyText.append("• ").append(s).append("\\n");
            journeyText.append("Pembatalan bullish: ").append(journey.cancelBullish).append("\\nSkenario bearish:\\n");
            for (String s : journey.bearishScenario) journeyText.append("• ").append(s).append("\\n");
            journeyText.append("Pembatalan bearish: ").append(journey.cancelBearish).append("\\nPotensi reversal:\\n");
            for (String s : journey.reversalNotes) journeyText.append("• ").append(s).append("\\n");
            for (MarketJourneyEngine.Level l : journey.nearbyLevels)
                journeyText.append(l.type).append(" ").append(l.timeframe).append(" ").append(l.label)
                        .append(" [").append(fmt(l.lower)).append("–").append(fmt(l.upper))
                        .append("] distance=").append(fmt(l.distance)).append("\\n");
            if (!journey.dataValid) journeyText.append("DATA INVALID — skenario ditahan sampai quote LIVE valid.\\n");
            marketJourneySummary.setText(journeyText.toString());
        });
    }

    @Override public void onLog(String message) {
        runOnUiThread(() -> log.append("\n" + DateFormat.getTimeInstance().format(new Date()) + " " + message));
    }

    private CandleTimeframe higherTimeframe(CandleTimeframe tf) {
        switch (tf) {
            case M1: case M2: case M3: case M5: case M15: return CandleTimeframe.H1;
            case M30: case H1: return CandleTimeframe.H4;
            case H4: case H8: case H12: return CandleTimeframe.D1;
            case D1: case W1: return null;
            default: return null;
        }
    }

    private String fmt(Double value) { return value == null ? "NOT ENOUGH DATA" : String.format(java.util.Locale.US, "%.2f", value); }

    @Override protected void onDestroy() {
        if (engine != null) engine.disconnect();
        if (candleEngine != null) candleEngine.close();
        super.onDestroy();
    }
}
