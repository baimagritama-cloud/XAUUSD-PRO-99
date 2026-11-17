# Stage 7 — Multi-Timeframe Analysis & Signal Scoring

## Roles
- Main direction: W1, D1, H4 (weight multiplier 4).
- Confirmation: H1, H8, H12, M30, M15 (multiplier 2).
- Setup/timing: M5, M3, M2, M1 (multiplier 1).
- Disagreement is retained as a conflict and penalizes the alignment/structure component; timeframes are not forced into unanimous direction.

## Initial fixed weights (sum 100)
Candle Pattern 20; Supply/Demand + Support/Resistance 25; Indicator confirmation 20; Market Structure 15; Multi-timeframe alignment 15; Entry Confirmation 5.
Points are bounded by each component's weight. Pattern strength is a heuristic, not a win probability.

## Quality thresholds
85–100 HIGH_QUALITY; 70–84 MODERATE_QUALITY; 57–69 EARLY_LOWER_QUALITY; 0–56 NO_TRADE.
Score <57 cannot publish BUY/SELL. Score 57–69 additionally requires confirmed next closed candle, valid location, supporting structure and RR >=1.5. Every score band still requires all hard gates.

## Hard gates
All 12 timeframes must provide LIVE closed candles, LIVE complete indicators, valid structure and valid Supply/Demand results. Tick must be valid and <=15 seconds old. Spread <= configurable cap (default 0.80 XAUUSD price units). A non-invalidated directional pattern must exist on M1/M2/M3/M5 and its following closed candle must confirm beyond pattern high/low. Matching active zone or swing S/R must be present. Main direction and setup structure must support the candidate. SL is beyond pattern extreme by 0.10 x M1 ATR; target is nearest active opposing Supply/Demand boundary; RR >=1.5. Identical direction/timeframe/pattern/time setup emits only once.

## Status
BUY/SELL only when score >=57 and all gates pass. WAIT when score >=57 but a gate remains unmet. NO_TRADE when score <57. Invalid/stale/missing data never emits entry.

## Calibration
Weights remain fixed. Any later change requires chronological out-of-sample backtest including spread/slippage/commission, no lookahead, sample-size reporting, documented parameter change and regression tests.

## Upstream limitation
Stage 3 has not verified BiQuote OHLC for H8/H12/W1. This implementation intentionally fails closed until all twelve timeframes are genuinely available. A successful unit test/build does not prove live-feed correctness or win rate.

---

# Stage 8 — Frequency governance (target 12/day, not a quota)

## Fixed quality boundaries
The existing boundaries are unchanged: 85–100 HIGH QUALITY, 70–84 MODERATE QUALITY, 57–69 EARLY / LOWER QUALITY, and 0–56 NO TRADE. No BUY/SELL can be published below 57. Frequency logic never promotes WAIT or NO_TRADE.

## Entry timing and context
M1, M2, M3 and M5 remain the pattern/entry-timing candidates. Larger timeframes remain context, confirmation and structural hard gates. Existing Stage 7 hard gates remain unchanged, including the requirement that all twelve timeframes have valid LIVE closed candles, indicators, structure and Supply/Demand. This is intentionally fail-closed while BiQuote H8/H12/W1 availability is not verified.

## Deduplication and cooldown
- Cross-timeframe identity ignores the timeframe label and uses direction + pattern name.
- Same identity within a rolling 5-minute formation-time window is counted once.
- Initial cooldown is 30 minutes per direction after a published entry.
- A pattern marked INVALIDATED is never published; WAIT/NO_TRADE is never upgraded.
- The 5-minute dedup window and 30-minute cooldown are initial governance settings, not statistically calibrated parameters. They must be reviewed only with chronological out-of-sample results.

## Candidate audit and frequency report
The controller records candidate decisions in the current application session (maximum 2,000 audit entries). Each candidate retains its score/status and accepted or rejected reason. The report displays BUY/SELL, active-session average/median, score bands and WIB session counts. The current implementation does not persist this audit across app restarts; cross-session and full calendar-day zero-signal reporting require a later persistent history store.

## Backtest and performance metrics
No validated 30–90 day historical OHLC dataset or trade-outcome ledger is currently available in this repository. Therefore win rate, expectancy, Profit Factor, maximum drawdown, per-timeframe long-term frequencies, and days without signals must display NOT AVAILABLE until the app has sufficient timestamped history and forward outcome tracking. They must not be estimated from the last 300 candles or invented.

## Frequency target
12 candidates/day is a monitoring target only. Actual qualifying count is reported as observed. A lower count is correct when filters reject the other setups. Do not lower the score threshold, loosen risk/reward, or bypass data/structure/quote validity to reach the target.
