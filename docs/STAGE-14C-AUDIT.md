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

## Gate 2 — label allocation follow-up
- Run 45987138479 tested ea5a9662682ec1de5b0b4f0a5e2b75424e3e3633 successfully; twelve screenshots captured, artifact 15191344865. Prioritize support/resistance labels, alternate supply/demand label allocation, and use compact price labels when full zone text does not fit. Full zone bounds remain in the level dialog. All zone fills retained. CI and visual confirmation pending; gate remains open.

## Gate 2 — timeframe control coverage
- Run 45988956940 tested ad0d68260492c4d144d8e5175400e37a0b47f52c successfully, artifact 15192872567. Visual review of latest labels pending.
- Add accessible timeframe strip identifier and exercise every enum timeframe in both existing UI tests, scrolling to hidden controls and restoring M1. This asserts reachability and chart presence, not OHLC availability or correct data selection. Drag/pinch assertions remain pending. No production feed changes.

## Gate 2 — drag instrumentation follow-up
- Add real touch swipes in both orientation tests and assert viewport offset changes in both directions using read-only chart state on the UI thread. Require real closed candles; no fixture or production feed changes. Pending CI; no gate pass claimed.

## Gate 2 — drag verified and pinch follow-up
- Run 46003335428 on 24853e0b68bbcd173878104b0d040f5a116ae7b2, job 140520253896, passed both orientation tests including bidirectional drag assertions, build/unit tests, background monitoring and calendar notification tests. Twelve screenshots captured; artifact 15199493448; visual review pending.
- Add pinch open/close assertions in both orientations using read-only viewport scale, requiring real closed candles. Pending CI. No trading engine changes.

## Gate 2 — pinch verified and pan bounds follow-up
- Run 46011159144 on 0524b3395f66840eb2251a51a919c968b3b2e7da passed both orientation tests including pinch assertions, build/unit tests, background monitoring and calendar notification tests; artifact 15203674262. Visual review pending.
- Fix pan clamp to max(0, closed count - visible bars), including after scale changes and data refresh. Test both boundaries and overscroll in both orientations. Drag regression now runs zoomed in because a full-history viewport correctly cannot pan. Pending CI.

## Gate 2 — pan bounds verified and label gesture follow-up
- Run 46013599998 on 82b9f3596744c97e19cccce72ad19c75e7b504f9 passed both orientation tests including pan bounds and overscroll, build/unit tests, background monitoring and calendar notification tests. Artifact 15204843487; visual review pending.
- Add positive label tap and negative drag/pinch dialog assertions in both orientations, requiring actual chart levels. Explicitly cancel tap eligibility on ACTION_CANCEL. Pending CI.

## Gate 2 — portrait label gesture recovery
- Run 46019013582 on 43a2f3fa6e6df8e5879fab4d477f4c32f9bff6e7: build/unit and landscape passed; portrait failed at `Chart missing after label drag`; later integration stages not executed. Artifact 15206924695. MainActivity live chart is not nested in a ScrollView. Exact cause not proven from job log alone.
- Make chart accessibility identity stable at construction instead of resetting it during draw. Wait for native visible/focused chart after dialog dismissal and drag, refresh touch coordinates, retain accessibility assertion and positive/negative dialog checks. Pending CI; no pass claimed.

## Gate 2 — historical chart separation
- Runs 46108748907 (both attempts) failed at real chart level assertion; build passed. Live-only guards can suppress levels for stale closed candles. Chart-only methods now permit validated stale history, explicitly label STALE and preserve existing trading calculation entry points. No assertion removed. Pending CI; exact runtime cause not yet proven.

## SQLite gate 3/4 — integration and refresh accumulation
- Integration commit c6c76fce0af616944b9a38b3e9c5c7b1f26ee1ad wired SQLite into activity and monitoring. CI not yet verified.
- Native refresh merges closed bars by timestamp, retains latest 300 in memory, and keeps original history in SQLite. Newer received revisions update existing rows. Freshness uses only current network response, not restored history; failures clear freshness. No synthetic bars or HistData. Pending regression and database recovery tests. M2/M3 historical aggregation remains a separate gate.

## SQLite gate 5 — M2/M3 historical aggregation
- Gate 4 workflow 46116605463 succeeded; artifact 15251652898. Dedicated SQLite tests remain pending.
- Merge up to 10000 stored native M1 bars with memory and latest REST response, validating source, timestamps and OHLC; prefer newest received duplicate. Existing complete aligned bucket algorithm retained. M2/M3 freshness inherits actual live M1 response; cache-only aggregation stays STALE. Pending CI and dedicated tests; 300 bars not yet demonstrated.

## SQLite gate 6 — H8/H12 aggregation
- Gate 5 workflow 46118825963 succeeded; artifact 15252773856. Actual 300 M2/M3 not yet verified.
- H8/H12 use complete UTC-aligned groups of two/three validated native H4 bars from REST, SQLite (up to 10000) and memory. Reject missing groups, invalid source/time/OHLC and overflow. Preserve actual source reception time and inherit H4 freshness. W1 unchanged. Pending CI and dedicated tests; actual 300 H8/H12 not established.
