import unittest
from datetime import datetime, timezone
from uuid import uuid4
from jarvis_contracts.protocol import ContractError, InMemoryGateway
from jarvis_contracts.integration import CapabilityRegistry
from jarvis_contracts.bridge import ProviderBridge, ApprovalRequired


def command(action="review.requested", payload=None, key="same"):
    return dict(schemaVersion="1.0", messageId=str(uuid4()),
                correlationId=str(uuid4()), timestamp="2026-10-14T10:00:00Z",
                source="jarvis", target="sentinel", type=action,
                idempotencyKey=key, deadline="2026-10-14T11:00:00Z",
                payload={"pr": 1} if payload is None else payload)


NOW = datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc)


class SecurityRegressionTests(unittest.TestCase):
    def test_same_key_different_payload_is_conflict(self):
        gateway = InMemoryGateway({"sentinel": {"review.requested"}}, {"sentinel": lambda _: "ok"})
        gateway.dispatch(command(), NOW)
        with self.assertRaises(ContractError):
            gateway.dispatch(command(payload={"pr": 2}), NOW)

    def test_same_key_different_action_is_conflict(self):
        gateway = InMemoryGateway({"sentinel": {"review.requested", "review.cancelled"}}, {"sentinel": lambda _: "ok"})
        gateway.dispatch(command(), NOW)
        with self.assertRaises(ContractError):
            gateway.dispatch(command(action="review.cancelled"), NOW)

    def test_approval_cannot_be_granted_by_static_action_allowlist(self):
        registry = CapabilityRegistry()
        registry.register("sentinel", "1.0", {"merge.execute"})
        bridge = ProviderBridge(registry, {"sentinel": lambda _: "merged"},
                                approved_actions=frozenset({"merge.execute"}))
        with self.assertRaises(ApprovalRequired):
            bridge.dispatch(command(action="merge.execute"), NOW)


if __name__ == "__main__":
    unittest.main()
