"""Monitor statuses do not confuse irrelevant workflow skips with failures."""
import unittest
from scripts.sentinel_health_check import exit_code_for_state
from jarvis_contracts.sentinel_health import HealthState

class SentinelObserverExitTests(unittest.TestCase):
    def test_relevant_workflow_success_is_healthy_exit(self):
        self.assertEqual(exit_code_for_state(HealthState.RECENT_READONLY_SUCCESS.value), 0)

    def test_skipped_unrelated_event_is_neutral_but_not_healthy(self):
        self.assertEqual(exit_code_for_state(HealthState.SKIPPED.value), 0)
        self.assertNotEqual(HealthState.SKIPPED, HealthState.RECENT_READONLY_SUCCESS)

    def test_failed_stale_or_unverifiable_observation_alerts(self):
        for state in (HealthState.RECENT_FAILURE.value, HealthState.STALE.value,
                      HealthState.NO_VERIFIED_RUN.value, "UNVERIFIED", "unknown"):
            with self.subTest(state=state):
                self.assertEqual(exit_code_for_state(state), 2)
