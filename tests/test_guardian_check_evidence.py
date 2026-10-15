"""Read-only check evidence must never imply merge permission."""
import unittest

from jarvis_contracts.check_evidence import CheckState, summarize_checks
from jarvis_contracts.github_audit import ReadOnlySnapshot

SHA = "a" * 40


def check(name, *, status="completed", conclusion="success", sha=SHA):
    return {"name": name, "head_sha": sha, "status": status,
            "conclusion": conclusion}


class GuardianCheckEvidenceTests(unittest.TestCase):
    def test_all_observed_successes_are_reported_without_merge_authority(self):
        evidence = summarize_checks((check("test"), check("android")), SHA)
        self.assertEqual(evidence.state, CheckState.OBSERVED_SUCCESS)
        self.assertEqual(evidence.passing, ("test", "android"))
        self.assertEqual(evidence.count, 2)
        self.assertFalse(evidence.authorized_to_merge)

    def test_in_progress_remains_pending_even_if_conclusion_claims_success(self):
        evidence = summarize_checks((check("tests"),
            check("android", status="in_progress", conclusion="success")), SHA)
        self.assertEqual(evidence.state, CheckState.PENDING)
        self.assertEqual(evidence.pending, ("android",))
        self.assertFalse(evidence.authorized_to_merge)

    def test_failure_dominates_pending_and_green_checks(self):
        evidence = summarize_checks((check("good"),
            check("blocking", status="completed", conclusion="failure"),
            check("waiting", status="queued", conclusion=None)), SHA)
        self.assertEqual(evidence.state, CheckState.FAILING)
        self.assertEqual(evidence.failing, ("blocking",))
        self.assertEqual(evidence.pending, ("waiting",))

    def test_empty_incomplete_and_unverifiable_results_fail_closed(self):
        samples = [
            ((), True),
            ((check("green"),), False),
            ((check("stale", sha="b" * 40),), True),
            ((check("skipped", conclusion="skipped"),), True),
            ((check("neutral", conclusion="neutral"),), True),
            ((check("bad", conclusion=None),), True),
            ((check("invalid", status="new", conclusion="success"),), True),
            (({"head_sha": SHA, "name": "bad", "status": "completed"},), True),
            ((check(""),), True),
            ((check("x"),), 1),
        ]
        for checks, complete in samples:
            with self.subTest(checks=checks, complete=complete):
                evidence = summarize_checks(checks, SHA, complete=complete)
                self.assertEqual(evidence.state, CheckState.UNKNOWN)
                self.assertFalse(evidence.authorized_to_merge)

    def test_snapshot_exposes_diagnostics_without_additional_provider_calls(self):
        snapshot = ReadOnlySnapshot("acme/widget", 3, SHA,
                                    (check("android"),), ())
        self.assertEqual(snapshot.check_evidence.state, CheckState.OBSERVED_SUCCESS)
        self.assertFalse(snapshot.authorized_to_merge)
        self.assertFalse(snapshot.check_evidence.authorized_to_merge)

    def test_diagnostics_do_not_mutate_source_evidence(self):
        original = [check("test"), check("queued", status="waiting", conclusion=None)]
        evidence = summarize_checks(tuple(original), SHA)
        self.assertEqual(evidence.pending, ("queued",))
        with self.assertRaises((AttributeError, TypeError)):
            evidence.pending += ("fabricated",)
        self.assertEqual(original[1]["status"], "waiting")


if __name__ == "__main__":
    unittest.main()
