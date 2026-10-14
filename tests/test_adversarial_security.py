"""Adversarial regression cases: do not trust optimistic transport or stale approvals."""
import io
import tempfile
import unittest
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from uuid import uuid4

from jarvis_contracts.transport import SecureTransport, TransportError
from jarvis_contracts.authorization import ApprovalStore
from jarvis_contracts.bridge import ProviderBridge
from jarvis_contracts.integration import CapabilityRegistry
from jarvis_contracts.persistence import DurableStore, IdempotencyConflict
from jarvis_contracts.protocol import ContractError


NOW = datetime(2026, 10, 14, 11, 30, tzinfo=timezone.utc)


def command(action="merge.execute", deadline="2026-10-14T11:00:00Z"):
    return {
        "schemaVersion": "1.0", "messageId": str(uuid4()),
        "correlationId": str(uuid4()), "timestamp": "2026-10-14T10:00:00Z",
        "source": "alice", "target": "sentinel", "type": action,
        "idempotencyKey": "sensitive", "deadline": deadline, "payload": {"pr": 2},
    }


class AdversarialSecurityTests(unittest.TestCase):
    def test_retry_safe_flag_cannot_make_mutation_retriable(self):
        attempts = []
        def fail(req, timeout):
            attempts.append(req)
            raise URLError("transient")
        transport = SecureTransport("https://partner.example/v1", lambda: "token", opener=fail,
                                    max_retries=3, sleeper=lambda _: None)
        with self.assertRaises(TransportError):
            transport.send({"action": "merge.execute"}, retry_safe=True)
        self.assertEqual(len(attempts), 1)

    def test_read_flag_cannot_override_mutation_action(self):
        attempts = []
        def fail(req, timeout):
            attempts.append(req)
            raise URLError("transient")
        transport = SecureTransport("https://partner.example/v1", lambda: "token", opener=fail,
                                    max_retries=3, sleeper=lambda _: None)
        with self.assertRaises(TransportError):
            transport.send({"read": True, "action": "payment.submit"}, retry_safe=True)
        self.assertEqual(len(attempts), 1)

    def test_client_http_400_never_retried(self):
        attempts = []
        def invalid(req, timeout):
            attempts.append(req)
            raise HTTPError(req.full_url, 400, "bad request", {}, io.BytesIO())
        transport = SecureTransport("https://partner.example/v1", lambda: "token", opener=invalid,
                                    max_retries=3, sleeper=lambda _: None)
        with self.assertRaises(TransportError):
            transport.send({"read": True}, retry_safe=True)
        self.assertEqual(len(attempts), 1)

    def test_expired_sensitive_command_does_not_consume_approval_or_claim_sqlite(self):
        reg = CapabilityRegistry()
        reg.register("sentinel", "1.0", {"merge.execute"})
        msg = command()
        approvals = ApprovalStore()
        fingerprint = ProviderBridge.fingerprint(msg)
        approvals.grant("alice", "sentinel", "merge.execute", fingerprint, expires_at=2000000000)
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/store.db") as store:
            bridge = ProviderBridge(reg, {"sentinel": lambda _: self.fail("handler invoked")},
                                    durable_store=store, approval_store=approvals)
            with self.assertRaises(ContractError):
                bridge.dispatch(msg, NOW)
            self.assertIsNone(store.status("alice", "sensitive"))
        approvals.require("alice", "sentinel", "merge.execute", fingerprint, now=NOW.timestamp())

    def test_completed_durable_record_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp, DurableStore(tmp + "/store.db") as store:
            store.complete("alice", "same", "h", {"ok": True})
            with self.assertRaises(IdempotencyConflict):
                store.complete("alice", "same", "h", {"ok": False})
            self.assertEqual(store.get("alice", "same", "h"), {"ok": True})

    def test_replay_after_restart_requires_manual_reconciliation(self):
        reg = CapabilityRegistry()
        reg.register("sentinel", "1.0", {"review.requested"})
        msg = command("review.requested", "2026-10-14T13:00:00Z")
        calls = []
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with DurableStore(path) as store:
                store.begin("alice", "sensitive", ProviderBridge.fingerprint(msg))
            with DurableStore(path) as store:
                bridge = ProviderBridge(reg, {"sentinel": lambda env: calls.append(env)}, durable_store=store)
                with self.assertRaises(RuntimeError):
                    bridge.dispatch(msg, NOW)
        self.assertEqual(calls, [])
