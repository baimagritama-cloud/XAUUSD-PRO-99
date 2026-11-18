# Stage 12 — Economic News Engine

## Data integrity and provider contract
- Accepts only HTTPS JSON calendar endpoints configured by the user/provider. No embedded sample calendar, fabricated schedule, or hard-coded future event is used.
- Supports common TradingEconomics-style JSON keys: Country, Event, Date, Importance, Previous, Forecast, Actual, LastUpdate. A root array or {"data":[...]} is accepted.
- Only US/USA/United States events matching the USD/XAUUSD macro-event watchlist are retained. This includes FOMC/Fed rate/Powell, CPI/Core CPI, PCE/Core PCE, NFP, unemployment, average hourly earnings, GDP, ISM manufacturing/services, retail sales, initial jobless claims, PPI, durable goods, consumer confidence and JOLTS.
- Missing Previous/Forecast/Actual remain null and render as unavailable. Invalid/empty response is INVALID; no endpoint/failed request is DATA UNAVAILABLE; calendar older than 30 minutes is STALE.
- Source is displayed as the exact configured HTTPS endpoint. This build does not claim that a provider endpoint is verified or preconfigured. The user must supply a licensed/authorized JSON calendar endpoint and credentials if required. No provider key is embedded in the APK.

## Alerts
- 45-minute and 30-minute alerts are emitted in a ±30-second scheduler window, once per event/type.
- Release alert is emitted in a ±30-second window.
- Actual-available alert is emitted after a non-empty numeric Actual appears for a released event.
- Cancelled events do not alert. Alert delivery still obeys Stage 11 notification/voice preferences, cooldown and fresh-XAUUSD quote gate. No voice alert is spoken when XAUUSD quote is stale.

## Reaction analysis
- Uses actual M1 closed candles only: last close before release, first closed candle beginning after release, and close at least two additional M1 intervals later.
- Reports initial direction, price change, total high-low reaction range, and whether direction persisted or reversed.
- Does not infer direction from the event label or forecast.
- Historical spread-at-release is unavailable because Stage 2 currently retains only the latest tick, not timestamped bid/ask history. UI says unavailable rather than substituting current spread.

## Validation
Unit tests cover filtering, nullable values, HTTPS validation, malformed data, alert timing/deduplication and stale-feed suppression. They do not verify a real provider endpoint or the correctness/completeness of an external calendar.
