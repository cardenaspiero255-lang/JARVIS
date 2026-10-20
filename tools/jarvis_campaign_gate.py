#!/usr/bin/env python3
"""Fail-closed, aggregate-only campaign inventory for JARVIS.

Reads existing GGUF, KWS and independent owner holdout report types.
No JSON input is proof of authenticity. Never emits raw audio, speaker IDs,
recording hashes, transcripts, biometric scores, or false certification.
"""
import argparse
import json
import math
from pathlib import Path

GGUF_SHA = "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"
SPEAKER_SHA = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"


def _positive(value, key):
    if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
        raise ValueError(f"Invalid positive measure: {key}")
    return value


def _counts(value, name):
    if not isinstance(value, dict):
        raise ValueError(f"Missing {name} counts")
    n = value.get("trials")
    if type(n) is not int or n <= 0:
        raise ValueError(f"Invalid {name} trial count")
    return n


def _gguf(report):
    if not isinstance(report, dict) or report.get("schema_version") != 1 or \
            report.get("model_sha256") != GGUF_SHA:
        raise ValueError("Invalid or unpinned GGUF report")
    cert = report.get("certification", {})
    voice = report.get("voice", {})
    if not isinstance(cert, dict) or cert.get("overall") != "NOT_CERTIFIED" or \
            cert.get("voice_e2e_verified") is not False or \
            cert.get("physical_vivo_verified") is not False or \
            not isinstance(voice, dict) or voice.get("llm_tool_authority") is not False or \
            voice.get("asr_audio_validated") is not False or \
            voice.get("tts_audio_validated") is not False:
        raise ValueError("GGUF cannot assert voice, Vivo or tool authorization")
    metrics = report.get("latency_ms")
    memory = report.get("memory_kib")
    if not isinstance(metrics, dict) or not isinstance(memory, dict):
        raise ValueError("No measured GGUF latency or PSS")
    _positive(metrics.get("first"), "first")
    _positive(metrics.get("subsequent_p50"), "p50")
    _positive(metrics.get("subsequent_p95"), "p95")
    _positive(memory.get("peak_pss"), "pss")
    samples = metrics.get("subsequent_samples")
    if type(samples) is not int or samples < 3:
        raise ValueError("Insufficient repeated GGUF observations")
    device = report.get("device", {})
    if not isinstance(device, dict):
        raise ValueError("Missing GGUF device")
    source = device.get("source")
    if source not in ("emulator", "physical_reported"):
        raise ValueError("Unrecognized GGUF device provenance")
    if source == "physical_reported":
        if device.get("physical_provenance_supplied") is not True or \
                device.get("model") != "V2158" or \
                str(device.get("manufacturer", "")).lower() != "vivo":
            raise ValueError("Physical GGUF claim lacks Vivo/USB provenance")
        return "PHYSICAL_GGUF_METRICS_REPORTED"
    if device.get("physical_provenance_supplied") is not False:
        raise ValueError("Emulator cannot claim physical provenance")
    return "EMULATOR_METRICS_REPORTED"


def _owner(report):
    if not isinstance(report, dict) or report.get("authentication") != "NOT_CERTIFIED" or \
            report.get("model_sha256") != SPEAKER_SHA or \
            report.get("baseline_non_regression_verified") is not False:
        raise ValueError("Owner report missing model binding or fail-closed verdict")
    sealed = report.get("holdout_precommitted")
    enough = report.get("sufficient_for_1pct_far")
    if type(sealed) is not bool or type(enough) is not bool:
        raise ValueError("Missing sealed independent owner holdout decisions")
    for split in ("calibration", "holdout"):
        groups = report.get(split, {})
        if not isinstance(groups, dict):
            raise ValueError("Missing owner split")
        for kind in ("impostor", "replay"):
            _counts(groups.get(kind), f"{split}/{kind}")
    _counts(report.get("holdout", {}).get("genuine"), "holdout/genuine")
    if not sealed:
        return "COUNTS_REPORTED_NOT_SEALED"
    if not enough:
        return "STATISTICS_REPORTED_INSUFFICIENT"
    if any(report[split][kind]["trials"] < 300
           for split in ("calibration", "holdout")
           for kind in ("impostor", "replay")):
        raise ValueError("Owner report falsely claims enough independent trials")
    if report["holdout"]["genuine"]["trials"] < 30:
        raise ValueError("Insufficient independently reported genuine trials")
    # A report is not evidence that recordings and consent were authentic.
    return "STATISTICS_REPORTED_AUDIT_REQUIRED"


def _kws(report):
    if not isinstance(report, dict) or report.get("status") != "EVALUATED_REAL_AUDIO":
        raise ValueError("KWS report lacks native WAV evaluation status")
    cases = report.get("cases")
    metrics = report.get("metrics")
    if not isinstance(cases, list) or not cases or not isinstance(metrics, dict):
        raise ValueError("KWS requires real labeled case records")
    hashes = set()
    count = {"tp": 0, "fp": 0, "tn": 0, "fn": 0}
    for row in cases:
        if not isinstance(row, dict) or row.get("split") != "holdout":
            raise ValueError("Only independently labeled KWS holdout allowed")
        digest = row.get("sha256")
        if not isinstance(digest, str) or len(digest) != 64 or \
                any(ch not in "0123456789abcdef" for ch in digest) or digest in hashes:
            raise ValueError("Duplicate or invalid KWS recording hash")
        hashes.add(digest)
        truth, detected = row.get("wake"), row.get("detected")
        if type(truth) is not bool or type(detected) is not bool or \
                not isinstance(row.get("speaker"), str) or not row["speaker"]:
            raise ValueError("Invalid independent KWS label")
        count["tp" if truth and detected else
              "fn" if truth else
              "fp" if detected else "tn"] += 1
    if count["tp"] + count["fn"] < 1 or count["tn"] + count["fp"] < 1:
        raise ValueError("Both positive and negative real KWS trials required")
    if metrics.get("n") != len(cases) or any(
        type(metrics.get(key)) is not int or metrics[key] != value
        for key, value in count.items()
    ):
        raise ValueError("KWS case counts do not match claimed confusion matrix")
    expected = {
        "precision": count["tp"] / (count["tp"] + count["fp"])
                     if count["tp"] + count["fp"] else 0.0,
        "recall": count["tp"] / (count["tp"] + count["fn"]),
        "fpr": count["fp"] / (count["tn"] + count["fp"]),
        "fnr": count["fn"] / (count["tp"] + count["fn"]),
    }
    for key, value in expected.items():
        observed = metrics.get(key)
        if type(observed) not in (int, float) or not math.isfinite(observed) or \
                not math.isclose(observed, value, rel_tol=1e-9, abs_tol=1e-9):
            raise ValueError("KWS rates cannot differ from labeled cases")
    return "REAL_WAV_EVALUATION_REPORTED_AUDIT_REQUIRED"


def summarize(gguf_report=None, owner_report=None, kws_report=None):
    """A truthful progress inventory. Never treats artifacts as product certification."""
    sources = {
        "gguf": "PENDING",
        "vivo_gguf": "PENDING",
        "owner_biometrics": "PENDING",
        "kws": "PENDING",
    }
    if gguf_report is not None:
        gguf_state = _gguf(gguf_report)
        sources["gguf" if gguf_state == "EMULATOR_METRICS_REPORTED"
                else "vivo_gguf"] = gguf_state
    if owner_report is not None:
        sources["owner_biometrics"] = _owner(owner_report)
    if kws_report is not None:
        sources["kws"] = _kws(kws_report)
    return {
        "schema": "jarvis.integrated-evidence.v1",
        "certification": "NOT_CERTIFIED",
        "sources": sources,
        "real_audio_voice_e2e_verified": False,
        "vivo_full_session_verified": False,
        "owner_actions_authorized": False,
        "device_control_integration_certified": False,
        "independent_provenance_audited": False,
        "missing": [
            "Independent, consented real owner/impostor/replay recordings and audit",
            "Real KWS wake-positive and negative holdout provenance",
            "Physical Vivo V25 Pro audio ASR-to-GGUF-to-TTS run and STOP",
            "Screen-off, thermal, battery and lifecycle device measurements",
            "Independent anti-replay/liveness assessment",
        ],
        "notice": ("Reported aggregate metrics and static tests are not evidence "
                   "of a complete voice, biometric or physical certification."),
    }


def _read_json(path):
    if path is None:
        return None
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 5_000_000:
        raise ValueError("Only existing bounded non-symlink private evidence files")
    def invalid(value):
        raise ValueError("Nonfinite JSON is not valid evidence")
    return json.loads(path.read_text(encoding="utf-8"), parse_constant=invalid)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gguf", type=Path, help="Private gguf-qa-results.json")
    parser.add_argument("--owner", type=Path, help="Private biometric aggregate JSON")
    parser.add_argument("--kws", type=Path, help="Private KWS real WAV report")
    parser.add_argument("--output", type=Path, required=True,
                        help="Aggregate-only inventory output path")
    args = parser.parse_args()
    result = summarize(_read_json(args.gguf), _read_json(args.owner),
                       _read_json(args.kws))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                           encoding="utf-8")
    print("JARVIS_EVIDENCE_INVENTORY: NOT_CERTIFIED; "
          "no raw audio, scores, device IDs or transcripts emitted")


if __name__ == "__main__":
    main()
