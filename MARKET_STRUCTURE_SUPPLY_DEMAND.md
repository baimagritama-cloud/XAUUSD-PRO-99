# MARKET STRUCTURE & SUPPLY/DEMAND ENGINE — Stage 6

## Market structure
- Uses closed, validated, ordered candles only.
- Swing pivot uses 2 bars left and 2 bars right. Pivot at index i is emitted only when the second right-hand candle is closed; confirmation timestamp/index is recorded separately from pivot time/index.
- HH/LH compare confirmed swing highs; HL/LL compare confirmed swing lows, with tolerance max(0.05 ATR(14), 0.0001% of price).
- BOS/CHOCH requires a closed candle close beyond the latest confirmed swing by tolerance. Wick-only excursions are FALSE_BREAKOUT_UP/DOWN.
- First directional break is BOS; a break opposite the established trend is CHOCH. A trend state follows the latest confirmed structural break. Consolidation is a 20-bar range <= 4 ATR when no directional structure is established.
- No backdating: an event is timestamped at the close of the candle that confirms it; a pivot is not available before its right-side confirmation.

## Supply / Demand
- Base is 1–3 low-body-ratio candles (body/range <= 0.45). A zone is created only after a close displaces beyond the base by >= 0.8 ATR within the next 3 closed candles.
- Zone bounds are the full high/low of the base. Formation time is the close time of the final base candle. No Fibonacci calculation.
- Demand invalidates on candle close below its lower boundary minus tolerance; Supply invalidates on close above upper boundary plus tolerance. Tolerance=max(0.05 ATR, 0.0001% of current price).
- Wick beyond a boundary followed by close back inside is recorded as a false breakout, not a valid breakout.
- First later overlap = TESTED; more than one overlap = WEAKENED. Rejection is a wick >=1.5 body with close in the outer 40% of the candle. Valid invalidation removes a zone from activeZones().
- H4 purple (#8000FF), D1 blue (#1677FF), W1 pink (#FF4FA3). Generic Supply red (#FF3030), Demand blue (#168CFF). Timeframe color is exposed for drawing; timeframe label remains explicit.

## Output and limitations
- Result exposes last swing high/low, all confirmed swings, BOS/CHOCH/false-breakout events, trend state, zone boundaries, source timeframe, formation timestamp, test count, status, distance, invalidation rule, and breakout/retest events.
- All output is derived from the candle snapshot provided. The upstream BiQuote OHLC response schema and live payload are not verified by this module.