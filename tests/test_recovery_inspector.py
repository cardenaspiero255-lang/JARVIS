"""Omega Recovery: no unsafe replay after crash or uncertain provider result."""
import tempfile
import unittest

from jarvis_contracts.persistence import DurableStore, IdempotencyConflict
from jarvis_contracts.recovery_inspector import (
    RecoveryInspector, ResumeDisposition, InvalidRecoveryInput,
)

SOURCE = "jarvis"
KEY = "task-17"
FP = "a" * 64
NOW = 1800000000


class RecoveryInspectorTests(unittest.TestCase):
    def test_new_command_is_only_eligible_for_fresh_authorized_submission(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                  now=NOW, deadline=NOW+60)
            self.assertEqual(result.disposition, ResumeDisposition.UNSEEN)
            self.assertFalse(result.can_execute)
            self.assertIsNone(db.status(SOURCE, KEY))

    def test_uncertain_command_from_prior_process_requires_manual_reconciliation(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with DurableStore(path) as db:
                db.begin(SOURCE, KEY, FP)
            with DurableStore(path) as db:
                result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                      now=NOW, deadline=NOW+60)
                self.assertEqual(result.disposition, ResumeDisposition.RECONCILE)
                self.assertFalse(result.can_execute)
                self.assertIsNone(result.completed_result)

    def test_completed_command_reuses_only_exact_fingerprint(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            db.complete(SOURCE, KEY, FP, {"ok": True})
            result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                  now=NOW, deadline=NOW+60)
            self.assertEqual(result.disposition, ResumeDisposition.COMPLETED)
            self.assertEqual(result.completed_result, {"ok": True})
            self.assertFalse(result.can_execute)
            with self.assertRaises(IdempotencyConflict):
                RecoveryInspector(db).inspect(SOURCE, KEY, "b"*64,
                                              now=NOW, deadline=NOW+60)

    def test_null_completed_result_is_still_completed(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            db.complete(SOURCE, KEY, FP, None)
            result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                  now=NOW, deadline=NOW+60)
            self.assertEqual(result.disposition, ResumeDisposition.COMPLETED)
            self.assertIsNone(result.completed_result)

    def test_expired_deadline_blocks_even_cached_commands(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            db.complete(SOURCE, KEY, FP, {"ok": True})
            result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                  now=NOW+60, deadline=NOW+60)
            self.assertEqual(result.disposition, ResumeDisposition.EXPIRED)
            self.assertIsNone(result.completed_result)

    def test_untrusted_input_is_rejected_with_no_db_mutation(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            bad = [
                ("../jarvis", KEY, FP, NOW, NOW+60),
                (SOURCE, "key with spaces", FP, NOW, NOW+60),
                (SOURCE, KEY, "short", NOW, NOW+60),
                (SOURCE, KEY, FP, float("nan"), NOW+60),
                (SOURCE, KEY, FP, NOW, float("inf")),
            ]
            for source, key, fp, now, deadline in bad:
                with self.subTest(args=(source, key, fp, now, deadline)):
                    with self.assertRaises(InvalidRecoveryInput):
                        RecoveryInspector(db).inspect(source, key, fp,
                                                      now=now, deadline=deadline)
            self.assertIsNone(db.status(SOURCE, KEY))

    def test_past_deadline_is_expired_not_invalid(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/state.db") as db:
            result = RecoveryInspector(db).inspect(SOURCE, KEY, FP,
                                                  now=NOW, deadline=NOW-1)
            self.assertEqual(result.disposition, ResumeDisposition.EXPIRED)
            self.assertFalse(result.can_execute)
            self.assertIsNone(db.status(SOURCE, KEY))

    def test_read_consistent_snapshot_when_completion_races(self):
        """No COMPLETED status may be returned without its actual result."""
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/race.db"
            with DurableStore(path) as initial:
                initial.begin(SOURCE, KEY, FP)
            with DurableStore(path) as reading, DurableStore(path) as writer:
                original_get = reading.get
                def racing_get(source, key, fingerprint):
                    value = original_get(source, key, fingerprint)
                    writer.complete(SOURCE, KEY, FP, {"verified": "result"})
                    return value
                reading.get = racing_get
                advice = RecoveryInspector(reading).inspect(
                    SOURCE, KEY, FP, now=NOW, deadline=NOW + 60)
                if advice.disposition == ResumeDisposition.COMPLETED:
                    self.assertEqual(advice.completed_result, {"verified": "result"})
                else:
                    self.assertEqual(advice.disposition, ResumeDisposition.RECONCILE)

    def test_all_envelope_valid_identifiers_can_be_recovered(self):
        variants = [
            ("com.example.jarvis", "car:71/retry 2"),
            ("provider/team", "task#42"),
            ("x" * 160, "key." + "z" * 160),
        ]
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/id.db") as db:
            for source, key in variants:
                db.complete(source, key, FP, {"verified": True})
                result = RecoveryInspector(db).inspect(
                    source, key, FP, now=NOW, deadline=NOW + 60)
                self.assertEqual(result.disposition, ResumeDisposition.COMPLETED)
                self.assertEqual(result.completed_result, {"verified": True})

    def test_atomically_reports_null_completed_value(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/null.db") as db:
            db.complete("com.example", "request:1", FP, None)
            result = RecoveryInspector(db).inspect("com.example", "request:1", FP,
                                                   now=NOW, deadline=NOW+60)
            self.assertEqual(result.disposition, ResumeDisposition.COMPLETED)
            self.assertIsNone(result.completed_result)
