import unittest
from datetime import datetime, timezone
from uuid import uuid4
from jarvis_contracts import ContractError, Envelope, InMemoryGateway, PermissionDenied, UnsupportedVersion


def sample(**changes):
    message = dict(schemaVersion="1.0", messageId=str(uuid4()), correlationId=str(uuid4()),
                   timestamp="2026-10-14T10:00:00Z", source="jarvis", target="sentinel",
                   type="review.requested", idempotencyKey="pr-202-sha-abc",
                   deadline="2026-10-14T11:00:00Z", payload={"pr": 202})
    message.update(changes)
    return message


class ContractTests(unittest.TestCase):
    def test_valid_contract(self):
        self.assertEqual(Envelope.parse(sample()).schema_version, "1.0")

    def test_rejects_incompatible_major(self):
        with self.assertRaises(UnsupportedVersion):
            Envelope.parse(sample(schemaVersion="2.0"))

    def test_rejects_missing_or_unknown_fields(self):
        for message in (sample(unexpected=True), {k:v for k,v in sample().items() if k != "target"}):
            with self.assertRaises(ContractError):
                Envelope.parse(message)

    def test_rejects_expired_deadline_at_creation(self):
        with self.assertRaises(ContractError):
            Envelope.parse(sample(deadline="2026-10-14T09:00:00Z"))

    def test_rejects_naive_timestamp(self):
        with self.assertRaises(ContractError):
            Envelope.parse(sample(timestamp="2026-10-14T10:00:00"))

    def test_authorization_is_fail_closed(self):
        gateway = InMemoryGateway({}, {"sentinel": lambda _: "ok"})
        with self.assertRaises(PermissionDenied):
            gateway.dispatch(sample(), datetime(2026,10,14,10,30,tzinfo=timezone.utc))

    def test_idempotency_reuses_completed_result(self):
        calls = []
        def handler(message):
            calls.append(message)
            return {"status":"accepted"}
        gateway = InMemoryGateway({"sentinel":{"review.requested"}}, {"sentinel":handler})
        now = datetime(2026,10,14,10,30,tzinfo=timezone.utc)
        first = gateway.dispatch(sample(), now)
        second = gateway.dispatch(sample(), now)
        self.assertEqual(first, second)
        self.assertEqual(len(calls), 1)

    def test_expired_command_never_runs(self):
        gateway = InMemoryGateway({"sentinel":{"review.requested"}}, {"sentinel":lambda _: "ok"})
        with self.assertRaises(ContractError):
            gateway.dispatch(sample(), datetime(2026,10,14,11,0,tzinfo=timezone.utc))

    def test_unavailable_provider_fails(self):
        gateway = InMemoryGateway({"sentinel":{"review.requested"}}, {})
        with self.assertRaises(ContractError):
            gateway.dispatch(sample(), datetime(2026,10,14,10,30,tzinfo=timezone.utc))


if __name__ == "__main__":
    unittest.main()
