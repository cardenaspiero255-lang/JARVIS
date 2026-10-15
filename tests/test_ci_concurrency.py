"""Keep costly feature-branch checks current without canceling main history."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / ".github/workflows"


class WorkflowConcurrencyContracts(unittest.TestCase):
    def test_branch_isolation_and_main_exemption_in_all_build_workflows(self):
        for file in ("android-shell.yml", "contracts.yml"):
            with self.subTest(workflow=file):
                text = (ROOT / file).read_text(encoding="utf-8")
                self.assertIn("concurrency:", text)
                self.assertIn("group: ", text)
                self.assertIn("github.workflow", text)
                self.assertIn("github.event_name", text)
                self.assertIn("github.ref", text)
                self.assertIn("github.head_ref", text)
                self.assertIn("github.run_id", text)
                self.assertIn("refs/heads/main", text)
                self.assertIn("cancel-in-progress: true", text)
                self.assertIn("contents: read", text)

    def test_sentinel_schedule_remains_unchanged_and_uncancelled(self):
        text = (ROOT / "sentinel-health.yml").read_text(encoding="utf-8")
        self.assertIn("cancel-in-progress: false", text)


if __name__ == "__main__":
    unittest.main()
