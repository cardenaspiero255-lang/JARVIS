"""Authenticated HTTPS JSON transport boundary.

Credentials are provided at call time; no secrets are persisted or logged.
The standard library opener validates TLS certificates and hostnames by default.
"""
import json
import time
import urllib.request
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit


class TransportError(RuntimeError):
    pass


class AuthenticationError(TransportError):
    pass


class CircuitOpen(TransportError):
    pass


class SecureTransport:
    def __init__(self, endpoint, token_provider, *, timeout=10.0, opener=None, max_retries=0, failure_threshold=3, reset_seconds=30, sleeper=None, clock=None):
        parsed = urlsplit(endpoint)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
            raise ValueError("HTTPS endpoint with valid host required")
        if not 0 < timeout <= 60:
            raise ValueError("invalid timeout")
        if not isinstance(max_retries, int) or not 0 <= max_retries <= 5:
            raise ValueError("invalid retry limit")
        if not isinstance(failure_threshold, int) or failure_threshold < 1 or reset_seconds <= 0:
            raise ValueError("invalid circuit settings")
        self.max_retries = max_retries
        self.failure_threshold = failure_threshold
        self.reset_seconds = reset_seconds
        self.sleeper = sleeper if sleeper is not None else time.sleep
        self.clock = clock if clock is not None else time.monotonic
        self.failures = 0
        self.opened_at = None
        self.endpoint = endpoint
        self.token_provider = token_provider
        self.timeout = timeout
        self.opener = opener if opener is not None else urllib.request.urlopen

    def send(self, payload, *, retry_safe=False):
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
        if self.opened_at is not None:
            if self.clock() - self.opened_at < self.reset_seconds:
                raise CircuitOpen("circuit breaker open")
            self.opened_at = None
            self.failures = 0
        attempts = self.max_retries + 1 if retry_safe else 1
        for attempt in range(attempts):
            try:
                with self.opener(request, timeout=self.timeout) as response:
                    if response.status != 200:
                        raise TransportError("unexpected HTTP status")
                    data = response.read(1024 * 1024 + 1)
                self.failures = 0
                break
            except HTTPError as exc:
                if exc.code in (401, 403):
                    raise AuthenticationError("authentication rejected") from exc
                error = TransportError("HTTP request failed")
            except (URLError, TimeoutError, OSError) as exc:
                error = TransportError("transport unavailable")
            except TransportError as exc:
                error = exc
            self.failures += 1
            if self.failures >= self.failure_threshold:
                self.opened_at = self.clock()
                raise error
            if attempt == attempts - 1:
                raise error
            self.sleeper(min(0.1 * (2 ** attempt), 2.0))
        if len(data) > 1024 * 1024:
            raise TransportError("response too large")
        try:
            result = json.loads(data)
        except (ValueError, UnicodeError) as exc:
            raise TransportError("invalid JSON response") from exc
        if not isinstance(result, dict):
            raise TransportError("response must be a JSON object")
        return result
