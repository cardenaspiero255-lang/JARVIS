"""Read only Ultra Sentinel GitHub Actions monitor, no remote write methods."""
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from jarvis_contracts.sentinel_health import SentinelHealthObserver, SentinelHealthError

BASE = "https://api.github.com/repos/cardenaspiero255-lang/Ultra-Sentinel"
ALLOWED = frozenset({"", "/actions/workflows/serverless-github-monitor.yml/runs?branch=main&per_page=30"})


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def authenticated_get(token, path):
    if path not in ALLOWED or not isinstance(token, str) or not 8 <= len(token) <= 2048:
        raise SentinelHealthError("invalid endpoint or token")
    url = BASE + path
    request = urllib.request.Request(
        url,
        data=None,
        method="GET",
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "JARVIS-Sentinel-ReadOnly-Observer",
        }
    )
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(request, timeout=15) as response:
            if response.status != 200 or response.geturl() != url:
                raise SentinelHealthError("untrusted response origin/status")
            content_type = response.headers.get("Content-Type", "")
            if not ("application/json" in content_type
                    or "application/vnd.github+json" in content_type):
                raise SentinelHealthError("untrusted content type")
            payload = response.read(1024 * 1024 + 1)
            if len(payload) > 1024 * 1024:
                raise SentinelHealthError("response exceeds safe size")
            decoded = json.loads(payload)
            if type(decoded) is not dict:
                raise SentinelHealthError("expected GitHub JSON object")
            return decoded
    except (urllib.error.URLError, TimeoutError, ValueError) as exc:
        raise SentinelHealthError("GitHub read-only API unavailable") from exc


def main():
    token = os.environ.get("GITHUB_TOKEN", "")
    try:
        metadata = authenticated_get(token, "")
        runs = authenticated_get(token, "/actions/runs?branch=main&per_page=30")
        snapshot = SentinelHealthObserver().inspect(
            metadata, runs, now=datetime.now(timezone.utc)
        )
        state = snapshot.state.value
        report = {
            "schema": "jarvis-sentinel-health/v1",
            "source": "github-api-readonly",
            "state": state,
            "runId": snapshot.run_id,
            "headSha": snapshot.head_sha,
            "continuousServiceVerified": False,
            "remoteWrites": False,
        }
    except Exception:
        # Never print GitHub tokens, response bodies, or private error strings.
        state = "UNVERIFIED"
        report = {"schema": "jarvis-sentinel-health/v1",
                  "source": "github-api-readonly", "state": state,
                  "runId": None, "headSha": None,
                  "continuousServiceVerified": False, "remoteWrites": False}
    public = json.dumps(report, sort_keys=True)
    print(public)
    report_path = os.environ.get("JARVIS_SENTINEL_PUBLIC_REPORT", "")
    if report_path:
        Path(report_path).write_text(public + "\n", encoding="utf-8")
    return 0 if state == "RECENT_READONLY_SUCCESS" else 2


if __name__ == "__main__":
    sys.exit(main())
