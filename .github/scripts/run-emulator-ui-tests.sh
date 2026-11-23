#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p artifacts/diagnostics artifacts/ui-screenshots

collect_diagnostics() {
  local result=$?
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
  adb shell ls -lR /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots > artifacts/diagnostics/device-screenshot-files.txt 2>&1
  adb pull /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots artifacts/ui-screenshots >/dev/null 2>&1
  echo "Collected screenshot files:"
  find artifacts/ui-screenshots -type f -name '*.png' -print 2>/dev/null
  return 0
}
trap collect_diagnostics EXIT

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

for attempt in $(seq 1 10); do
  adb shell service check package | grep -q found
  adb shell service check activity | grep -q found
  sleep 2
done

adb logcat -c

# Landscape-only stabilization phase: six pages plus live SignalR/monitoring integration.
gradle --no-daemon :app:connectedDebugAndroidTest

adb shell ls -lR /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots
adb pull /sdcard/Android/data/com.xauusdpro99/files/ui-screenshots artifacts/ui-screenshots

count=$(find artifacts/ui-screenshots -type f -name '*.png' ! -path '*/debug/*' | wc -l)
echo "Expected 6 landscape page screenshots; found $count"
[[ "$count" -eq 6 ]]
