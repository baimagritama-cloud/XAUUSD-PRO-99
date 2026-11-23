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
    echo "=== instrumentation runner package ==="
    adb shell ls -lR /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots 2>&1
    echo "=== target application package ==="
    adb shell ls -lR /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots 2>&1
  } > artifacts/diagnostics/device-screenshot-files.txt

  mkdir -p artifacts/ui-screenshots/instrumentation artifacts/ui-screenshots/target-app
  adb pull /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots artifacts/ui-screenshots/instrumentation >/dev/null 2>&1 || true
  adb pull /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots artifacts/ui-screenshots/target-app >/dev/null 2>&1 || true

  adb shell dumpsys activity activities > artifacts/diagnostics/activity-activities.txt 2>&1
  echo "Collected screenshot and hierarchy files:"
  find artifacts/ui-screenshots -type f \( -name '*.png' -o -name '*.xml' \) -print 2>/dev/null
  return 0
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

# Landscape-only UI run; keeps all six pages and live SignalR/background monitoring test.
gradle --no-daemon :app:connectedDebugAndroidTest

# Screenshots are currently saved by SixPageUiTest in the instrumentation runner package.
# Also pull the target-app location for compatibility with older test revisions.
mkdir -p artifacts/ui-screenshots/instrumentation artifacts/ui-screenshots/target-app
adb shell ls -lR /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots || true
adb shell ls -lR /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots || true
adb pull /sdcard/Android/data/com.xauusdpro99.test/files/ui-screenshots artifacts/ui-screenshots/instrumentation || true
adb pull /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots artifacts/ui-screenshots/target-app || true

count=$(find artifacts/ui-screenshots -type f -name '*.png' ! -path '*/debug/*' | wc -l)
count=$(echo "$count" | tr -d '[:space:]')

echo "Expected 6 landscape page screenshots; found $count"
if [[ "$count" -ne 6 ]]; then
  echo "ERROR: Screenshot count mismatch!"
  exit 1
fi

echo "Success! All 6 screenshots found."
