#!/usr/bin/env bash
# Dedicated CI: a real verified 101-MB model, not a fake JNI missing-path test.
# This script runs in the Android emulator runner working-directory apps/android.
set -euo pipefail
out="app/build/diagnostics/gguf-e2e"
mkdir -p "$out"
echo "=== Genuine GGUF JNI generation; SHA=${GITHUB_SHA:-unknown} ==="
test -s "app/build/generated/ggufE2eAssets/e2e-smollm.gguf"
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
echo "GGUF_E2E_PASS: native path verified on Android emulator" > "$out/summary.txt"
