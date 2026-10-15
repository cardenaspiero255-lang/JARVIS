"""Conservative, read-only summary of observed GitHub check-run evidence.

This report is descriptive, not a required-check policy or merge gate. It is
never an authority to call GitHub mutation methods.
"""
from dataclasses import dataclass
from enum import Enum
import re


class CheckState(str, Enum):
    OBSERVED_SUCCESS = "observed-success"
    PENDING = "pending"
    FAILING = "failing"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class CheckEvidence:
    state: CheckState
    count: int
    passing: tuple[str, ...]
    pending: tuple[str, ...]
    failing: tuple[str, ...]
    unverified: tuple[str, ...]

    @property
    def authorized_to_merge(self) -> bool:
        # Branch protection, reviewer authority, and required checks are not
        # established by this read-only snapshot.
        return False


_SHA = re.compile(r"[0-9a-f]{40}")
_WAITING = frozenset({"queued", "in_progress", "waiting", "requested", "pending"})
_FAILURES = frozenset({"failure", "timed_out", "cancelled", "action_required",
                       "startup_failure"})


def summarize_checks(checks, head_sha, *, complete=True) -> CheckEvidence:
    """Classify only immutable SHA-bound observations; never infer permission.

    A successful conclusion in a noncompleted job remains PENDING. An absent,
    skipped, neutral, malformed or partially collected inventory remains UNKNOWN.
    Every recorded failure is surfaced even when other checks are still running.
    """
    if (type(checks) not in (list, tuple) or type(head_sha) is not str
            or _SHA.fullmatch(head_sha) is None):
        return CheckEvidence(CheckState.UNKNOWN, 0, (), (), (), ("invalid inventory",))
    passing, pending, failing, unverified = [], [], [], []
    for item in checks[:1000]:
        if type(item) is not dict:
            unverified.append("invalid check")
            continue
        name = item.get("name")
        if type(name) is not str or not name.strip() or len(name) > 120:
            unverified.append("invalid check")
            continue
        if item.get("head_sha") != head_sha:
            unverified.append(name)
            continue
        status, conclusion = item.get("status"), item.get("conclusion")
        if status in _WAITING:
            pending.append(name)
        elif status == "completed":
            if conclusion == "success":
                passing.append(name)
            elif conclusion in _FAILURES:
                failing.append(name)
            else:
                unverified.append(name)
        else:
            unverified.append(name)
    if len(checks) > 1000:
        unverified.append("inventory exceeds budget")
    if failing:
        state = CheckState.FAILING
    elif complete is not True or not checks or unverified:
        state = CheckState.UNKNOWN
    elif pending:
        state = CheckState.PENDING
    else:
        state = CheckState.OBSERVED_SUCCESS
    return CheckEvidence(state, len(checks), tuple(passing), tuple(pending),
                         tuple(failing), tuple(unverified))
