import unittest
from datetime import datetime, timezone
from uuid import uuid4

from jarvis_contracts.integration import CapabilityRegistry
from jarvis_contracts.bridge import ProviderBridge, ApprovalRequired, ProviderFailure


def message(target="sentinel", action="review.requested"):
    return {
        "schemaVersion": "1.0", "messageId": str(uuid4()),
        "correlationId": str(uuid4()), "timestamp": "2026-10-14T10:00:00Z",
        "source": "jarvis", "target": target, "type": action,
        "idempotencyKey": "test-1", "deadline": "2026-10-14T11:00:00Z",
        "payload": {"repository": "example/project", "pr": 202},
    }


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.registry = CapabilityRegistry()
        self.registry.register("sentinel", "1.0", {"review.requested"})
        self.now = datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc)

    def test_dispatches_to_compatible_provider(self):
        bridge = ProviderBridge(self.registry, {"sentinel": lambda command: {"status": "accepted"}})
        self.assertEqual(bridge.dispatch(message(), self.now), {"status": "accepted"})

    def test_rejects_missing_capability(self):
        bridge = ProviderBridge(self.registry, {"sentinel": lambda command: "ok"})
        with self.assertRaises(ValueError):
            bridge.dispatch(message(action="merge.execute"), self.now)

    def test_rejects_sensitive_action_without_approval(self):
        self.registry.register("sentinel", "1.0", {"merge.execute"})
        bridge = ProviderBridge(self.registry, {"sentinel": lambda command: "ok"})
        with self.assertRaises(ApprovalRequired):
            bridge.dispatch(message(action="merge.execute"), self.now)

    def test_propagates_provider_failure_without_fake_success(self):
        def fail(command):
            raise RuntimeError("offline")
        bridge = ProviderBridge(self.registry, {"sentinel": fail})
        with self.assertRaises(ProviderFailure):
            bridge.dispatch(message(), self.now)

    def test_reuses_completed_result(self):
        calls = []
        def handler(command):
            calls.append(command)
            return {"status": "accepted"}
        bridge = ProviderBridge(self.registry, {"sentinel": handler})
        self.assertEqual(bridge.dispatch(message(), self.now), bridge.dispatch(message(), self.now))
        self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
