"""Strict evidence interpretation for the actual Ultra Sentinel GitHub workflow.

This reports *observations*, not a deployed API, trusted code verdict,
continuous 24/7 monitoring, autonomous patching, or merge authorization.
"""
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
import re


class SentinelHealthError(ValueError):
    pass


class HealthState(str, Enum):
    RECENT_READONLY_SUCCESS = "RECENT_READONLY_SUCCESS"
    RECENT_FAILURE = "RECENT_FAILURE"
    NO_VERIFIED_RUN = "NO_VERIFIED_RUN"
    STALE = "STALE"
    SKIPPED = "SKIPPED"


@dataclass(frozen=True)
class SentinelHealth:
    state: HealthState
    run_id: int | None = None
    head_sha: str | None = None

    @property
    def continuous_service_verified(self) -> bool:
        return False

    @property
    def can_write(self) -> bool:
        return False


class SentinelHealthObserver:
    REPO = "cardenaspiero255-lang/Ultra-Sentinel"
    REPO_ID = 1418338512
    OWNER_ID = 322222186
    WORKFLOW_PATH = (
        "cardenaspiero255-lang/Ultra-Sentinel/"
        ".github/workflows/serverless-github-monitor.yml@refs/heads/main"
    )
    _SHA = re.compile(r"^[a-f0-9]{40}$")
    _EVENTS = frozenset({"schedule", "workflow_dispatch", "issues", "issue_comment"})

    def inspect(self, metadata, run_inventory, *, now: datetime) -> SentinelHealth:
        if (type(metadata) is not dict or metadata.get("id") != self.REPO_ID
                or metadata.get("full_name") != self.REPO
                or type(metadata.get("owner")) is not dict
                or metadata["owner"].get("id") != self.OWNER_ID
                or metadata.get("default_branch") != "main"
                or metadata.get("archived") is True):
            raise SentinelHealthError("Ultra Sentinel GitHub identity mismatch")
        if (not isinstance(now, datetime) or now.tzinfo is None
                or now.utcoffset() is None):
            raise SentinelHealthError("trusted aware clock required")
        if (type(run_inventory) is not dict
                or type(run_inventory.get("workflow_runs")) is not list
                or len(run_inventory["workflow_runs"]) > 30):
            raise SentinelHealthError("incomplete or invalid workflow inventory")
        selected = []
        for item in run_inventory["workflow_runs"]:
            if type(item) is not dict:
                raise SentinelHealthError("invalid workflow metadata")
            if (item.get("name") != "Ultra Sentinel — serverless GitHub observer"
                    or item.get("path") != self.WORKFLOW_PATH
                    or item.get("event") not in self._EVENTS):
                continue
            run_id = item.get("id")
            sha = item.get("head_sha")
            if (type(run_id) is not int or run_id <= 0
                    or type(sha) is not str or not self._SHA.fullmatch(sha)
                    or item.get("head_branch") != "main"
                    or item.get("status") != "completed"
                    or item.get("conclusion") not in ("success", "failure", "skipped")):
                raise SentinelHealthError("malformed relevant workflow run")
            raw = item.get("created_at")
            if type(raw) is not str:
                raise SentinelHealthError("missing run timestamp")
            try:
                stamp = datetime.fromisoformat(raw.replace("Z", "+00:00"))
                if stamp.tzinfo is None or stamp.utcoffset() is None:
                    raise ValueError("naive timestamp")
                stamp = stamp.astimezone(timezone.utc)
            except (ValueError, OverflowError) as exc:
                raise SentinelHealthError("invalid workflow timestamp") from exc
            age = (now.astimezone(timezone.utc) - stamp).total_seconds()
            if age < -30:
                raise SentinelHealthError("future workflow timestamp")
            selected.append((stamp, run_id, sha, age, item["conclusion"]))
        if not selected:
            return SentinelHealth(HealthState.NO_VERIFIED_RUN)
        selected.sort(key=lambda row: (row[0], row[1]), reverse=True)
        _, run_id, sha, age, conclusion = selected[0]
        if age > 7200:
            return SentinelHealth(HealthState.STALE, run_id, sha)
        if conclusion == "failure":
            return SentinelHealth(HealthState.RECENT_FAILURE, run_id, sha)
        if conclusion == "skipped":
            return SentinelHealth(HealthState.SKIPPED, run_id, sha)
        return SentinelHealth(HealthState.RECENT_READONLY_SUCCESS, run_id, sha)
