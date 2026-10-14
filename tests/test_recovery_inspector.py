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
                (SOURCE, KEY, FP, NOW, NOW-1),
            ]
            for source, key, fp, now, deadline in bad:
                with self.subTest(args=(source, key, fp, now, deadline)):
                    with self.assertRaises(InvalidRecoveryInput):
                        RecoveryInspector(db).inspect(source, key, fp,
                                                      now=now, deadline=deadline)
            self.assertIsNone(db.status(SOURCE, KEY))
