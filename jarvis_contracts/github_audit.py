"""Read-only, SHA-bound GitHub PR audit.

Evidence is collected from an authenticated GitHub REST client with strict
allowlisted endpoints; it is not a policy decision or merge authorization.
"""
from dataclasses import dataclass
import re

from .adapters import GitHubReadOnlyAdapter
from .check_evidence import summarize_checks


class IncompleteSnapshot(ValueError):
    """The remote API did not supply a complete bounded inventory."""


class StaleSnapshot(IncompleteSnapshot):
    """Some evidence does not match the immutable PR HEAD."""


_SHA = re.compile(r"[0-9a-f]{40}")


@dataclass(frozen=True)
class ReadOnlySnapshot:
    repository: str
    pr_number: int
    head_sha: str
    checks: tuple
    reviews: tuple
    check_inventory_complete: bool = True

    @property
    def check_evidence(self):
        # Snapshot never decides required checks or authorizes a merge.
        return summarize_checks(self.checks, self.head_sha,
                                complete=self.check_inventory_complete)

    @property
    def authorized_to_merge(self):
        # GitHub branch policies, actor authorization and independent signals
        # are NOT established by this read-only inventory.
        return False


class GitHubReadOnlyAudit:
    """Bounded read-only check/review inventory without inferred authority."""

    def __init__(self, authenticated_get, *, max_pages=10):
        if not callable(authenticated_get):
            raise TypeError("authenticated GET function required")
        if type(max_pages) is not int or not 1 <= max_pages <= 10:
            raise ValueError("max_pages must be between 1 and 10")
        self._get = authenticated_get
        self._adapter = GitHubReadOnlyAdapter(authenticated_get)
        self._max_pages = max_pages

    def _pull(self, repository, number):
        pr = self._adapter.get_pull_request(repository, number)
        if (type(pr) is not dict or pr.get("number") != number
                or type(pr.get("head")) is not dict):
            raise IncompleteSnapshot("incomplete PR identity")
        sha = pr["head"].get("sha")
        if type(sha) is not str or not _SHA.fullmatch(sha):
            raise IncompleteSnapshot("malformed PR head")
        return sha

    def _checks(self, repository, sha):
        total = None
        collected = []
        for page in range(1, self._max_pages + 1):
            path = f"/repos/{repository}/commits/{sha}/check-runs?per_page=100&page={page}"
            response = self._get(path)
            if type(response) is not dict or type(response.get("check_runs")) is not list:
                raise IncompleteSnapshot("malformed check run response")
            count = response.get("total_count")
            if type(count) is not int or not 0 <= count <= self._max_pages * 100:
                raise IncompleteSnapshot("check count exceeds audit budget")
            if total is None:
                total = count
            elif total != count:
                raise IncompleteSnapshot("check inventory changed while paging")
            expected = min(100, max(0, total - len(collected)))
            batch = response["check_runs"]
            if len(batch) != expected:
                raise IncompleteSnapshot("truncated or duplicated check page")
            for check in batch:
                if type(check) is not dict or check.get("head_sha") != sha:
                    raise StaleSnapshot("check run does not match immutable HEAD")
                if not isinstance(check.get("name"), str) or not check["name"].strip():
                    raise IncompleteSnapshot("missing check identity")
                if check.get("status") not in ("queued", "in_progress", "completed", "waiting", "requested", "pending"):
                    raise IncompleteSnapshot("unrecognized check status")
            collected.extend(batch)
            if len(collected) == total:
                return tuple(collected)
        raise IncompleteSnapshot("check pages exhausted")

    def _reviews(self, repository, number):
        reviews = []
        for page in range(1, self._max_pages + 1):
            path = f"/repos/{repository}/pulls/{number}/reviews?per_page=100&page={page}"
            response = self._get(path)
            if type(response) is not list or len(response) > 100:
                raise IncompleteSnapshot("malformed review inventory")
            if any(type(review) is not dict for review in response):
                raise IncompleteSnapshot("invalid review record")
            reviews.extend(response)
            if len(response) < 100:
                return tuple(reviews)
        raise IncompleteSnapshot("review pages exhausted; unsafe to infer review status")

    def snapshot(self, repository, pr_number):
        head = self._pull(repository, pr_number)
        checks = self._checks(repository, head)
        reviews = self._reviews(repository, pr_number)
        if self._pull(repository, pr_number) != head:
            raise StaleSnapshot("pull request HEAD moved during evidence collection")
        return ReadOnlySnapshot(repository, pr_number, head, checks, reviews)
