import tempfile
import threading
import unittest
from datetime import datetime, timezone
from uuid import uuid4
from jarvis_contracts.persistence import DurableStore
from jarvis_contracts.authorization import ApprovalStore, ApprovalDenied
from jarvis_contracts.bridge import ProviderBridge
from jarvis_contracts.integration import CapabilityRegistry


def message():
    return dict(schemaVersion="1.0", messageId=str(uuid4()), correlationId=str(uuid4()),
                timestamp="2026-10-14T10:00:00Z", source="alice", target="sentinel",
                type="merge.execute", idempotencyKey="merge-1",
                deadline="2026-10-14T11:00:00Z", payload={"pr": 2})


class HardeningTests(unittest.TestCase):
    def test_approval_can_only_be_consumed_once(self):
        store = ApprovalStore()
        store.grant("alice", "sentinel", "merge.execute", "fingerprint", expires_at=200)
        store.consume("alice", "sentinel", "merge.execute", "fingerprint", now=100)
        with self.assertRaises(ApprovalDenied):
            store.consume("alice", "sentinel", "merge.execute", "fingerprint", now=100)

    def test_concurrent_claim_has_one_winner(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            results = []
            errors = []
            barrier = threading.Barrier(8)

            def claim():
                try:
                    # SQLite connections are owned by the thread that created them.
                    with DurableStore(path) as store:
                        barrier.wait(timeout=5)
                        result = store.claim("alice", "k", "fingerprint")
                        results.append(result)
                except Exception as exc:
                    errors.append(exc)

            threads = [threading.Thread(target=claim) for _ in range(8)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join(timeout=10)
            self.assertTrue(all(not thread.is_alive() for thread in threads))
            self.assertEqual(errors, [])
            self.assertEqual(results.count(True), 1)
            self.assertEqual(results.count(False), 7)

    def test_sensitive_approval_is_consumed_on_dispatch(self):
        approvals = ApprovalStore()
        command = message()
        approvals.grant("alice", "sentinel", "merge.execute", ProviderBridge.fingerprint(command),
                        expires_at=2000000000)
        registry = CapabilityRegistry()
        registry.register("sentinel", "1.0", {"merge.execute"})
        bridge = ProviderBridge(registry, {"sentinel": lambda _: "ok"}, approval_store=approvals)
        self.assertEqual(bridge.dispatch(command, datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc)), "ok")
        with self.assertRaises(ValueError):
            bridge.dispatch(message(), datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc))
