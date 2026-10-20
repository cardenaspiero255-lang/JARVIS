"""Voice Match evaluator adversarial tests: SYNTHETIC data, no voice certification."""
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "tools/evaluate_owner_biometric_holdout.py"
spec = importlib.util.spec_from_file_location("voice_match_replay_evaluator", source)
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
MODEL = harness.MODEL_SHA256


def corpus(negative_n=5, genuine_n=5, replay_origins=20):
    rows = []
    for split in ("calibration", "holdout"):
        for kind in ("genuine", "impostor", "replay"):
            n = genuine_n if kind == "genuine" else negative_n
            for i in range(n):
                label = f"{split}:{kind}:{i}"
                row = {
                    "trial_id": f"trial_{split}_{kind}_{i}",
                    "split": split, "kind": kind,
                    "speaker_id": ("owner" if kind != "impostor"
                                   else f"impostor_{split}_{i % 20}"),
                    "session_id": (f"{split}_owner_{i % 3}" if kind == "genuine"
                                   else f"{split}_{kind}_{i}"),
                    "recording_sha256": hashlib.sha256(label.encode()).hexdigest(),
                    "enrollment_sha256": "f" * 64,
                    "model_sha256": MODEL,
                    "score": {"genuine": .92, "impostor": .15, "replay": .10}[kind],
                    "consent": True,
                }
                if kind == "replay":
                    row["replay_source_sha256"] = hashlib.sha256(
                        f"source:{split}:{i % replay_origins}".encode()).hexdigest()
                rows.append(row)
    return rows


class VoiceMatchReplayIndependenceTest(unittest.TestCase):
    def test_replay_origin_cannot_cross_calibration_and_holdout(self):
        data = corpus()
        origin = next(r["replay_source_sha256"] for r in data
                      if r["split"] == "calibration" and r["kind"] == "replay")
        changed = next(r for r in data
                       if r["split"] == "holdout" and r["kind"] == "replay")
        changed["replay_source_sha256"] = origin
        with self.assertRaisesRegex(ValueError, "(?i)replay source.*split"):
            harness.evaluate(data, minimum_per_class=4)

    def test_same_replay_origin_can_repeat_within_one_partition(self):
        data = corpus()
        replay = [r for r in data
                  if r["split"] == "calibration" and r["kind"] == "replay"]
        replay[1]["replay_source_sha256"] = replay[0]["replay_source_sha256"]
        report = harness.evaluate(data, minimum_per_class=4)
        self.assertEqual("NOT_CERTIFIED", report["authentication"])

    def test_full_volume_synthetic_threshold_never_authenticates(self):
        # Exercise the real minimums using fake labels only as a UNIT TEST.
        # Never store these artificial metrics as benchmark evidence.
        data = corpus(negative_n=300, genuine_n=30, replay_origins=20)
        holdout = sorted((r for r in data if r["split"] == "holdout"),
                         key=lambda r: r["trial_id"])
        sealed = hashlib.sha256("\n".join(
            json.dumps(r, sort_keys=True, allow_nan=False, separators=(",", ":"))
            for r in holdout).encode()).hexdigest()
        report = harness.evaluate(data, expected_holdout_sha256=sealed)
        self.assertTrue(report["sufficient_for_1pct_far"])
        self.assertEqual("NOT_CERTIFIED", report["authentication"])
        self.assertFalse(report["baseline_non_regression_verified"])
        self.assertEqual(300, report["holdout"]["impostor"]["trials"])
        self.assertEqual(300, report["holdout"]["replay"]["trials"])


if __name__ == "__main__":
    unittest.main()
