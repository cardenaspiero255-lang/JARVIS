"""Explicit, scoped, expiring approvals; no implicit action-wide grants."""


class ApprovalDenied(PermissionError):
    pass


class ApprovalStore:
    def __init__(self):
        self._approvals = {}

    def grant(self, principal, target, action, fingerprint, *, expires_at):
        if not all(isinstance(v, str) and v for v in (principal, target, action, fingerprint)):
            raise ValueError("approval scope required")
        self._approvals[(principal, target, action, fingerprint)] = expires_at

    def require(self, principal, target, action, fingerprint, now):
        expiry = self._approvals.get((principal, target, action, fingerprint))
        if expiry is None or now >= expiry:
            raise ApprovalDenied("specific, unexpired approval required")
