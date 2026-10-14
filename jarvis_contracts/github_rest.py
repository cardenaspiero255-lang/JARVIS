"""Authenticated, read-only GitHub REST connector.

API paths are strict allowlisted templates. The token is obtained at request time;
no writes or credential-bearing cross-host redirects are permitted. A token
provider must implement installation-token refresh out of band.
"""
import json
import re
import urllib.request
from urllib.error import HTTPError, URLError


class GitHubAPIError(RuntimeError):
    pass


class GitHubAuthenticationError(GitHubAPIError):
    pass


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GitHubRESTClient:
    _REPO = r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"
    _PR = re.compile(rf"^/repos/({_REPO})/pulls/([1-9][0-9]*)$")
    _CHECKS = re.compile(rf"^/repos/({_REPO})/commits/([a-f0-9]{{40}})/check-runs\?per_page=100$")
    _REVIEWS = re.compile(rf"^/repos/({_REPO})/pulls/([1-9][0-9]*)/reviews\?per_page=100$")

    def __init__(self, token_provider, *, timeout=10.0, opener=None):
        if not callable(token_provider) or not 0 < timeout <= 60:
            raise ValueError("token provider and bounded timeout required")
        self._token_provider = token_provider
        self._timeout = timeout
        self._opener = opener if opener is not None else urllib.request.build_opener(_NoRedirect()).open

    def get(self, path):
        if not isinstance(path, str) or len(path) > 300:
            raise ValueError("invalid GitHub API path")
        match = next((m for p in (self._PR, self._CHECKS, self._REVIEWS)
                      if (m := p.fullmatch(path)) is not None), None)
        if match is None or any(s in (".", "..") for s in match.group(1).split("/")):
            raise ValueError("GitHub endpoint not allowlisted")
        token = self._token_provider()
        if not isinstance(token, str) or not token.strip() or any(c in token for c in "\r\n"):
            raise GitHubAuthenticationError("valid installation credentials required")
        req = urllib.request.Request(
            "https://api.github.com" + path,
            method="GET",
            headers={
                "Authorization": "Bearer " + token,
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "JARVIS-contract-adapter",
            },
        )
        try:
            with self._opener(req, timeout=self._timeout) as response:
                if response.status != 200:
                    raise GitHubAPIError("unexpected GitHub response")
                raw = response.read(1024 * 1024 + 1)
        except HTTPError as exc:
            if exc.code in (401, 403):
                raise GitHubAuthenticationError("GitHub authentication or permission denied") from exc
            raise GitHubAPIError("GitHub request rejected") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise GitHubAPIError("GitHub unavailable") from exc
        if len(raw) > 1024 * 1024:
            raise GitHubAPIError("GitHub response exceeds size limit")
        try:
            decoded = json.loads(raw)
        except (ValueError, UnicodeError) as exc:
            raise GitHubAPIError("invalid GitHub response") from exc
        if not isinstance(decoded, dict):
            raise GitHubAPIError("GitHub response is not an object")
        return decoded
