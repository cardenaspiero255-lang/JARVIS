#!/usr/bin/env python3
"""Extract measured GGUF JSON from preserved Gradle AndroidTest logcat.

Gradle instrumentation removes its target app after a successful run; using
`adb exec-out run-as APP cat files/gguf-qa-raw.json` AFTER connectedDebugAndroidTest
returns 'run-as: unknown package' on Android 14. The testcase-specific logcat
is captured by Android Gradle Plugin BEFORE app removal. Extract exactly one
JSON record instead of trusting stdout messages or a synthetic fixture.
"""
import argparse
import json
from pathlib import Path

LOG_FILE_NAME = (
    "logcat-com.cardenaspiero255.jarvis."
    "LocalGgufRealModelInstrumentedTest-"
    "genuinePinnedGgufProducesTextOffline.txt"
)
MARKER = "GGUF_QA_JSON_V1="


def extract(root: Path):
    files = list(root.rglob(LOG_FILE_NAME))
    if len(files) != 1:
        raise ValueError(f"Expected one genuine model test logcat, found {len(files)}")
    lines = files[0].read_text(encoding="utf-8", errors="replace").splitlines()
    values = []
    for line in lines:
        if "System.out:" in line and MARKER in line:
            values.append(line.split(MARKER, 1)[1].strip())
    if len(values) != 1:
        raise ValueError(
            f"GGUF_QA_EVIDENCE_MISSING_OR_DUPLICATED: {len(values)} metric records"
        )
    try:
        parsed = json.loads(values[0])
    except json.JSONDecodeError as error:
        raise ValueError("GGUF_QA_EVIDENCE_TRUNCATED_OR_INVALID") from error
    if not isinstance(parsed, dict) or parsed.get("schema_version") != 1:
        raise ValueError("GGUF_QA_EVIDENCE_INVALID_SCHEMA")
    return parsed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--test-results", required=True, type=Path)
    parser.add_argument("--dest", required=True, type=Path)
    args = parser.parse_args()
    payload = extract(args.test_results)
    args.dest.parent.mkdir(parents=True, exist_ok=True)
    args.dest.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print("GGUF_QA_LOGCAT_RECORD_EXTRACTED: real instrumentation metrics retrieved")


if __name__ == "__main__":
    main()
