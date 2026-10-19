#!/usr/bin/env python3
"""Local-only biometric score benchmark. Zero audio, voices or templates exported.

Usage:
  python tools/evaluate_owner_biometric_holdout.py --input private_trials.jsonl \
    --expected-holdout-sha256 HEX --output private_aggregate.json

The trials MUST come from consented, real, independently recorded speech.
Do not commit trials, templates, scores, recordings or JSON reports to GitHub.
A score is never owner authentication. No live threshold is written to Android.
"""
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import re

MODEL_SHA256 = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
HASH = re.compile(r"^[0-9a-f]{64}$")
PSEUDONYM = re.compile(r"^[a-zA-Z0-9_-]{3,48}$")
KINDS = ("genuine", "impostor", "replay")
SPLITS = ("calibration", "holdout")
Z95 = 1.6448536269514722  # One-sided 95% Wilson upper confidence bound


def _upper_95(errors, total):
    """One-sided Wilson upper confidence bound; no independence claim implied."""
    if total <= 0:
        return 1.0
    p = errors / total
    zz = Z95 * Z95
    return min(1.0, (p + zz / (2 * total) +
                     Z95 * math.sqrt(p * (1-p) / total + zz / (4 * total * total))) /
               (1 + zz / total))


def validate(rows):
    if not rows:
        raise ValueError("No independently scored biometric trials")
    seen_trials, seen_recordings, session_splits = set(), set(), {}
    recording_splits = {}
    for idx, row in enumerate(rows):
        if not isinstance(row, dict):
            raise ValueError("Invalid trial row")
        required = ("trial_id", "split", "kind", "speaker_id", "session_id",
                    "recording_sha256", "enrollment_sha256", "model_sha256",
                    "score", "consent")
        if any(key not in row for key in required):
            raise ValueError(f"Missing required trial metadata at row {idx}")
        trial_id = row["trial_id"]
        if not isinstance(trial_id, str) or not PSEUDONYM.fullmatch(trial_id) or trial_id in seen_trials:
            raise ValueError("Duplicate or invalid trial_id")
        seen_trials.add(trial_id)
        if row["split"] not in SPLITS or row["kind"] not in KINDS:
            raise ValueError("Invalid split or kind")
        if row["consent"] is not True:
            raise ValueError("Explicit, recorded consent required for every trial")
        for key in ("recording_sha256", "enrollment_sha256", "model_sha256"):
            if not isinstance(row[key], str) or not HASH.fullmatch(row[key]):
                raise ValueError(f"Invalid {key}")
        if row["model_sha256"] != MODEL_SHA256:
            raise ValueError("Model hash mismatch: evidence must use the pinned model")
        if row["recording_sha256"] == row["enrollment_sha256"]:
            raise ValueError("Probe recording must be independent of enrollment")
        score = row["score"]
        if isinstance(score, bool) or not isinstance(score, (float, int)) or not (
                math.isfinite(score) and -1.0 <= score <= 1.0):
            raise ValueError("Score must be a finite cosine within [-1,1]")
        if any(not isinstance(row[k], str) or not PSEUDONYM.fullmatch(row[k])
               for k in ("speaker_id", "session_id")):
            raise ValueError("Pseudonym metadata invalid")
        if row["kind"] == "impostor" and row["speaker_id"] == "owner":
            raise ValueError("Impostor cannot use owner identity")
        if row["kind"] in ("genuine", "replay") and row["speaker_id"] != "owner":
            raise ValueError("Genuine/replay must reference targeted owner")
        recording = row["recording_sha256"]
        if recording in seen_recordings:
            raise ValueError("Duplicate recording SHA-256 across trials")
        seen_recordings.add(recording)
        recording_splits[recording] = row["split"]
        session_key = (row["speaker_id"], row["session_id"])
        previous = session_splits.setdefault(session_key, row["split"])
        if previous != row["split"]:
            raise ValueError("Same session leaks across calibration and holdout")
        if row["kind"] == "replay":
            origin = row.get("replay_source_sha256")
            if not isinstance(origin, str) or not HASH.fullmatch(origin) or origin == recording:
                raise ValueError("Invalid replay source provenance")
            if origin == row["enrollment_sha256"]:
                raise ValueError("Replay source must not be enrollment recording")
    for row in rows:
        if row["kind"] == "replay":
            origin = row["replay_source_sha256"]
            if origin in recording_splits and recording_splits[origin] != row["split"]:
                raise ValueError("Replay source leaks across independent splits")

    # A holdout must not contain exactly the same impostor voice used to tune.
    calibration_impostors = {r["speaker_id"] for r in rows
                              if r["kind"] == "impostor" and r["split"] == "calibration"}
    holdout_impostors = {r["speaker_id"] for r in rows
                          if r["kind"] == "impostor" and r["split"] == "holdout"}
    if calibration_impostors & holdout_impostors:
        raise ValueError("Impostor speaker leaks across splits")
    for split in SPLITS:
        relevant = [r for r in rows if r["split"] == split]
        if len({r["session_id"] for r in relevant if r["kind"] == "genuine"}) < 2:
            raise ValueError(f"{split}: need independent genuine sessions")
        if len({r["speaker_id"] for r in relevant if r["kind"] == "impostor"}) < 3:
            raise ValueError(f"{split}: need at least 3 impostor speakers")


def _counts(rows, threshold):
    by_kind = {}
    for kind in KINDS:
        trials = [r for r in rows if r["kind"] == kind]
        accepted = sum(1 for r in trials if r["score"] >= threshold)
        errors = len(trials) - accepted if kind == "genuine" else accepted
        by_kind[kind] = {
            "trials": len(trials),
            "false_rejects" if kind == "genuine" else "false_accepts": errors,
            "error_rate": errors / len(trials) if trials else None,
            "error_rate_upper95": _upper_95(errors, len(trials)),
        }
    return by_kind


def evaluate(rows, minimum_per_class=300, expected_holdout_sha256=None):
    if minimum_per_class < 1:
        raise ValueError("Invalid minimum_per_class")
    validate(rows)
    by_split = {split: [r for r in rows if r["split"] == split] for split in SPLITS}
    for split, trials in by_split.items():
        for kind in KINDS:
            needed = min(30, minimum_per_class) if kind == "genuine" else minimum_per_class
            if sum(r["kind"] == kind for r in trials) < needed:
                raise ValueError(f"{split} {kind}: insufficient independent trials ({needed} needed)")

    # Compute hash over canonical metadata *before opening the holdout*.
    holdout_hash = hashlib.sha256(
        "\n".join(json.dumps(row, sort_keys=True, allow_nan=False, separators=(",", ":"))
                  for row in sorted(by_split["holdout"], key=lambda r: r["trial_id"]))
        .encode("utf8")
    ).hexdigest()
    holdout_precommitted = expected_holdout_sha256 == holdout_hash
    if expected_holdout_sha256 is not None and not holdout_precommitted:
        raise ValueError("Sealed holdout SHA-256 mismatch")

    calibration = by_split["calibration"]
    scores = sorted({float(r["score"]) for r in calibration})
    candidates = scores + [1.0 + 1e-9]
    # The holdout NEVER enters model/threshold selection.
    viable = []
    for threshold in candidates:
        metrics = _counts(calibration, threshold)
        imp = metrics["impostor"]["error_rate"]
        replay = metrics["replay"]["error_rate"]
        if imp <= .01 and replay == 0.0:
            viable.append((metrics["genuine"]["false_rejects"], -threshold, threshold))
    threshold = sorted(viable)[0][2] if viable else 1.0 + 1e-9
    calibration_metrics = _counts(calibration, threshold)
    held_metrics = _counts(by_split["holdout"], threshold)

    # N>=300 and no false accepts leads to ~<1% one-sided 95% upper CI.
    sufficient = all(held_metrics[k]["trials"] >= 300 and
                     held_metrics[k]["error_rate_upper95"] <= .01
                     for k in ("impostor", "replay"))
    sufficient = sufficient and all(calibration_metrics[k]["trials"] >= 300
                                    for k in ("impostor", "replay"))
    sufficient = sufficient and held_metrics["genuine"]["trials"] >= 30 and (
        held_metrics["genuine"]["error_rate"] <= .05)
    return {
        "authentication": "NOT_CERTIFIED",
        "evaluation_notice": "MANIFEST_PROVENANCE_AND_CONSENT_REQUIRE_INDEPENDENT_AUDIT",
        "model_sha256": MODEL_SHA256,
        "selected_threshold": threshold,
        "holdout_sha256": holdout_hash,
        "holdout_precommitted": holdout_precommitted,
        "sufficient_for_1pct_far": bool(sufficient and holdout_precommitted),
        "baseline_non_regression_verified": False,
        "calibration": calibration_metrics,
        "holdout": held_metrics,
        "limitations": [
            "Threshold is REPORT-ONLY; never deployed or applied to Android authorization.",
            "Replay remains independently evaluated; a cosine score cannot detect liveness.",
            "No real recording, permission, participant, or source authenticity checked here.",
            "No evidence of FAR/FRR improvement over a prior independently evaluated baseline.",
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True,
                        help="PRIVATE real scores JSONL, never upload to GitHub")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-holdout-sha256", type=str, required=True,
                        help="Holdout seal independently fixed before calibration")
    args = parser.parse_args()
    rows = [json.loads(line, parse_constant=lambda value: (_ for _ in ()).throw(
        ValueError("Non-finite number"))) for line in args.input.read_text().splitlines()
        if line.strip()]
    report = evaluate(rows, expected_holdout_sha256=args.expected_holdout_sha256)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print("Result:", report["authentication"], "| sealed holdout:",
          report["holdout_precommitted"], "| sufficient target:",
          report["sufficient_for_1pct_far"])


if __name__ == "__main__":
    main()
