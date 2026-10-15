"""Static regression for hosted observer's least-privilege network boundary."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class SentinelHostedObserverContract(unittest.TestCase):
    def test_script_never_posts_or_exposes_raw_token(self):
        script = (ROOT/"scripts/sentinel_health_check.py").read_text()
        self.assertIn('method="GET"', script)
        self.assertIn("NoRedirect", script)
        self.assertIn("/actions/workflows/serverless-github-monitor.yml/runs?branch=main", script)
        self.assertNotIn('"/actions/runs?branch=main', script)
        self.assertIn("JARVIS_SENTINEL_PUBLIC_REPORT", script)
        self.assertNotIn("method=\"POST\"", script)
        self.assertNotIn("requests.post(", script)
        self.assertNotIn('print(token)', script)

    def test_workflow_has_readonly_permissions_and_no_pr_code_execution(self):
        path=ROOT/".github/workflows/sentinel-health.yml"
        text=path.read_text()
        self.assertIn("contents: read", text)
        self.assertIn("actions: read", text)
        self.assertNotIn("contents: write", text)
        self.assertNotIn("pull-requests: write", text)
        self.assertNotIn("pull_request:", text)
        self.assertIn("python -m scripts.sentinel_health_check", text)
