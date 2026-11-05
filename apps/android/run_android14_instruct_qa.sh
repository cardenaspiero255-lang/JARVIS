#!/usr/bin/env bash
# Isolated, no-phone-network Spanish Instruct GGUF evaluation.
set -euo pipefail
out="app/build/diagnostics/instruct-quality"
mkdir -p "$out"
test -s app/build/generated/ggufInstructE2eAssets/instruct-smollm.gguf
gradle --no-daemon -PggufInstructE2e=true :app:assembleDebug :app:assembleDebugAndroidTest
adb shell svc wifi disable
adb shell svc data disable
gradle --no-daemon -PggufInstructE2e=true :app:connectedDebugAndroidTest
python3 - "$out/quality.json" <<'PY'
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

root = Path("app/build/outputs/androidTest-results/connected/debug")
xml_files = list(root.rglob("*.xml"))
matches = []
for path in xml_files:
    try:
        parent = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for element in parent.iter("testcase"):
        if "LocalGgufInstructInstrumentedTest" in element.attrib.get("classname", ""):
            matches.append(element)
if len(matches) != 1:
    raise SystemExit(f"INSTRUCT_E2E_NOT_RUN: required one test, found {len(matches)}")
if any(matches[0].find(k) is not None for k in ("failure", "error", "skipped")):
    raise SystemExit("INSTRUCT_E2E_FAILURE_OR_SKIPPED")
pattern = "logcat-com.cardenaspiero255.jarvis.LocalGgufInstructInstrumentedTest-" + (
    "pinnedInstructionWeightsAnswerFourSpanishQuestionsOffline.txt"
)
logs = list(root.rglob(pattern))
if len(logs) != 1:
    raise SystemExit(f"INSTRUCT_SOURCE_LOG_MISSING_OR_AMBIGUOUS: {len(logs)}")
lines = logs[0].read_text(encoding="utf-8", errors="replace").splitlines()
cases = {}
for line in lines:
    if "System.out:" not in line or "INSTRUCT_QUALITY_CASE " not in line:
        continue
    marker = line.split("INSTRUCT_QUALITY_CASE ", 1)[1]
    match = re.fullmatch(
        r"index=(\d+) id=([a-z_]+) matched=(true|false) duration_ms=(\d+) chars=(\d+)",
        marker.strip(),
    )
    if not match:
        raise SystemExit("INSTRUCT_QUALITY_INVALID_CASE_LINE")
    ix = int(match.group(1))
    if ix in cases or int(match.group(4)) <= 0 or int(match.group(5)) <= 0:
        raise SystemExit("INSTRUCT_QUALITY_DUPLICATE_OR_INVALID_METRIC")
    cases[ix] = {"id": match.group(2), "keyword_matched": match.group(3) == "true",
                 "latency_ms": int(match.group(4)), "output_chars": int(match.group(5))}
if set(cases) != set(range(4)):
    raise SystemExit(f"INSTRUCT_QUALITY_INCOMPLETE: indexes={sorted(cases)}")
report = {
    "schema_version": 1,
    "device": "ANDROID14_EMULATOR",
    "model": "unsloth/SmolLM2-135M-Instruct-GGUF",
    "model_sha256": "ed5fa30c487b282ec156c29062f1222e5c20875a944ac98289dbd242e947f747",
    "source": "ORIGINAL_ANDROID_INSTRUMENTATION_LOGCAT",
    "method": "FROZEN_SPANISH_KEYWORD_PROXY_NOT_SEMANTIC",
    "cases": [cases[i] for i in range(4)],
    "matches": sum(int(c["keyword_matched"]) for c in cases.values()),
    "semantic_certified": False,
    "physical_vivo_verified": False,
    "voice_e2e_verified": False,
    "overall": "NOT_CERTIFIED",
}
Path(sys.argv[1]).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("INSTRUCT_REAL_GGUF_PASS source=android14 cases=4 matched=" + str(report["matches"]) +
      " semantic_certified=false")
PY
