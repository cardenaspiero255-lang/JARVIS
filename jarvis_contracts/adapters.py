"""Explicit provider boundaries. No live credentials or invented endpoints.

The caller supplies an authenticated GitHub REST client; this module does not
create an installation token or imply deployment of external integrations.
"""
import re


class ProviderNotConfigured(RuntimeError):
    pass


class GitHubReadOnlyAdapter:
    """Read-only GitHub PR adapter using an injected authenticated GET function."""

    _REPO = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    def __init__(self, authenticated_get):
        if not callable(authenticated_get):
            raise TypeError("authenticated GET client required")
        self._get = authenticated_get

    def get_pull_request(self, repository, number):
        if not isinstance(repository, str) or not self._REPO.fullmatch(repository):
            raise ValueError("invalid repository")
        if any(part in (".", "..") for part in repository.split("/")):
            raise ValueError("invalid repository")
        if type(number) is not int or number < 1:
            raise ValueError("invalid pull request number")
        return self._get(f"/repos/{repository}/pulls/{number}")

    def get_head_snapshot(self, repository, number):
        pr = self.get_pull_request(repository, number)
        if not isinstance(pr, dict) or not isinstance(pr.get("head"), dict):
            raise ValueError("malformed PR")
        sha = pr["head"].get("sha")
        if not isinstance(sha, str) or not re.fullmatch(r"[a-f0-9]{40}", sha):
            raise ValueError("invalid head SHA")
        response = self._get(f"/repos/{repository}/commits/{sha}/check-runs?per_page=100")
        if not isinstance(response, dict) or not isinstance(response.get("check_runs"), list):
            raise ValueError("invalid checks")
        checks = response["check_runs"]
        if any(not isinstance(check, dict) or check.get("head_sha") != sha for check in checks):
            raise ValueError("stale check SHA")
        return {"headSha": sha, "checks": checks,
                "checkInventoryComplete": False, "authorizedToMerge": False}


class UnsupportedProvider:
    """Fail-closed boundary until a verified external protocol is supplied."""

    def __init__(self, provider):
        self.provider = provider

    def execute(self, request):
        raise ProviderNotConfigured(f"{self.provider} integration is not configured")
