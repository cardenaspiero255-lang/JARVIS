"""Explicit, scoped, expiring, single-use approvals."""
import threading
import math


class ApprovalDenied(PermissionError):
    pass


def _finite_number(value):
    return type(value) in (int, float) and math.isfinite(value)


class ApprovalStore:
    def __init__(self):
        self._approvals = {}
        self._lock = threading.Lock()

    def grant(self, principal, target, action, fingerprint, *, expires_at):
        if not all(isinstance(v, str) and v for v in (principal, target, action, fingerprint)):
            raise ValueError("approval scope required")
        if not _finite_number(expires_at):
            raise ValueError("finite approval expiry required")
        with self._lock:
            self._approvals[(principal, target, action, fingerprint)] = expires_at

    def require(self, principal, target, action, fingerprint, now):
        if not _finite_number(now):
            raise ApprovalDenied("trusted clock required")
        with self._lock:
            expiry = self._approvals.get((principal, target, action, fingerprint))
            if expiry is None or now >= expiry:
                raise ApprovalDenied("specific, unexpired approval required")

    def consume(self, principal, target, action, fingerprint, now):
        if not _finite_number(now):
            raise ApprovalDenied("trusted clock required")
        scope = (principal, target, action, fingerprint)
        with self._lock:
            expiry = self._approvals.get(scope)
            if expiry is None or now >= expiry:
                raise ApprovalDenied("specific, unexpired unused approval required")
            del self._approvals[scope]
