# Stage 14C acceptance audit

Baseline: 124fb9ffe205fdc60622e7759d6e5978f1b598d5; UI #43 and Build #112 passed. Artifact 15125248316 verified SHA-256 ade068ba4c93ef8629e6f12674f427a24fae6a8f1246ccb550914019cbd403dc. Six 1920x1080 and six 1080x1920 screenshots inspected. Landscape chart was squeezed by OHLC summary; news endpoint unconfigured. Live quote and candle data visible in screenshots, not proof of sustained feed.

## Acceptance gates (not yet complete)
- [x] Inspect baseline 12 screenshots.
- [ ] Reinspect 12 screenshots after layout changes; chart visible and labels clear.
- [ ] Audit every field and action on six pages; test all buttons.
- [ ] Validate real XAUUSD ticks, freshness, reconnect and OHLC across timeframes.
- [ ] Validate all indicator values against known closed-candle fixtures.
- [ ] Verify score boundaries 56/57, 69/70, 84/85 and BUY/SELL hard gates.
- [ ] Validate TP1/TP2/TP3, SL, RR and invalidation.
- [ ] Configure authorized economic calendar source and verify schedule, previous, forecast, actual, impact and price reaction; no fabricated data.
- [ ] Test small screens, text clipping, scrolling and rotation; verify no duplicate connections.
- [ ] Run regression, build, both orientations and background monitoring; retain evidence.

No trading engine or feature removal. Do not mark Stage 14C complete until evidence supports every gate. No final APK before Stage 16.

## 2026-11-27 WIB — UI #45 visual review
- Evidence: commit 93087adc6b434f4ff55a45b2b677f610046b3d3b; Build #114 run 45853928629; UI #45 run 45853928636; artifact 15136590332; locally computed SHA-256 70341ecbd0a09afd7f9c0e22355120aee3f76a9e56224fffff96b43d65423617.
- Gate 1: visually reviewed all 12 PNGs (six 1920x1080 and six 1080x1920). All six page contents visible in both orientations. Review completed, defects remain open under other gates.
- Gates 2/3/9/10: chart now visible in both orientations; landscape plot occupies about 340 physical pixels vertically. Buttons appear as thin gray strips with clipped or invisible captions (refresh, EMA, indicator, DETAIL OHLC); hierarchy confirms button bounds only 40–44 physical pixels high. Need correct button styling/height without sacrificing chart. Zone labels overlap in dense areas; axis readability needs further review. Journey, indicators and signal text are dense; scrolling must be tested.
- Gates 4/5: screenshots show LIVE quotes 3572.95 and 3573.08, M1 300 closed bars, M2 149 and M3 99; insufficient EMA history explicitly displayed. Not proof of sustained ticks or full 300 bars per TF. Investigate historical fetch depth.
- Gates 6/7: signal screenshot shows BUY candidate score 66 with ENGINE WAIT and FREQUENCY BLOCKED, RR below 1.5. Distinguish candidate direction from actionable signal more clearly; no evidence of valid TP1–3 yet.
- Gate 8: calendar explicitly DATA_UNAVAILABLE, endpoint unconfigured; no fabricated data.
- Gate 11: #45 passed in-session rotation assertions; no duplicate connection assertion yet. Gate 12: build and existing integration passed, final regression pending. Notification screenshot shows service UNKNOWN before integration starts; not proof of failure or sustained service.

## 2026-11-27 WIB — Gate 2/3 compact controls
- HEAD e879caef: Build #115 and UI #46 report success. Source audit found fixed 40/44 physical-pixel heights combined with default Button background insets. Replaced chart/navigation control styling with explicit contrast, ripple, no vertical inset and density-scaled 40dp height. Existing handlers unchanged. Pending build, screenshot review and small-screen validation; not a gate pass.

## Gate 2/3 landscape chart regression
- Build #116 and UI #47 passed on 284ee8c0, but visual review found insufficient landscape chart space. Compact quote metadata horizontally and combine OHLC details with overlay controls in landscape. Add chart viewport assertion (at least one sixth of display height). Pending CI and visual verification; no gate pass claimed.

## Gate 4 — OHLC source audit
- Build #117 (45878165525) and UI #48 (45878165617) succeeded on b2f235dfeee32bacad43e4b0444dee215b1751f2; chart viewport assertion passed. Visual review remains pending.
- BiquoteOhlcClient requests limit=1000 from https://biquote.io/api/XAUUSD/ohlc, validates symbol and parses bars. MultiTimeframeEngine aggregates M2/M3 from the full raw M1 response before retaining 300 closed candles. No evidence that local retention causes the short history. Actual server response count and documented pagination support remain unverified; do not invent pagination parameters or candles. Gate 4 remains open.

## 2026-11-27 WIB — CI evidence at e704ba18
- Build #140 (45887905859) and UI #71 (45887905821) succeeded at e704ba18924d9bd663843cd140e27c22f2d13204. UI job 140143094368 logs confirm unit-test task and APK build succeeded, three instrumentation invocations each reported OK (1 test), and Stage 1 explicitly reported all twelve landscape and portrait screenshots captured. Artifact upload succeeded. Gates 2/3/9/10/11/12: execution evidence only; visual review and coverage of individual assertions remain open.
- Calendar Provider Probe #3 (45887882969) succeeded at 544eda4ad54e8d93c664dee3d70da66bcfa4bf8a after Probe #2 failed. Gate 8 remains open pending provider payload and application integration verification.
- No workflow rerun requested; no trading logic changed. Binary screenshot contents have not been inspected for UI #71.

## 2026-11-27 10:16 WIB — CI evidence at f6143671
- Android Build #152 (45929421893) and UI #83 (45929421883) succeeded on f61436712eafddddf0ae88550e9fe52a1ebcb0c0. UI job 140278328044 logs confirm build/unit-test step succeeded, four instrumentation invocations reported OK (1 test), twelve screenshots captured, Stage 2 Background Monitoring assertions passed, and Stage 3 calendar notification posting/stale voice suppression passed. Artifact 15168595957 reports SHA-256 f76797cbd93a969316d8daf02053830129c73372d3197f00a452dabaf34d1d69 (provider metadata, not locally recomputed).
- Gate 8: latest test waits for TTS initialization and checks fresh speech acceptance if ready; otherwise explicitly logs unavailable engine. Job success alone does not establish fresh TTS acceptance or audible output. Diagnostic log inspection and real provider field/reaction verification remain open.
- Gates 2/3/9/10/11/12: execution evidence updated, visual inspection and coverage remain open. No new gate marked complete. No rerun requested.

## 2026-11-27 13:37 WIB — Google APIs emulator orientation regression
- HEAD 3a969775654157b465d964bc1ea67865b5e35d94: Build #160 (45944990332) passed; UI #91 (45944990276), job 140327523195, failed Stage 1 with Incorrect screenshot orientation: landscape_live_chart.png. Unit/build step and diagnostic upload passed. Google TTS service/package detected; fresh speech and audible output not verified because Stage 3 was not reached.
- Gates 2/3/8/9/10/11/12 remain open. Test-only correction waits for two consecutive matching display and actual screenshot dimensions after rotation, before navigation and in the in-session rotation test. Existing screenshot assertions retained. Root cause hypothesis is asynchronous rotation; pending CI validation. No production logic changed, no rerun requested.

## Gate 2 — chart presentation follow-up
- Source review at f2172b3793d9e2ce2c31f63938aaed8c06bfbbfd: separate level label area, UTC candle timestamps, pinch zoom and 300-bar default present. Follow-up prevents a completed pinch or drag from opening the level dialog and indicates omitted inline labels. Full level text remains in the dialog. No trading engine changes.
- Latest CI status unavailable via connector; no gate pass claimed. Pending gesture tests, both orientations and screenshot review. No rerun requested.

## Gate 2 — verified UI run 45982891680
- Tested commit dabf717329221e0e238e45168ff1c2b5f1c2e4c1; job 140451023761 completed successfully. Unit/build and four instrumentation invocations passed; twelve screenshots captured; background monitoring and notification assertions passed. Fresh news TTS accepted; audible output not asserted.
- Artifact 15190521217, SHA-256 0561a7a553fbe6036dbeb9e7ba6c19452383c686eca80f563f23dd19e1e1c0d7. Previous visual review found six pages in each orientation, chart labels outside candles, but short landscape plot and repeated zone labels. UI #91 orientation failure did not recur.
- Follow-up adds actual zone bounds to full chart level labels. Pending CI, landscape layout improvement and dedicated gesture tests. Gate 2 remains open. No trading logic changes.

## Gate 2 — landscape viewport follow-up
- Run 45985394260, job 140459583254, tested 20968144e1ae6940d6f498240c75d7363647d97f successfully: unit/build, four instrumentation invocations, twelve screenshots, monitoring and notification assertions. Fresh TTS accepted; audible output not asserted. Artifact 15190484344; latest visual inspection pending.
- Combine refresh controls and horizontally scrollable timeframe selector into one 40dp row in landscape, preserving every button and handler. Portrait unchanged. Expected gain 40dp of chart height; actual viewport and button accessibility pending CI and screenshots. Gate 2 remains open.
