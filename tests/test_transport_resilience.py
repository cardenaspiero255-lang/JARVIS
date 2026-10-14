import unittest
from urllib.error import URLError
from jarvis_contracts.transport import SecureTransport, TransportError


class Response:
    status = 200
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, size): return b'{"ok":true}'


class ResilienceTests(unittest.TestCase):
    def test_retry_only_when_explicitly_safe(self):
        attempts = []
        def opener(req, timeout):
            attempts.append(req)
            if len(attempts) < 3:
                raise URLError("offline")
            return Response()
        transport = SecureTransport("https://example.org/api", lambda: "token", opener=opener,
                                    max_retries=2, sleeper=lambda _: None)
        self.assertEqual(transport.send({"read": True}, retry_safe=True), {"ok": True})
        self.assertEqual(len(attempts), 3)

    def test_mutations_are_not_retried(self):
        attempts = []
        def opener(req, timeout):
            attempts.append(req)
            raise URLError("offline")
        transport = SecureTransport("https://example.org/api", lambda: "token", opener=opener,
                                    max_retries=3, sleeper=lambda _: None)
        with self.assertRaises(TransportError):
            transport.send({"action": "merge.execute"})
        self.assertEqual(len(attempts), 1)

    def test_circuit_opens_after_repeated_failures(self):
        attempts = []
        def opener(req, timeout):
            attempts.append(req)
            raise URLError("offline")
        transport = SecureTransport("https://example.org/api", lambda: "token", opener=opener,
                                    max_retries=0, failure_threshold=2, reset_seconds=30,
                                    clock=lambda: 100, sleeper=lambda _: None)
        for _ in range(3):
            with self.assertRaises(TransportError):
                transport.send({"read": True})
        self.assertEqual(len(attempts), 2)


if __name__ == "__main__":
    unittest.main()
