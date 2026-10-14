import tempfile
import unittest
from datetime import datetime, timezone
from uuid import uuid4
from jarvis_contracts.bridge import ProviderBridge, ApprovalRequired
from jarvis_contracts.persistence import DurableStore
from jarvis_contracts.authorization import ApprovalStore
from jarvis_contracts.integration import CapabilityRegistry


NOW = datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc)


def msg(action="review.requested", key="k", payload=None):
    return {"schemaVersion": "1.0", "messageId": str(uuid4()),
            "correlationId": str(uuid4()), "timestamp": "2026-10-14T10:00:00Z",
            "source": "alice", "target": "sentinel", "type": action,
            "idempotencyKey": key, "deadline": "2026-10-14T11:00:00Z",
            "payload": payload or {"pr": 2}}


class DurableBridgeTests(unittest.TestCase):
    def setUp(self):
        self.registry = CapabilityRegistry()
        self.registry.register("sentinel", "1.0", {"review.requested", "merge.execute"})

    def test_result_is_reused_after_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            calls = []
            def handler(command):
                calls.append(command)
                return {"ok": True}
            with DurableStore(path) as store:
                bridge = ProviderBridge(self.registry, {"sentinel": handler}, durable_store=store)
                self.assertEqual(bridge.dispatch(msg(), NOW), {"ok": True})
            with DurableStore(path) as store:
                bridge = ProviderBridge(self.registry, {"sentinel": handler}, durable_store=store)
                self.assertEqual(bridge.dispatch(msg(), NOW), {"ok": True})
            self.assertEqual(len(calls), 1)

    def test_uncertain_command_never_replays(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with DurableStore(path) as store:
                store.begin("alice", "k", ProviderBridge.fingerprint(msg()))
            calls = []
            with DurableStore(path) as store:
                bridge = ProviderBridge(self.registry, {"sentinel": lambda cmd: calls.append(cmd)}, durable_store=store)
                with self.assertRaises(RuntimeError):
                    bridge.dispatch(msg(), NOW)
            self.assertEqual(calls, [])

    def test_scoped_approval_required_for_sensitive_action(self):
        approvals = ApprovalStore()
        command = msg("merge.execute", key="merge")
        approvals.grant("alice", "sentinel", "merge.execute", ProviderBridge.fingerprint(command), expires_at=2000000000)
        bridge = ProviderBridge(self.registry, {"sentinel": lambda _: "ok"}, approval_store=approvals)
        self.assertEqual(bridge.dispatch(command, NOW), "ok")
        with self.assertRaises(ApprovalRequired):
            bridge.dispatch(msg("merge.execute", key="another", payload={"pr": 3}), NOW)


if __name__ == "__main__":
    unittest.main()
