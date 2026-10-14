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


_SOURCE = re.compile(r"[A-Za-z0-9_-]{1,64}")
_KEY = re.compile(r"[A-Za-z0-9_-]{1,128}")
_FINGERPRINT = re.compile(r"[0-9a-f]{64}")


class RecoveryInspector:
    """Safe restart state derived from the already implemented DurableStore."""

    def __init__(self, durable_store):
        if (not callable(getattr(durable_store, "get", None))
                or not callable(getattr(durable_store, "status", None))):
            raise TypeError("compatible durable store required")
        self._store = durable_store

    def inspect(self, source, key, fingerprint, *, now, deadline):
        if (type(source) is not str or _SOURCE.fullmatch(source) is None
                or type(key) is not str or _KEY.fullmatch(key) is None
                or type(fingerprint) is not str or
                _FINGERPRINT.fullmatch(fingerprint) is None
                or type(now) not in (int, float) or not math.isfinite(now)
                or type(deadline) not in (int, float) or
                not math.isfinite(deadline)
                or now <= 0 or deadline <= 0 or deadline < now):
            # Passing a request with an expired deadline is not an invalid
            # operation input; it must return EXPIRED without side effects.
            if (type(now) in (int, float) and type(deadline) in (int, float)
                    and math.isfinite(now) and math.isfinite(deadline)
                    and 0 < deadline <= now
                    and type(source) is str and _SOURCE.fullmatch(source)
                    and type(key) is str and _KEY.fullmatch(key)
                    and type(fingerprint) is str and _FINGERPRINT.fullmatch(fingerprint)):
                # Still check fingerprint collision even on an expired command.
                self._store.get(source, key, fingerprint)
                return ResumeAdvice(ResumeDisposition.EXPIRED)
            raise InvalidRecoveryInput("invalid principal, key, fingerprint or clock")

        previous = self._store.get(source, key, fingerprint)
        status = self._store.status(source, key)
        if status is None:
            return ResumeAdvice(ResumeDisposition.UNSEEN)
        if status == "uncertain":
            return ResumeAdvice(ResumeDisposition.RECONCILE)
        if status == "completed":
            return ResumeAdvice(ResumeDisposition.COMPLETED, previous)
        raise InvalidRecoveryInput("unknown persisted command state")
