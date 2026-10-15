"""Evidence-first Ultra Sentinel serverless observer: no invented live endpoint."""
import unittest
from datetime import datetime, timezone, timedelta

from jarvis_contracts.sentinel_health import (
    SentinelHealthObserver, SentinelHealthError, HealthState
)

REPO = "cardenaspiero255-lang/Ultra-Sentinel"
NOW = datetime(2026, 10, 15, 2, 0, tzinfo=timezone.utc)
META = {"id": 1418338512, "full_name": REPO,
        "owner": {"id": 322222186}, "default_branch": "main", "archived": False}

def run(*, conclusion="success", date="2026-10-15T01:30:00Z",
        event="schedule", sha="a"*40, workflow="Ultra Sentinel — serverless GitHub observer"):
    return {"id": 123, "name": workflow, "head_sha": sha,
            "head_branch": "main", "event": event, "status": "completed",
            "conclusion": conclusion, "created_at": date,
            "path": ".github/workflows/serverless-github-monitor.yml"}

class SentinelHealthTests(unittest.TestCase):
    def test_recent_real_workflow_success_is_observation_not_deployment_proof(self):
        health = SentinelHealthObserver().inspect(META, {"workflow_runs": [run()]}, now=NOW)
        self.assertEqual(health.state, HealthState.RECENT_READONLY_SUCCESS)
        self.assertEqual(health.head_sha, "a"*40)
        self.assertFalse(health.continuous_service_verified)
        self.assertFalse(health.can_write)
        self.assertEqual(health.run_id, 123)

    def test_cannot_trust_forged_repo_identity(self):
        for forged in [dict(META, id=12), dict(META, full_name="attacker/repo"),
                       dict(META, owner={"id": 42}), dict(META, archived=True)]:
            with self.subTest(forged=forged), self.assertRaises(SentinelHealthError):
                SentinelHealthObserver().inspect(forged, {"workflow_runs": [run()]}, now=NOW)

    def test_failed_skipped_or_missing_runs_are_not_healthy(self):
        for report in [{"workflow_runs": []},
                       {"workflow_runs": [run(conclusion="failure")]},
                       {"workflow_runs": [run(conclusion="skipped")]}]:
            result = SentinelHealthObserver().inspect(META, report, now=NOW)
            self.assertNotEqual(result.state, HealthState.RECENT_READONLY_SUCCESS)

    def test_stale_success_is_not_misrepresented_as_current(self):
        result = SentinelHealthObserver().inspect(META,
            {"workflow_runs": [run(date="2026-10-14T19:00:00Z")]}, now=NOW)
        self.assertEqual(result.state, HealthState.STALE)

    def test_wrong_workflow_or_head_ref_cannot_attest(self):
        wrong = run()
        wrong["path"] = "attacker/.github/workflows/something-else.yml"
        result = SentinelHealthObserver().inspect(META, {"workflow_runs": [wrong]}, now=NOW)
        self.assertEqual(result.state, HealthState.NO_VERIFIED_RUN)
        forged = run(sha="not-a-sha")
        with self.assertRaises(SentinelHealthError):
            SentinelHealthObserver().inspect(META, {"workflow_runs": [forged]}, now=NOW)

    def test_future_or_malformed_time_fails_closed(self):
        for date in ["2126-10-15T02:00:00Z", "not-a-date"]:
            with self.assertRaises(SentinelHealthError):
                SentinelHealthObserver().inspect(META,
                    {"workflow_runs": [run(date=date)]}, now=NOW)
