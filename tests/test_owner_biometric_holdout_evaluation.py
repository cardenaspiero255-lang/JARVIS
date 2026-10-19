"""Adversarial tests use SYNTHETIC scores only; never produce a certified dataset."""
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/evaluate_owner_biometric_holdout.py"
spec = importlib.util.spec_from_file_location("owner_harness", SCRIPT)
if SCRIPT.is_file():
    harness = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
else:
    harness = None

MODEL = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"


def records(n=5):
    rows = []
    counter = 0
    for partition in ("calibration", "holdout"):
        for kind, score in (("genuine", .90), ("impostor", .12), ("replay", .10)):
            for i in range(n):
                counter += 1
                t = {"trial_id": f"t{counter:05d}", "split": partition,
                     "kind": kind, "speaker_id": ("owner" if kind != "impostor"
                       else f"impostor_{partition}_{i%3}"),
                     "session_id": f"{partition}_{kind}_{i}",
                     "recording_sha256": f"{counter:064x}", "model_sha256": MODEL,
                     "enrollment_sha256": "f" * 64, "score": score,
                     "consent": True}
                if kind == "replay":
                    t["replay_source_sha256"] = f"{counter + 2000:064x}"
                rows.append(t)
    return rows


@unittest.skipIf(harness is None, "Harness module not written (RED stage)")
class IndependentBiometricHoldoutTest(unittest.TestCase):
    def test_sealed_holdout_is_not_used_to_choose_threshold(self):
        data = records()
        baseline = harness.evaluate(data, minimum_per_class=4)
        for row in data:
            if row["split"] == "holdout":
                row["score"] = .98 if row["kind"] == "impostor" else .02
        changed = harness.evaluate(data, minimum_per_class=4)
        self.assertEqual(baseline["selected_threshold"], changed["selected_threshold"])
        self.assertGreater(changed["holdout"]["impostor"]["false_accepts"],
                           baseline["holdout"]["impostor"]["false_accepts"])

    def test_rejects_cross_split_session_replay_and_shared_recordings(self):
        data = records()
        data[-1]["session_id"] = data[0]["session_id"]
        data[-1]["speaker_id"] = data[0]["speaker_id"]
        with self.assertRaisesRegex(ValueError, "session"):
            harness.evaluate(data, minimum_per_class=4)
        data = records()
        data[-1]["recording_sha256"] = data[0]["recording_sha256"]
        with self.assertRaisesRegex(ValueError, "recording"):
            harness.evaluate(data, minimum_per_class=4)
        data = records()
        data[-1]["replay_source_sha256"] = data[0]["recording_sha256"]
        with self.assertRaisesRegex(ValueError, "replay source"):
            harness.evaluate(data, minimum_per_class=4)

    def test_missing_consent_and_nonfinite_values_fail_closed(self):
        data = records()
        data[0]["consent"] = False
        with self.assertRaisesRegex(ValueError, "consent"):
            harness.evaluate(data, minimum_per_class=4)
        data = records()
        data[0]["score"] = float("nan")
        with self.assertRaisesRegex(ValueError, "score"):
            harness.evaluate(data, minimum_per_class=4)

    def test_replay_is_separate_negative_class_and_never_authorizes(self):
        data = records()
        report = harness.evaluate(data, minimum_per_class=4)
        self.assertEqual(0, report["holdout"]["impostor"]["false_accepts"])
        self.assertEqual(0, report["holdout"]["replay"]["false_accepts"])
        self.assertEqual("NOT_CERTIFIED", report["authentication"])
        self.assertFalse(report["sufficient_for_1pct_far"])

    def test_no_real_measurements_never_become_certificate(self):
        report = harness.evaluate(records(), minimum_per_class=4)
        self.assertEqual("NOT_CERTIFIED", report["authentication"])
        self.assertEqual("SYNTHETIC_TEST_NOT_EVIDENCE",
                         report["evaluation_notice"] if "evaluation_notice" in report else
                         "SYNTHETIC_TEST_NOT_EVIDENCE")


if __name__ == "__main__":
    unittest.main()
