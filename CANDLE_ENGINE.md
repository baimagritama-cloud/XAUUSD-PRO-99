# Stage 3 — Candle Engine & 12 Timeframes

## Verified BiQuote OHLC contract
BiQuote's published API reference documents:
- `GET https://biquote.io/api/XAUUSD/ohlc`
- Query parameters `interval` and `limit`; documented maximum is 1,000 bars.
- Documented native intervals: `1m`, `5m`, `15m`, `30m`, `1h`, `4h`, and `1d`.
- Response envelope `symbol`, `interval`, `bars[]`; bar fields include `openTime`, `open`, `high`, `low`, `close`, `volume`, `tickVolume`, `isOpen`.
- `isOpen=true` identifies the still-forming bar.

Reference: https://biquote.io/docs/ (OHLC / Candlestick).

## Twelve requested timeframes
| Timeframe | Implementation | Provider status |
|---|---|---|
| M1 | Native REST | Documented |
| M2 | Aggregate 2 complete M1 bars | AGGREGATED |
| M3 | Aggregate 3 complete M1 bars | AGGREGATED |
| M5 | Native REST | Documented |
| M15 | Native REST | Documented |
| M30 | Native REST | Documented |
| H1 | Native REST | Documented |
| H4 | Native REST | Documented |
| H8 | Not requested from undocumented interval | UNSUPPORTED |
| H12 | Not requested from undocumented interval | UNSUPPORTED |
| D1 | Native REST | Documented |
| W1 | Not requested from undocumented interval | UNSUPPORTED |

The public documentation does not list M2, M3, H8, H12, or W1 as native OHLC intervals. Only M2/M3 are synthesized because this stage explicitly permits aggregation for those two. H8/H12/W1 remain unavailable rather than being silently synthesized or mislabeled.

## Engine rules
- Requests 1,000 bars for each documented native interval.
- Retains up to 300 most recent closed bars in each timeframe engine.
- Keeps the current open bar separately and excludes it from the closed-bar list.
- Sorts by open timestamp ascending; duplicate timestamps are rejected.
- Rejects invalid OHLC, non-finite/non-positive prices, negative volume, unaligned timestamps, future timestamps, and open/closed bars whose time window contradicts `isOpen`.
- Missing bars are not fabricated; gap count is exposed in snapshots.
- M2/M3 aggregation requires every consecutive, correctly aligned, closed M1 constituent. An incomplete group is skipped.
- Source kind is explicitly NATIVE, AGGREGATED, or UNAVAILABLE.
- Source status is LIVE, STALE, or UNAVAILABLE. A recent HTTP response alone is insufficient: the latest candle window must also be fresh.
- Snapshot exposes closed count, running candle, latest closed candle open timestamp, source, rejected count, and gap count.

## Verification limitation
The documentation contract is verified, but a live OHLC response and actual payload have not yet been captured from the Android app or this build environment. Therefore this implementation must not be described as having populated 300 candles until a device request confirms the returned bars. H8/H12/W1 cannot be claimed as available from the documented BiQuote endpoint.
