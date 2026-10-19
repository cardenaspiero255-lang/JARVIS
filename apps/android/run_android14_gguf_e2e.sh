#!/usr/bin/env bash
# Dedicated CI: a real verified 101-MB model, not a fake JNI missing-path test.
# This script runs in the Android emulator runner working-directory apps/android.
set -euo pipefail
out="app/build/diagnostics/gguf-e2e"
mkdir -p "$out"
echo "=== Genuine GGUF JNI generation; SHA=${GITHUB_SHA:-unknown} ==="
test -s "app/build/generated/ggufE2eAssets/e2e-smollm.gguf"
# Production JARVIS has no INTERNET permission. Model weights were fetched
# and SHA-checked on the CI host, NOT the Android device.
# We disable device connectivity for the test, but Gradle on the build host
# MUST still resolve its UTP test runner plugins if not cached.
# A previous --offline here failed BEFORE running any instrumentation because
# com.android.tools.utp:android-test-plugin-host-additional-test-output
# was not cached. Host network is not an app/mobile network.
gradle --no-daemon -PggufE2e=true :app:assembleDebug :app:assembleDebugAndroidTest
adb shell svc wifi disable
adb shell svc data disable
gradle --no-daemon -PggufE2e=true :app:connectedDebugAndroidTest
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

files = list(Path("app/build/outputs/androidTest-results").rglob("*.xml"))
cases = []
for file in files:
    try:
        root = ET.parse(file).getroot()
    except ET.ParseError:
        continue
    for tc in root.iter("testcase"):
        if "LocalGgufRealModelInstrumentedTest" in tc.attrib.get("classname", ""):
            cases.append(tc)
if len(cases) != 1:
    raise SystemExit(f"GGUF_E2E_NOT_RUN: expected exactly one real model test, got {len(cases)}")
tc = cases[0]
if any(tc.find(x) is not None for x in ("failure", "error", "skipped")):
    raise SystemExit("GGUF_E2E_INVALID_RESULT: test failed or was skipped")
print("GGUF_E2E_PASS: one genuine model instrumentation test completed without skips")
PY
# The instrumentation writes only synthetic-case IDs and aggregate metrics.
# Fail closed if the app failed to persist the measurements.
adb exec-out run-as com.cardenaspiero255.jarvis.handsfreepreview \
  cat files/gguf-qa-raw.json > "$out/gguf-qa-raw.json"
test -s "$out/gguf-qa-raw.json"
python3 ../../tools/gguf_qa_report.py \
  --input "$out/gguf-qa-raw.json" \
  --json "$out/gguf-qa-results.json" \
  --markdown "$out/gguf-qa-results.md"
cat "$out/gguf-qa-results.md"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$out/gguf-qa-results.md" >> "$GITHUB_STEP_SUMMARY"
fi
echo "GGUF_E2E_PASS: native path and measured QA verified on Android emulator" > "$out/summary.txt"
