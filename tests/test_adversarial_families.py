"""Additional adversarial family regression cases: authority, time and races."""
import math
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from uuid import uuid4

from jarvis_contracts.authorization import ApprovalStore, ApprovalDenied
from jarvis_contracts.bridge import ProviderBridge, ApprovalRequired
from jarvis_contracts.integration import CapabilityRegistry
from jarvis_contracts.protocol import InMemoryGateway

NOW = datetime(2026, 10, 14, 10, 30, tzinfo=timezone.utc)

def command(action):
    return {"schemaVersion": "1.0", "messageId": str(uuid4()),
            "correlationId": str(uuid4()), "timestamp": "2026-10-14T10:00:00Z",
            "source": "alice", "target": "system", "type": action,
            "idempotencyKey": "k", "deadline": "2026-10-14T11:00:00Z", "payload": {}}


class AdditionalAdversarialTests(unittest.TestCase):
    def test_unknown_side_effects_require_scoped_approval_by_default(self):
        for action in ("game.launch", "ui.tap", "shell.execute", "message.send", "network.optimize"):
            with self.subTest(action=action):
                registry = CapabilityRegistry()
                registry.register("system", "1.0", {action})
                bridge = ProviderBridge(registry, {"system": lambda _: self.fail("unauthorized")})
                with self.assertRaises(ApprovalRequired):
                    bridge.dispatch(command(action), NOW)

    def test_nonfinite_approval_expiry_and_now_rejected(self):
        approvals = ApprovalStore()
        for expiry in (math.nan, math.inf, -math.inf):
            with self.subTest(expiry=expiry), self.assertRaises(ValueError):
                approvals.grant("alice", "system", "shell.execute", "hash", expires_at=expiry)
        approvals.grant("alice", "system", "shell.execute", "hash", expires_at=100)
        with self.assertRaises(ApprovalDenied):
            approvals.consume("alice", "system", "shell.execute", "hash", math.nan)

    def test_in_memory_duplicate_commands_execute_once_under_race(self):
        msg = command("review.requested")
        count = []
        def handler(envelope):
            count.append(envelope)
            time.sleep(0.02)
            return {"ok": True}
        gateway = InMemoryGateway({"system": {"review.requested"}}, {"system": handler})
        with ThreadPoolExecutor(max_workers=6) as pool:
            results = list(pool.map(lambda _: gateway.dispatch(msg, NOW), range(6)))
        self.assertEqual(results, [{"ok": True}] * 6)
        self.assertEqual(len(count), 1)
