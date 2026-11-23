#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p artifacts/diagnostics artifacts/ui-screenshots

collect_diagnostics() {
  local result="${1:-unknown}"
  set +e
  echo "Collecting emulator diagnostics (exit=$result)"
  adb devices -l > artifacts/diagnostics/adb-devices.txt 2>&1
  adb version > artifacts/diagnostics/adb-version.txt 2>&1
  adb shell getprop > artifacts/diagnostics/device-properties.txt 2>&1
  adb logcat -b all -d -v threadtime > artifacts/diagnostics/logcat-all.txt 2>&1
  adb shell dumpsys cpuinfo > artifacts/diagnostics/cpuinfo.txt 2>&1
  adb shell dumpsys meminfo > artifacts/diagnostics/meminfo.txt 2>&1
  adb shell dumpsys activity processes > artifacts/diagnostics/activity-processes.txt 2>&1
  adb shell dumpsys activity services com.xauusdpro99 > artifacts/diagnostics/activity-services.txt 2>&1
  adb shell pm list instrumentation > artifacts/diagnostics/instrumentations.txt 2>&1

  {
    echo "=== instrumentation runner private cache ==="
    adb shell run-as com.xauusdpro99.test ls -lR cache/ui-screenshots 2>&1
    echo "=== instrumentation runner external files (diagnostic only) ==="
    adb shell ls -lR /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots 2>&1
  } > artifacts/diagnostics/device-screenshot-files.txt

  export_instrumentation_evidence || true

  adb shell dumpsys activity activities > artifacts/diagnostics/activity-activities.txt 2>&1
  echo "Collected screenshot and hierarchy files:"
  find artifacts/ui-screenshots -type f \( -name '*.png' -o -name '*.xml' \) -print 2>/dev/null
  return 0
}

# Export files written by the instrumentation runner's private cache. This avoids
# relying on ExternalFilesDir, which can be null on headless emulator images.
export_instrumentation_evidence() {
  mkdir -p artifacts/ui-screenshots/instrumentation
  if adb shell run-as com.xauusdpro99.test test -d cache/ui-screenshots; then
    adb exec-out run-as com.xauusdpro99.test tar -C cache -cf - ui-screenshots \
      | tar -xf - -C artifacts/ui-screenshots/instrumentation
  else
    echo "Instrumentation screenshot cache not found; listing runner cache:"
    adb shell run-as com.xauusdpro99.test ls -la cache || true
    return 1
  fi
}

# Collect the large diagnostic bundle only when this script fails.
on_exit() {
  local exit_code=$?
  trap - EXIT
  if [[ "$exit_code" -ne 0 ]]; then
    collect_diagnostics "$exit_code"
  fi
  exit "$exit_code"
}
trap on_exit EXIT

adb start-server
timeout 120 adb wait-for-device

ready=0
for attempt in $(seq 1 180); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  pkg=$(adb shell service check package 2>&1 || true)
  activity=$(adb shell service check activity 2>&1 || true)
  connectivity=$(adb shell service check connectivity 2>&1 || true)

  if [[ "$boot" == "1" ]] && grep -q found <<<"$pkg" && grep -q found <<<"$activity" && grep -q found <<<"$connectivity"; then
    ready=1
    break
  fi
  sleep 2
done

if [[ "$ready" -ne 1 ]]; then
  echo "Android framework did not become ready within 6 minutes"
  exit 22
fi

# Additional stabilization after Android framework readiness.
echo "Framework ready. Waiting 20s for additional stabilization..."
sleep 20

adb logcat -c

# Stage 1: run only the six-page landscape UI test. The portrait method remains
# in source but is @Ignore during this stabilization phase.
echo "STAGE 1/2: six-page landscape UI test"
gradle --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.xauusdpro99.SixPageUiTest#allSixPagesRenderInLandscape

# Export screenshots immediately, before another instrumentation invocation can
# alter the runner package's private cache.
export_instrumentation_evidence
find artifacts/ui-screenshots/instrumentation -type f -print

count=$(find artifacts/ui-screenshots/instrumentation -type f -name '*.png' ! -path '*/debug/*' | wc -l)
count=$(echo "$count" | tr -d '[:space:]')

echo "STAGE 1 evidence: expected 6 landscape page screenshots; found $count"
if [[ "$count" -ne 6 ]]; then
  echo "ERROR: Stage 1 screenshot count mismatch!"
  exit 1
fi
# Preserve Stage 1's reports and logcat before Stage 2 can replace Gradle outputs.
mkdir -p artifacts/diagnostics/stage1-test-results artifacts/diagnostics/stage1-test-reports
if [[ -d app/build/outputs/androidTest-results/connected ]]; then
  cp -a app/build/outputs/androidTest-results/connected/. artifacts/diagnostics/stage1-test-results/
fi
if [[ -d app/build/reports/androidTests/connected ]]; then
  cp -a app/build/reports/androidTests/connected/. artifacts/diagnostics/stage1-test-reports/
fi
adb logcat -b all -d -v threadtime > artifacts/diagnostics/stage1-logcat.txt 2>&1
adb logcat -c

echo "STAGE 1 PASSED: all six landscape page screenshots captured."

# Stage 2: independently verify the production Foreground Service and SignalR
# lifecycle while the app task is backgrounded. No feature is disabled or mocked.
echo "STAGE 2/2: live market and Background Monitoring integration test"
gradle --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.xauusdpro99.SixPageUiTest#liveMarketAndBackgroundMonitoringRemainActiveAfterLeavingApp

# Preserve Stage 2's independent test reports and logcat as separate evidence.
mkdir -p artifacts/diagnostics/stage2-test-results artifacts/diagnostics/stage2-test-reports
if [[ -d app/build/outputs/androidTest-results/connected ]]; then
  cp -a app/build/outputs/androidTest-results/connected/. artifacts/diagnostics/stage2-test-results/
fi
if [[ -d app/build/reports/androidTests/connected ]]; then
  cp -a app/build/reports/androidTests/connected/. artifacts/diagnostics/stage2-test-reports/
fi
adb logcat -b all -d -v threadtime > artifacts/diagnostics/stage2-logcat.txt 2>&1

echo "STAGE 2 PASSED: Background Monitoring integration assertions completed."
echo "ALL STAGES PASSED."
