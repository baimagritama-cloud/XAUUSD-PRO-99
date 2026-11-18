# Stage 12 — Economic News Engine

## Data integrity and provider contract
- Accepts only HTTPS JSON calendar endpoints configured by the user/provider. No embedded sample calendar, fabricated schedule, or hard-coded future event is used.
- Recommended source: Trading Economics Calendar API. Its official documentation specifies CalendarId, Date (UTC), Country, Event, Source, Actual, Previous, Forecast, Importance and LastUpdate. API access requires a provider subscription/API key; the app does not embed a key. Example endpoint: https://api.tradingeconomics.com/calendar/country/united%20states?c=YOUR_API_KEY&f=json.
- Supports common TradingEconomics JSON keys: CalendarId, Country, Event, Date, Importance, Previous, Forecast, Actual, Source, LastUpdate. A root array or {"data":[...]} is accepted.
- Only US/USA/United States events matching the USD/XAUUSD macro-event watchlist are retained. This includes FOMC/Fed rate/Powell, CPI/Core CPI, PCE/Core PCE, NFP, unemployment, average hourly earnings, GDP, ISM manufacturing/services, retail sales, initial jobless claims, PPI, durable goods, consumer confidence and JOLTS.
- Previous/Forecast/Actual are preserved as provider text (including units such as %, K, M or B); missing values remain null and render as unavailable. The per-event Source field identifies the publisher such as BLS/BEA/ISM when supplied. Invalid/empty response is INVALID; no endpoint/failed request is DATA UNAVAILABLE; calendar older than 30 minutes is STALE.
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
