# Stage 5 — Candle Pattern Engine

## Input, output and status rules
- Only validated closed OHLC candles are scanned. Running candles, stale/unavailable snapshots, malformed OHLC and duplicate/out-of-order timestamps are excluded.
- The scanner evaluates recent formations (latest eight possible formation endpoints). Timestamp is the close time of the final candle in the formation; formation price is its close.
- Output: pattern name, bullish/bearish direction, timeframe, formation price, timestamp, structural location, deterministic strength score, status and invalidation rule.
- DETECTED = OHLC geometry matched. LOCATION_CONFIRMED = matching Demand/Supply zone or same-timeframe swing Support/Resistance is nearby. ENTRY_CONFIRMED = a later closed candle closes beyond the full formation high (bullish) or low (bearish). INVALIDATED = a later close breaks the opposite formation boundary.
- A detected pattern is not an automatic BUY/SELL signal. No win-rate or probability is claimed.

## Measurement formulas
body=abs(Close-Open); upperWick=High-max(Open,Close); lowerWick=min(Open,Close)-Low; range=High-Low; bodyToRange=body/range; wickToBody=(upperWick+lowerWick)/body; closePosition=(Close-Low)/range; ATRrelative=range/WilderATR14.
Tolerance for approximate equal highs/lows and body containment=max(0.10×ATR14, 0.001% of price). Zone/SR proximity tolerance=max(0.35×ATR14, 0.01% of price).

## Mathematical pattern rules
Definitions: bullish candle Close>Open; bearish Close<Open; bodyLow=min(Open,Close); bodyHigh=max(Open,Close). All candles must be closed.

| Pattern | Rule |
|---|---|
| Bullish Engulfing | Previous bearish/current bullish; current body low <= prior body low+tolerance and current body high >= prior body high-tolerance; body/range >=0.45. |
| Bullish Harami | Previous bearish/current bullish; current body strictly inside prior body; current body <=60% prior body. |
| Piercing Line | Previous bearish/current bullish; current open <= previous close+tolerance; current close above midpoint of previous body but below previous open; body/range >=0.35. |
| Morning Star | First bearish; middle body/range <=0.32; third bullish, closes above midpoint of first body and below first open; third body/range >=0.40. |
| Three White Soldiers | Three bullish candles with higher closes; second/third opens and closes inside previous body; body/range >=0.50 each; upper wick <=0.45×body each. |
| Hammer | body/range <=0.38; lower wick >=2×body; upper wick <=0.45×body; close position >=0.60; range/ATR in [0.45,2.8]. |
| Inverted Hammer | body/range <=0.38; upper wick >=2×body; lower wick <=0.45×body; close position <=0.40; range/ATR in [0.45,2.8]. |
| Dragonfly Doji | body/range <=0.10; upper wick <=10% range; lower wick >=60% range. |
| Tweezer Bottom | Prior bearish/current bullish; lows differ <= tolerance; both body/range >=0.20. |
| Bullish Marubozu | Bullish; body/range >=0.90; each wick <=6% range. |
| Bullish Kicker | Prior bearish/current bullish; current open above prior body high+tolerance; body/range >=0.50. |
| Three Inside Up | First bearish; second bullish body strictly inside first; third bullish closes above first open. |
| Three Outside Up | First bearish; second bullish body engulfs first; third bullish closes above second close. |
| Bullish Pin Bar | lower wick >=2.5×body; upper wick <=0.50×body; close position >=0.65; body/range <=0.35; range/ATR >=0.35. |
| Bullish Rejection Candle | Same lower-wick rejection geometry as bullish pin bar; structural location is assessed separately. |
| Bearish Engulfing | Prior bullish/current bearish; current body covers prior body within tolerance; body/range >=0.45. |
| Bearish Harami | Prior bullish/current bearish; current body strictly inside prior body; current body <=60% prior body. |
| Dark Cloud Cover | Prior bullish/current bearish; current open >= prior close-tolerance; current close below prior body midpoint but above prior open; body/range >=0.35. |
| Evening Star | First bullish; middle body/range <=0.32; third bearish, closes below midpoint of first body and above first open; third body/range >=0.40. |
| Three Black Crows | Three bearish candles with lower closes; second/third open and close inside prior body; body/range >=0.50 each; lower wick <=0.45×body each. |
| Shooting Star | body/range <=0.38; upper wick >=2×body; lower wick <=0.45×body; close position <=0.40; range/ATR in [0.45,2.8]. |
| Hanging Man | Same lower-wick geometry as Hammer; bearish context is separate and does not automatically create entry. |
| Gravestone Doji | body/range <=0.10; lower wick <=10% range; upper wick >=60% range. |
| Tweezer Top | Prior bullish/current bearish; highs differ <= tolerance; both body/range >=0.20. |
| Bearish Marubozu | Bearish; body/range >=0.90; each wick <=6% range. |
| Bearish Kicker | Prior bullish/current bearish; current open below prior body low-tolerance; body/range >=0.50. |
| Three Inside Down | First bullish; second bearish body strictly inside first; third bearish closes below first open. |
| Three Outside Down | First bullish; second bearish body engulfs first; third bearish closes below second close. |
| Bearish Pin Bar | upper wick >=2.5×body; lower wick <=0.50×body; close position <=0.35; body/range <=0.35; range/ATR >=0.35. |
| Bearish Rejection Candle | Same upper-wick rejection geometry as bearish pin bar; structural location is assessed separately. |

## Structure and score
- Demand zones may confirm bullish location; Supply zones may confirm bearish location. This API accepts zones from the future SupplyDemand engine without a compile-time dependency on that unfinished stage.
- If zones are not supplied, same-timeframe swing lows/highs from IndicatorEngine can confirm Support/Resistance proximity.
- Higher-timeframe bias adjusts descriptive strength only; it cannot by itself mark location confirmed.
- Strength is a deterministic heuristic score 0–100, not probability and not a win-rate estimate.
- Kicker uses a strict open beyond prior candle body; a true session gap is not required because XAUUSD CFD/FX sessions may not gap consistently.

## JUnit coverage
Tests cover Bullish Engulfing, Hammer geometry including ATR-relative range, Demand location, next-candle entry confirmation, invalidation, running/unavailable input, and malformed OHLC rejection. Synthetic formula fixtures are not evidence of live-market accuracy.
