# Stage 4 — Indicator Engine

## Input and safety
- Input is only the validated, closed OHLC list from CandleEngine.
- The running candle is never included in final indicator values.
- A STALE or UNAVAILABLE timeframe returns no numeric indicator values.
- Missing values are null internally and rendered as NOT ENOUGH DATA; no zero/default placeholders.
- Each result carries timeframe, closed-bar count, validity, detail, and calculation/update timestamp.
- Swing pivots are only reported after two later closed bars confirm them (2-left/2-right); no future candle is read.

## Formula contract
| Indicator | Calculation |
|---|---|
| RSI 14 | Wilder RSI: first 14 close-to-close gains/losses use SMA; subsequent average gain/loss use RMA/SMMA; RSI = 100 - 100/(1+RS). Flat series = 50; no losses = 100. |
| Momentum 14 | MT-style ratio: Close[t] / Close[t-14] × 100. Baseline 100; >100 positive, <100 negative. |
| ADX/+DI/-DI 14 | Wilder directional movement. +DM/-DM select the larger positive high/low excursion; TR = max(H-L, abs(H-prevClose), abs(L-prevClose)); smooth TR and DMs with Wilder SMMA; DI = smoothed DM / smoothed TR ×100; DX = abs(+DI--DI)/(+DI+-DI)×100; ADX = Wilder-smoothed DX. |
| EMA 10/20/50/100/200 | Close-only EMA, seeded with SMA of the first N closes; alpha=2/(N+1). |
| Ichimoku | Tenkan=(HH9+LL9)/2; Kijun=(HH26+LL26)/2; Senkou B=(HH52+LL52)/2; Senkou A=(Tenkan+Kijun)/2. Values are calculated at the current closed bar; chart forward displacement is a presentation offset, not future input. |
| ATR 14 | TR as above; first ATR is SMA of 14 TR values, then Wilder RMA: (previous×13+TR)/14. |
| Daily pivot | Traditional pivot from latest completed D1 bar: P=(H+L+C)/3; R1=2P-L; S1=2P-H; R2=P+(H-L); S2=P-(H-L); R3=2P+(H-2L); S3=2P-(2H-L). |
| Swing S/R | Confirmed 2-left/2-right fractal highs/lows on closed OHLC. Up to five latest supports/resistances returned per timeframe. |

## Derived interpretation
- RSI >55 bullish, <45 bearish, otherwise neutral.
- Momentum >100 bullish, <100 bearish, exactly 100 neutral.
- ADX >=25 strong, 20–25 moderate, <20 weak. ADX measures strength, DI ordering gives direction.
- Volatility compares current ATR14 with the mean of up to 50 recent ATR14 values: ratio <0.8 low, >1.2 high, otherwise normal.
- Composite bias is a simple vote across RSI, DI direction, EMA50/EMA200 with close position, and close position versus Ichimoku cloud. This is a descriptive bias, not a trade probability.

## Warm-up / insufficient data
- RSI and Momentum require at least 15 closed closes.
- EMA-N requires N closes; EMA200 therefore needs 200.
- Ichimoku requires 9 / 26 / 52 closed candles.
- ATR14 needs 15 bars (14 true ranges after a prior close).
- ADX14 needs 28 closed bars to seed 14 DX values after the first 14 directional-movement periods.
- Daily pivot requires one valid completed D1 bar.
- If some indicators have enough data while others do not, valid metrics remain visible and only unavailable metrics show NOT ENOUGH DATA. Overall timeframe validity is NOT_ENOUGH_DATA until the full required set is available.

## Reference comparison
- MetaTrader 5 RSI calculation and interpretation: https://www.metatrader5.com/en/terminal/help/indicators/oscillators/rsi
- MetaTrader 5 Wilder ADX, DI and True Range calculations: https://www.metatrader5.com/en/terminal/help/indicators/trend_indicators/admiw
- TradingView RSI / Wilder RMA explanation: https://www.tradingview.com/support/solutions/43000502338-relative-strength-index-rsi/
- TradingView Traditional Pivot Point formulas: https://www.tradingview.com/support/solutions/43000521824-pivot-points-standard/

## Tests
JUnit fixtures cover Momentum's 100 baseline, rising-market RSI, ATR/DI/ADX in a monotonic series, Ichimoku window calculations, and NOT_ENOUGH_DATA instead of placeholder values. These are deterministic formula tests; they do not constitute a live BiQuote parity test. A true provider parity comparison still requires captured OHLC payloads and matching MetaTrader/TradingView inputs, timezone, candle session boundaries, and smoothing initialization.
