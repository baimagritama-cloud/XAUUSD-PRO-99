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

## Verified 2026-11-27 WIB
- Probe #3 run 45887882969, artifact 15149713847: downloaded XML inspected; tests=1, skipped=0, failures=0, errors=0. livePrimaryAndRealFallback output: LIVE PRIMARY events=12; LIVE FALLBACK events=12. Both endpoints HTTP 200. Controlled primary outage, real fallback HTTP fetch.
- Probe #2 run 45887861415 failed during Gradle classpath dependency resolution (kotlin-stdlib-jdk8:1.9.20), before calendar test execution. Subsequent probe passed.
- HEAD ed93b77907eb516830d8c0d11764d0d2b49e068e: build 45901327491 and emulator 45901327480 passed, unit test task successful, 12 screenshots and background monitoring assertions confirmed in logs.
- Remaining: device-level calendar notification delivery and audible bell/TTS evidence. Gate 8 remains OPEN. No claim of physical audio verification.

## Stage 3 evidence — 2026-11-27
Commit 6f4c0b34923eeb34f3930b9f281404f49ae68483: Android Build run 45917765114 and Emulator UI run 45917765070 succeeded. Logs explicitly report Stage 1 (12 screenshots), Stage 2 (background monitoring), and Stage 3 calendarNotificationDeliveryAndVoiceSafety OK (1 test), ALL STAGES PASSED. Stage 3 checks Android notification posting, duplicate record rejection and stale voice suppression using a controlled fixture. It does not prove real-release dispatch through the service or audible bell/TTS; emulator uses -no-audio. Device acceptance remains open. No feature removal or final APK.
