"""Explicit, scoped, expiring, single-use approvals."""
import threading


class ApprovalDenied(PermissionError):
    pass


class ApprovalStore:
    def __init__(self):
        self._approvals = {}
        self._lock = threading.Lock()

    def grant(self, principal, target, action, fingerprint, *, expires_at):
        if not all(isinstance(v, str) and v for v in (principal, target, action, fingerprint)):
            raise ValueError("approval scope required")
        self._approvals[(principal, target, action, fingerprint)] = expires_at

    def require(self, principal, target, action, fingerprint, now):
        expiry = self._approvals.get((principal, target, action, fingerprint))
        if expiry is None or now >= expiry:
            raise ApprovalDenied("specific, unexpired approval required")

    def consume(self, principal, target, action, fingerprint, now):
        scope = (principal, target, action, fingerprint)
        with self._lock:
            expiry = self._approvals.get(scope)
            if expiry is None or now >= expiry:
                raise ApprovalDenied("specific, unexpired unused approval required")
            del self._approvals[scope]
