"""Authenticated HTTPS JSON transport boundary.

Credentials are provided at call time; no secrets are persisted or logged.
The standard library opener validates TLS certificates and hostnames by default.
"""
import json
import urllib.request
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit


class TransportError(RuntimeError):
    pass


class AuthenticationError(TransportError):
    pass


class SecureTransport:
    def __init__(self, endpoint, token_provider, *, timeout=10.0, opener=None):
        parsed = urlsplit(endpoint)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
            raise ValueError("HTTPS endpoint with valid host required")
        if not 0 < timeout <= 60:
            raise ValueError("invalid timeout")
        self.endpoint = endpoint
        self.token_provider = token_provider
        self.timeout = timeout
        self.opener = opener if opener is not None else urllib.request.urlopen

    def send(self, payload):
        token = self.token_provider()
        if not isinstance(token, str) or not token.strip() or "\r" in token or "\n" in token:
            raise AuthenticationError("valid credentials required")
        try:
            body = json.dumps(payload, allow_nan=False).encode("utf-8")
        except (TypeError, ValueError) as exc:
            raise TransportError("invalid request payload") from exc
        request = urllib.request.Request(
            self.endpoint, data=body, method="POST",
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with self.opener(request, timeout=self.timeout) as response:
                if response.status != 200:
                    raise TransportError("unexpected HTTP status")
                data = response.read(1024 * 1024 + 1)
        except HTTPError as exc:
            if exc.code in (401, 403):
                raise AuthenticationError("authentication rejected") from exc
            raise TransportError("HTTP request failed") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise TransportError("transport unavailable") from exc
        if len(data) > 1024 * 1024:
            raise TransportError("response too large")
        try:
            result = json.loads(data)
        except (ValueError, UnicodeError) as exc:
            raise TransportError("invalid JSON response") from exc
        if not isinstance(result, dict):
            raise TransportError("response must be a JSON object")
        return result
