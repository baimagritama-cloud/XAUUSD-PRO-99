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
