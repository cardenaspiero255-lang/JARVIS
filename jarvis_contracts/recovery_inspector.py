"""Omega Recovery: conservative inspection of existing durable journal state.

This module is read-only. It never executes external operations, renews
approvals, or retries a command whose outcome is uncertain.
"""
from dataclasses import dataclass
from enum import Enum
import math
import re


class InvalidRecoveryInput(ValueError):
    pass


class ResumeDisposition(str, Enum):
    UNSEEN = "unseen"
    RECONCILE = "manual-reconciliation-required"
    COMPLETED = "completed"
    EXPIRED = "expired"


@dataclass(frozen=True)
class ResumeAdvice:
    disposition: ResumeDisposition
    completed_result: object = None

    @property
    def can_execute(self) -> bool:
        # The record inspection never grants permissions or authority.
        return False


_FINGERPRINT = re.compile(r"[0-9a-f]{64}")


class RecoveryInspector:
    """Safe restart state derived from the already implemented DurableStore."""

    def __init__(self, durable_store):
        if not callable(getattr(durable_store, "snapshot", None)):
            raise TypeError("compatible durable store required")
        self._store = durable_store

    def inspect(self, source, key, fingerprint, *, now, deadline):
        if (type(source) is not str or not source.strip()
                or type(key) is not str or not key.strip()
                or type(fingerprint) is not str or _FINGERPRINT.fullmatch(fingerprint) is None
                or type(now) not in (int, float) or not math.isfinite(now)
                or type(deadline) not in (int, float) or not math.isfinite(deadline)
                or now <= 0 or deadline <= 0):
            raise InvalidRecoveryInput("invalid principal, key, fingerprint or clock")

        # Detect a reused idempotency key even if this request is expired.
        snapshot = self._store.snapshot(source, key, fingerprint)
        if now >= deadline:
            return ResumeAdvice(ResumeDisposition.EXPIRED)

        if snapshot is None:
            return ResumeAdvice(ResumeDisposition.UNSEEN)
        status, previous = snapshot
        if status == "uncertain":
            return ResumeAdvice(ResumeDisposition.RECONCILE)
        if status == "completed":
            return ResumeAdvice(ResumeDisposition.COMPLETED, previous)
        raise InvalidRecoveryInput("unknown persisted command state")
