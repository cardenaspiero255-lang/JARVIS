#!/usr/bin/env bash
# Real model, emulator-only. No output is allowed to certify microphone/TTS.
set -euo pipefail
out="app/build/diagnostics/qwen-quality"
mkdir -p "$out"
test -s app/build/generated/ggufQwenE2eAssets/instruct-qwen.gguf
for i in $(seq 0 11); do
  test -s "app/build/generated/ggufQwenE2eAssets/test-voice-16k-$i.pcm"
done
gradle --no-daemon -PggufQwenE2e=true :app:assembleDebug :app:assembleDebugAndroidTest
adb shell svc wifi disable
adb shell svc data disable
# Fail early on genuine Vosk recognition before spending CPU on GGUF decoding.
# This never substitutes the recognized transcript or weakens the exact gate.
gradle --no-daemon -PggufQwenE2e=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cardenaspiero255.jarvis.OfflineVoicePcmToTtsInstrumentedTest#syntheticSpanishPcmIsAccuratelyRecognizedOffline \
  :app:connectedDebugAndroidTest
gradle --no-daemon -PggufQwenE2e=true -Pandroid.testInstrumentationRunnerArguments.class=com.cardenaspiero255.jarvis.LocalQwenBenchmarkInstrumentedTest :app:connectedDebugAndroidTest
python3 - "$out/quality.json" <<'PY'
import json
import re
import statistics
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path("app/build/outputs/androidTest-results/connected/debug")
cases = []
all_tests = []
for file in root.rglob("*.xml"):
    try:
        xml = ET.parse(file).getroot()
    except ET.ParseError:
        continue
    all_tests += [t for t in xml.iter("testcase") if
                  "LocalQwenBenchmarkInstrumentedTest" in t.attrib.get("classname", "")]
if len(all_tests) != 1:
    raise SystemExit(f"QWEN_E2E_NOT_EXECUTED: expected 1, got {len(all_tests)}")
if any(all_tests[0].find(x) is not None for x in ("failure", "error", "skipped")):
    raise SystemExit("QWEN_E2E_FAILED_OR_SKIPPED")
logs = list(root.rglob(
    "logcat-com.cardenaspiero255.jarvis.LocalQwenBenchmarkInstrumentedTest-"
    "officialQwenWeightsAnswerTwoBoundedSpanishTurnsOffline.txt"))
if len(logs) != 1:
    raise SystemExit(f"QWEN_E2E_SOURCE_LOG_MISSING_OR_AMBIGUOUS: {len(logs)}")
for line in logs[0].read_text(errors="replace").splitlines():
    if "System.out:" not in line or "QWEN_BENCH_CASE " not in line:
        continue
    data = line.split("QWEN_BENCH_CASE ", 1)[1].strip()
    match = re.fullmatch(r"index=(\d+) duration_ms=(\d+) chars=(\d+) pss_kib=(\d+)", data)
    if not match:
        raise SystemExit("QWEN_E2E_INVALID_METRIC_FORMAT")
    index, latency, chars, memory = map(int, match.groups())
    if latency <= 0 or chars <= 0 or memory <= 0:
        raise SystemExit("QWEN_E2E_INVALID_MEASUREMENTS")
    cases.append(dict(index=index, latency_ms=latency,
                      generated_chars=chars, pss_kib=memory))
if sorted(x["index"] for x in cases) != [0, 1]:
    raise SystemExit("QWEN_E2E_CASES_MISSING_OR_DUPLICATE")
durations = sorted(x["latency_ms"] for x in cases)
p95 = durations[0] + (durations[-1] - durations[0]) * 0.95
report = dict(
    schema_version=1,
    model="Qwen/Qwen2.5-0.5B-Instruct-GGUF Q4_K_M",
    model_sha256="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
    device="ANDROID14_X86_64_EMULATOR",
    source="ORIGINAL_INSTRUMENTATION_LOGCAT",
    input="SCRIPTED_ASR_TRANSCRIPTS",
    network_on_device=False,
    cases=sorted(cases, key=lambda x: x["index"]),
    p50_latency_ms=statistics.median(durations),
    p95_interpolated_ms=p95,
    max_pss_kib=max(c["pss_kib"] for c in cases),
    semantic_certified=False,
    voice_e2e_verified=False,
    physical_vivo_verified=False,
    overall="NOT_CERTIFIED",
)
Path(sys.argv[1]).write_text(json.dumps(report, indent=2) + "\n")
print(f"QWEN_REAL_GGUF_PASS source=android14 cases={len(cases)} "
      f"p50_ms={report['p50_latency_ms']} p95_ms={p95:.1f} "
      "semantic_certified=false voice_e2e_verified=false")
PY

# Separate instrumented run: actual CI-synthesized Spanish PCM through the
# bundled Vosk model, Qwen JNI, and Android's offline Spanish TTS engine.
# A missing native ASR recognition or offline voice FAILS this quality gate.
# This is still not physical microphone-to-speaker certification.
gradle --no-daemon -PggufQwenE2e=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cardenaspiero255.jarvis.OfflineVoicePcmToTtsInstrumentedTest \
  :app:connectedDebugAndroidTest
