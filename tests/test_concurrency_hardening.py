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
            with DurableStore(path) as first, DurableStore(path) as second:
                results = []
                barrier = threading.Barrier(2)
                def claim(store):
                    barrier.wait()
                    results.append(store.claim("alice", "k", "fingerprint"))
                t1 = threading.Thread(target=claim, args=(first,))
                t2 = threading.Thread(target=claim, args=(second,))
                t1.start(); t2.start(); t1.join(); t2.join()
                self.assertCountEqual(results, [True, False])

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
