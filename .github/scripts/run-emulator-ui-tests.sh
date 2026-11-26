#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p artifacts/diagnostics artifacts/ui-screenshots

LIVE_LOGCAT_PID=""
LIVE_WATCHER_PID=""

start_live_evidence_capture() {
  local stage="$1"
  local dir="artifacts/diagnostics/${stage}-live"
  mkdir -p "$dir"

  # Keep a continuous logcat stream while Gradle/UTP is running. UTP uninstalls
  # the test APK during teardown, before the Gradle command returns on failure.
  adb logcat -b all -v threadtime > "$dir/logcat-live.txt" 2>&1 &
  LIVE_LOGCAT_PID=$!

  # Avoid concurrent package-manager and run-as polling during instrumentation.
  LIVE_WATCHER_PID=""
}

stop_live_evidence_capture() {
  if [[ -n "${LIVE_WATCHER_PID:-}" ]]; then
    kill "$LIVE_WATCHER_PID" 2>/dev/null || true
    wait "$LIVE_WATCHER_PID" 2>/dev/null || true
    LIVE_WATCHER_PID=""
  fi
  if [[ -n "${LIVE_LOGCAT_PID:-}" ]]; then
    kill "$LIVE_LOGCAT_PID" 2>/dev/null || true
    wait "$LIVE_LOGCAT_PID" 2>/dev/null || true
    LIVE_LOGCAT_PID=""
  fi
}

collect_diagnostics() {
  local result="${1:-unknown}"
  set +e
  echo "Collecting emulator diagnostics (exit=$result)"
  timeout 30 adb devices -l > artifacts/diagnostics/adb-devices.txt 2>&1
  timeout 30 adb version > artifacts/diagnostics/adb-version.txt 2>&1
  timeout 30 adb shell getprop > artifacts/diagnostics/device-properties.txt 2>&1
  timeout 30 adb logcat -b all -d -v threadtime > artifacts/diagnostics/logcat-all.txt 2>&1
  timeout 30 adb shell dumpsys cpuinfo > artifacts/diagnostics/cpuinfo.txt 2>&1
  timeout 30 adb shell dumpsys meminfo > artifacts/diagnostics/meminfo.txt 2>&1
  timeout 30 adb shell dumpsys activity processes > artifacts/diagnostics/activity-processes.txt 2>&1
  timeout 30 adb shell dumpsys activity services com.xauusdpro99 > artifacts/diagnostics/activity-services.txt 2>&1
  timeout 30 adb shell pm list instrumentation > artifacts/diagnostics/instrumentations.txt 2>&1

  {
    echo "=== instrumentation runner private cache ==="
    adb shell run-as com.xauusdpro99.test ls -lR cache/ui-screenshots 2>&1
    echo "=== instrumentation runner external files (diagnostic only) ==="
    adb shell ls -lR /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots 2>&1
  } > artifacts/diagnostics/device-screenshot-files.txt

  export_instrumentation_evidence || true

  timeout 30 adb shell dumpsys activity activities > artifacts/diagnostics/activity-activities.txt 2>&1
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
  stop_live_evidence_capture
  if [[ "$exit_code" -ne 0 ]]; then
    collect_diagnostics "$exit_code"
  fi
  exit "$exit_code"
}
trap on_exit EXIT

adb start-server
timeout 120 adb wait-for-device

# Require six consecutive healthy probes with the same system_server PID.
wait_for_stable_framework() {
  local stage="$1" deadline=$((SECONDS + 360)) stable=0
  local boot pkg activity connectivity packages pid previous_pid=""
  local log="artifacts/diagnostics/${stage}-framework-readiness.txt"
  while (( SECONDS < deadline )); do
    boot=$(timeout 10 adb shell getprop sys.boot_completed 2>&1 | tr -d '\r' || true)
    pkg=$(timeout 10 adb shell service check package 2>&1 | tr -d '\r' || true)
    activity=$(timeout 10 adb shell service check activity 2>&1 | tr -d '\r' || true)
    connectivity=$(timeout 10 adb shell service check connectivity 2>&1 | tr -d '\r' || true)
    pid=$(timeout 10 adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    packages=$(timeout 15 adb shell pm path android 2>&1 | tr -d '\r' || true)
    if [[ "$boot" == "1" && "$pkg" == "Service package: found" && "$activity" == "Service activity: found" && "$connectivity" == "Service connectivity: found" && "$pid" =~ ^[0-9]+$ && "$packages" == package:* ]]; then
      if [[ "$pid" == "$previous_pid" ]]; then
        stable=$((stable + 1))
      else
        stable=1
      fi
    else
      stable=0
    fi
    previous_pid="$pid"
    printf '%s stable=%s boot=%s pid=%s package=%s activity=%s connectivity=%s pm=%s\n' "$(date -u +%FT%TZ)" "$stable" "$boot" "$pid" "$pkg" "$activity" "$connectivity" "$packages" >> "$log"
    if (( stable >= 6 )); then
      echo "Framework stable for $stage (six consecutive probes)."
      return 0
    fi
    sleep 5
  done
  echo "Android framework failed stability gate for $stage; see $log"
  return 22
}

timeout 30 adb logcat -b all -d -v threadtime > artifacts/diagnostics/pre-stage1-logcat.txt 2>&1 || true
wait_for_stable_framework stage1

# Install once and retain packages until evidence has been collected.
gradle --no-daemon :app:dependencies --configuration androidTestUtil > artifacts/diagnostics/test-util-dependencies.txt
orchestrator=$(find "$HOME/.gradle/caches/modules-2/files-2.1/androidx.test/orchestrator/1.6.1" -name '*.apk' -print -quit)
services=$(find "$HOME/.gradle/caches/modules-2/files-2.1/androidx.test.services/test-services" -name '*.apk' -print -quit)
test -f "$orchestrator"
test -f "$services"
timeout 180 adb install -r -t "$services"
timeout 180 adb install -r -t "$orchestrator"
timeout 180 adb install -r -t app/build/outputs/apk/debug/app-debug.apk
timeout 180 adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
wait_for_stable_framework stage1-installed
timeout 30 adb shell dumpsys package androidx.test.orchestrator > artifacts/diagnostics/orchestrator-package.txt
timeout 30 adb shell dumpsys package androidx.test.services > artifacts/diagnostics/test-services-package.txt
timeout 30 adb shell dumpsys package queries > artifacts/diagnostics/package-visibility.txt
grep -F 'OrchestratorService' artifacts/diagnostics/orchestrator-package.txt
timeout 30 adb shell pm list instrumentation > artifacts/diagnostics/installed-instrumentations.txt
grep -F 'com.xauusdpro99.test/androidx.test.runner.AndroidJUnitRunner' artifacts/diagnostics/installed-instrumentations.txt

run_orchestrated() {
  local stage="$1" method="$2" result=0
  local output="artifacts/diagnostics/${stage}-instrumentation.txt"
  timeout 600 adb shell 'CLASSPATH=$(pm path androidx.test.services | cut -d: -f2) app_process / androidx.test.services.shellexecutor.ShellMain am instrument -w -r -e class com.xauusdpro99.SixPageUiTest#'"$method"' -e targetInstrumentation com.xauusdpro99.test/androidx.test.runner.AndroidJUnitRunner androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator' > "$output" 2>&1 || result=$?
  cat "$output"
  [[ "$result" -eq 0 ]] || return "$result"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=|INSTRUMENTATION_STATUS_CODE: -[12]' "$output"; then
    return 1
  fi
  grep -Eq 'OK \(1 test\)|Tests found: 1, Tests run: 1,  Failures: 0' "$output"
}

# Stage 1: run only the six-page landscape UI test. The portrait method remains
# in source but is @Ignore during this stabilization phase.
echo "STAGE 1/2: six-page landscape UI test"
start_live_evidence_capture stage1
run_orchestrated stage1 allSixPagesRenderInLandscape
stop_live_evidence_capture

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
wait_for_stable_framework stage2
echo "STAGE 2/2: live market and Background Monitoring integration test"
start_live_evidence_capture stage2
run_orchestrated stage2 liveMarketAndBackgroundMonitoringRemainActiveAfterLeavingApp
stop_live_evidence_capture

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
