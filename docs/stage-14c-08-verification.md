# Stage 14C — item 8 verification

Status: OPEN, pending live verification and device audio evidence.

## Verified baseline
Commit 1f9ac73a7d906d739c24be0e46dc109f157d2677:
- Android Build: https://github.com/baimagritama-cloud/XAUUSD-PRO-99/actions/runs/45886963352 — success; :app:testDebugUnitTest and :app:assembleDebug.
- Emulator UI: https://github.com/baimagritama-cloud/XAUUSD-PRO-99/actions/runs/45886963311 — success; six landscape and six portrait screenshots, background monitoring assertions.
- Provider probe: https://github.com/baimagritama-cloud/XAUUSD-PRO-99/actions/runs/45882039629 — HTTP 200 from both providers; not proof of production parser acceptance.

## New verification gates
- LiveCalendarVerificationTest: primary HTTPS and production parser; controlled primary failure followed by real XOOMAR HTTPS response. Workflow Calendar Provider Probe must pass.
- EconomicNewsEngineTest: five milestones (120, 45, 30 minutes, release, actual), deduplication and stale data guards. Latest CI must pass.
- Voice: inspect real Android TTS initialization, bell and spoken Indonesian output on device, including notification settings and quiet hours. Existing source wiring is not proof of audible output.
- Confirm actual delivery with live release and foreground/background transition; do not fabricate values or claim audio success from a unit test.

No final APK, no Stage 15, and no item 9 until required gates are verified. Existing trading engines and SignalR retained.
