"""Regression tests for *honest* 50-feature QA inventory."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/qa_coverage_matrix.py"


def loader():
    spec = importlib.util.spec_from_file_location("qa_coverage_matrix", SCRIPT)
    if not SCRIPT.is_file():
        raise FileNotFoundError("QA inventory script is not implemented")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CoverageMatrixContract(unittest.TestCase):
    def test_all_fifty_ids_accounted_for_without_claiming_certification(self):
        module = loader()
        report = module.build_report(ROOT)
        self.assertEqual(50, report["total_features"])
        self.assertEqual(list(range(1, 51)), [
            f["id"] for f in report["features"]
        ])
        self.assertEqual("NOT_CERTIFIED", report["product_status"])
        self.assertEqual(0, report["features_certified"])
        self.assertIn("hardware", report["unverified_evidence_classes"])
        self.assertIn("real_owner_biometrics",
                      report["unverified_evidence_classes"])

    def test_existing_test_files_are_checked_and_misleading_results_rejected(self):
        module = loader()
        report = module.build_report(ROOT)
        by_id = {f["id"]: f for f in report["features"]}
        self.assertTrue(by_id[35]["defined_tests"])
        self.assertTrue(by_id[47]["defined_tests"])
        self.assertEqual("NO_MAPPED_TESTS", by_id[3]["automated_coverage"])
        self.assertEqual("NO_MAPPED_TESTS", by_id[45]["automated_coverage"])
        self.assertNotEqual("VERIFIED", by_id[47]["automated_coverage"])
        self.assertFalse(report["device_test_executed_here"])

    def test_missing_coverage_does_not_silently_disappear(self):
        module = loader()
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            (tmpdir / "docs").mkdir()
            original = (ROOT / "docs/SSS_PLUS_50_TRACKER.md").read_text()
            (tmpdir / "docs/SSS_PLUS_50_TRACKER.md").write_text(
                original.replace("| 50 |", "| 51 |"))
            with self.assertRaises(ValueError):
                module.build_report(tmpdir)

    def test_results_report_does_not_accept_fake_pass_state_from_path_existence(self):
        module = loader()
        report = module.build_report(ROOT)
        for feature in report["features"]:
            self.assertNotIn("PASS", feature["automated_coverage"])
            self.assertNotIn("SUCCESS", feature["automated_coverage"])
        self.assertFalse(report["voice_owner_authorized"])
        self.assertFalse(report["funtouch_screen_off_certified"])


if __name__ == "__main__":
    unittest.main()
