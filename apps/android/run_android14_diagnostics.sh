#!/usr/bin/env bash
# Called as ONE bash process inside the Android emulator action.
# Preserve diagnostics even if Gradle returns nonzero or Android process aborts.
set -uo pipefail
out="app/build/diagnostics/android14"
mkdir -p "$out"
adb logcat -c || true
echo "=== Android 14 JNI instrumented tests; SHA=${GITHUB_SHA:-unknown} ==="
if gradle --no-daemon :app:connectedDebugAndroidTest; then
  result=0
else
  result=$?
fi
echo "=== Instrumented test exit: $result ==="
find app/build/outputs/androidTest-results app/build/test-results -type f -name '*.xml' -print -exec grep -n -A 35 -B 2 '<failure\|<error' {} \; 2>/dev/null || true
adb logcat -d -b crash -v threadtime > "$out/crash-logcat.txt" 2>&1 || true
adb logcat -d -v threadtime > "$out/full-logcat.txt" 2>&1 || true
grep -nE -A 12 -B 9 "FATAL EXCEPTION|Fatal signal|SIGSEGV|tombstone|sherpa|onnxruntime|Abort message|KWS_NATIVE_DIAG|AndroidRuntime" "$out/full-logcat.txt" | tail -n 240 || true
echo "Diagnostic logs: $out"
exit "$result"
