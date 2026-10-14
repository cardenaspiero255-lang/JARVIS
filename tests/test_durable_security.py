import tempfile
import unittest
from datetime import datetime, timezone
from jarvis_contracts.persistence import DurableStore, IdempotencyConflict
from jarvis_contracts.authorization import ApprovalStore, ApprovalDenied


class DurableTests(unittest.TestCase):
    def test_completed_command_survives_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with DurableStore(path) as store:
                store.complete("jarvis", "k1", "hash1", {"ok": True})
            with DurableStore(path) as store:
                self.assertEqual(store.get("jarvis", "k1", "hash1"), {"ok": True})

    def test_reused_key_with_different_fingerprint_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with DurableStore(tmp + "/state.db") as store:
                store.complete("jarvis", "k1", "hash1", {"ok": True})
                with self.assertRaises(IdempotencyConflict):
                    store.get("jarvis", "k1", "hash2")

    def test_uncertain_operation_not_replayed_after_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with DurableStore(path) as store:
                store.begin("jarvis", "k1", "hash1")
            with DurableStore(path) as store:
                self.assertEqual(store.status("jarvis", "k1"), "uncertain")


class AuthorizationTests(unittest.TestCase):
    def test_approval_binds_principal_target_action_payload_and_expiry(self):
        approvals = ApprovalStore()
        approvals.grant("alice", "github", "merge.execute", "sha-1", expires_at=200)
        approvals.require("alice", "github", "merge.execute", "sha-1", now=100)
        for args in [
            ("bob", "github", "merge.execute", "sha-1", 100),
            ("alice", "github", "merge.execute", "sha-2", 100),
            ("alice", "github", "merge.execute", "sha-1", 201),
        ]:
            with self.assertRaises(ApprovalDenied):
                approvals.require(*args)


if __name__ == "__main__":
    unittest.main()
