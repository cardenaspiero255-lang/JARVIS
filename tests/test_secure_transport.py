import unittest
from unittest.mock import Mock
from jarvis_contracts.transport import SecureTransport, TransportError, AuthenticationError


class TransportTests(unittest.TestCase):
    def test_requires_https(self):
        with self.assertRaises(ValueError):
            SecureTransport("http://example.test/api", lambda: "token")

    def test_rejects_empty_credentials_before_network(self):
        network = Mock()
        transport = SecureTransport("https://example.test/api", lambda: "", opener=network)
        with self.assertRaises(AuthenticationError):
            transport.send({"type": "review.requested"})
        network.assert_not_called()

    def test_uses_authorization_and_timeout(self):
        captured = {}
        def opener(request, timeout):
            captured["request"] = request
            captured["timeout"] = timeout
            return Mock(__enter__=lambda self: self, __exit__=lambda *args: None,
                        read=lambda size: b'{"status":"accepted"}', status=200)
        transport = SecureTransport("https://example.test/api", lambda: "secret", opener=opener)
        self.assertEqual(transport.send({"type": "review.requested"}), {"status": "accepted"})
        self.assertEqual(captured["request"].get_header("Authorization"), "Bearer secret")
        self.assertGreater(captured["timeout"], 0)

    def test_rejects_non_json_response(self):
        response = Mock(__enter__=lambda self: self, __exit__=lambda *args: None,
                        read=lambda size: b"not json", status=200)
        transport = SecureTransport("https://example.test/api", lambda: "secret", opener=lambda *_args, **_kw: response)
        with self.assertRaises(TransportError):
            transport.send({"type": "review.requested"})


if __name__ == "__main__":
    unittest.main()
