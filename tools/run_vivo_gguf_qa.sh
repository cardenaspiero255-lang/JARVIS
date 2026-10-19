#!/usr/bin/env bash
# Optional ONE-SHOT physical Vivo V25 Pro benchmark. Never run in cloud CI.
# Uses the same SHA-pinned GGUF as the real emulator E2E suite.
set -euo pipefail

if [[ "${1:-}" != "--accept-debug-test-model-reset" ]]; then
  echo "This installs a separate DEBUG JARVIS test build and replaces the"
  echo "model only inside that debug app's private storage."
  echo "No test will be run without --accept-debug-test-model-reset."
  exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
for command in adb gradle python3; do
  command -v "$command" >/dev/null || {
    echo "Missing tool: $command" >&2
    exit 2
  }
done

mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ "${#devices[@]}" -ne 1 ]]; then
  echo "Connect exactly one authorized physical Vivo via USB; got ${#devices[@]}." >&2
  exit 2
fi
serial="${devices[0]}"
model="$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')"
manufacturer="$(adb -s "$serial" shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
if [[ "$manufacturer" != "vivo" ]]; then
  echo "Refusing to claim Vivo benchmark for manufacturer '$manufacturer'." >&2
  exit 2
fi
expected_model="${JARVIS_EXPECTED_VIVO_MODEL:-V2158}"
if [[ "$model" != "$expected_model" ]]; then
  echo "Connected model '$model', expected '$expected_model'." >&2
  echo "If the OEM property differs, set JARVIS_EXPECTED_VIVO_MODEL explicitly." >&2
  exit 2
fi

# No live secrets or real microphone audio are stored in the report.
session="$(date -u +%Y%m%dT%H%M%SZ)"
serial_hash="$(python3 -c 'import hashlib,sys;print(hashlib.sha256(sys.argv[1].encode()).hexdigest())' "$serial")"
dest="$root/apps/android/app/build/generated/ggufE2eAssets/e2e-smollm.gguf"
out="$root/apps/android/app/build/diagnostics/gguf-vivo-$session"
mkdir -p "$out"

python3 "$root/tools/prepare_gguf_e2e_fixture.py" --dest "$dest"
# This single command installs/runs the isolated debug APK plus the test APK.
# Neither this script nor JARVIS changes the phone's Wi-Fi or Funtouch settings.
gradle -p apps/android --no-daemon -PggufE2e=true :app:connectedDebugAndroidTest

# Gradle uninstall happens before this script resumes; the testcase logcat
# is the durable on-host evidence. No need for run-as or privileged device files.
python3 "$root/tools/extract_gguf_qa_log.py" \
  --test-results "$root/apps/android/app/build/outputs/androidTest-results/connected/debug" \
  --dest "$out/gguf-qa-raw.json"
test -s "$out/gguf-qa-raw.json"
python3 - "$out/gguf-qa-raw.json" "$session" "$serial_hash" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
if payload.get("device", {}).get("source") != "physical_reported":
    raise SystemExit("Expected a physical-device run; do not label an emulator as Vivo")
payload["device"]["provenance"] = {
    "method": "usb_adb_instrumentation",
    "session_id": sys.argv[2],
    "adb_serial_sha256": sys.argv[3],
}
path.write_text(json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
PY
python3 "$root/tools/gguf_qa_report.py" \
  --input "$out/gguf-qa-raw.json" \
  --json "$out/gguf-qa-results.json" \
  --markdown "$out/gguf-qa-results.md"
echo "Measured physical-session results are in $out"
echo "Voice acoustic reliability, temperature/power and independent approval"
echo "remain unverified; do not call this Vivo physical certification."
