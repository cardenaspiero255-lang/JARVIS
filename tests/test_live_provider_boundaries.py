"""Phase 4: verified provider boundaries before production connectivity."""
import io
import json
import unittest
from urllib.error import HTTPError, URLError

from jarvis_contracts.adapters import GitHubReadOnlyAdapter
from jarvis_contracts.github_rest import GitHubRESTClient, GitHubAPIError, GitHubAuthenticationError
from jarvis_contracts.partner_contracts import (
    SentinelAdvisory, GameHubToolManifest, AndroidPermissionGate,
    InvalidPartnerContract, UnavailableIntegration,
)


class HTTPResponse:
    def __init__(self, obj, status=200):
        self.status = status
        self.body = json.dumps(obj).encode()
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self, count): return self.body[:count]


class LiveBoundaryTests(unittest.TestCase):
    def test_github_client_get_includes_runtime_token_never_stores_it(self):
        calls = []
        def opener(req, timeout):
            calls.append((req.full_url, req.get_method(), req.get_header("Authorization"), timeout))
            return HTTPResponse({"number": 2})
        client = GitHubRESTClient(lambda: "runtime-token", opener=opener)
        self.assertEqual(client.get("/repos/acme/widget/pulls/2"), {"number": 2})
        self.assertEqual(calls, [("https://api.github.com/repos/acme/widget/pulls/2",
                                  "GET", "Bearer runtime-token", 10.0)])
        self.assertNotIn("runtime-token", repr(client))

    def test_github_client_rejects_absolute_and_mutating_paths(self):
        client = GitHubRESTClient(lambda: "x", opener=lambda *_args, **_kw: self.fail("network used"))
        for bad in ("https://evil.invalid/a", "//evil.invalid/x", "/repos/a/b/../secret",
                    "/repos/a/b/pulls/1?token=secret", "/repos/a/b/pulls/1#fragment",
                    "/repos/a/b/pulls/1%0a", "/repos/a/b/pulls/1\\evil"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                client.get(bad)

    def test_github_client_auth_and_network_fail_closed(self):
        def unauth(req, timeout):
            raise HTTPError(req.full_url, 401, "denied", {}, io.BytesIO())
        with self.assertRaises(GitHubAuthenticationError):
            GitHubRESTClient(lambda: "x", opener=unauth).get("/repos/a/b/pulls/1")
        with self.assertRaises(GitHubAPIError):
            GitHubRESTClient(lambda: "x", opener=lambda req, timeout: (_ for _ in ()).throw(URLError("offline"))).get("/repos/a/b/pulls/1")

    def test_github_snapshot_sha_bound_and_check_inventory_not_assumed_complete(self):
        sha = "a" * 40
        def api(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": sha}}
            if path.endswith("/check-runs?per_page=100"):
                return {"total_count": 1, "check_runs": [{"name": "tests", "head_sha": sha, "status": "completed", "conclusion": "success"}]}
            raise AssertionError(path)
        snap = GitHubReadOnlyAdapter(api).get_head_snapshot("acme/widget", 2)
        self.assertEqual(snap["headSha"], sha)
        self.assertEqual(snap["checks"][0]["name"], "tests")
        self.assertFalse(snap["checkInventoryComplete"])
        self.assertFalse(snap["authorizedToMerge"])

    def test_github_snapshot_rejects_check_from_other_commit(self):
        sha = "a" * 40
        def api(path):
            return {"number": 2, "head": {"sha": sha}} if path.endswith("/pulls/2") else {
                "total_count": 1, "check_runs": [{"head_sha": "b" * 40, "status": "completed", "conclusion": "success"}]}
        with self.assertRaises(ValueError):
            GitHubReadOnlyAdapter(api).get_head_snapshot("acme/widget", 2)

    def test_sentinel_real_schema_advisory_cannot_authorize_merge(self):
        data = {"schemaVersion": 1, "targetRepo": "cardenaspiero255-lang/JARVIS",
                "headSha": "a" * 40, "verdict": "ADVISORY", "evidenceState": "UNVERIFIED", "findingCount": 2}
        att = SentinelAdvisory.from_wire(data, repository="cardenaspiero255-lang/JARVIS", head_sha="a" * 40)
        self.assertEqual(att.finding_count, 2)
        self.assertFalse(att.authorized_to_merge)
        with self.assertRaises(InvalidPartnerContract):
            SentinelAdvisory.from_wire({**data, "verdict": "CLEAR", "evidenceState": "VERIFIED"},
                                        repository="cardenaspiero255-lang/JARVIS", head_sha="a" * 40)
        with self.assertRaises(InvalidPartnerContract):
            SentinelAdvisory.from_wire(data, repository="cardenaspiero255-lang/JARVIS", head_sha="b" * 40)

    def test_sentinel_schema_unknown_fields_and_bool_integer_rejected(self):
        base = {"schemaVersion": 1, "targetRepo": "cardenaspiero255-lang/JARVIS",
                "headSha": "a" * 40, "verdict": "BLOCKED", "evidenceState": "UNVERIFIED"}
        for payload in ({**base, "permission": "merge"},
                        {**base, "schemaVersion": True},
                        {**base, "findingCount": True}):
            with self.subTest(payload=payload), self.assertRaises(InvalidPartnerContract):
                SentinelAdvisory.from_wire(payload, repository=base["targetRepo"], head_sha=base["headSha"])

    def test_gamehub_tool_manifest_must_be_explicit_and_readonly(self):
        item = {"id": "network.metrics", "kind": "TELEMETRY", "sideEffect": "READ_ONLY", "requiresNetwork": False}
        tool = GameHubToolManifest.from_descriptor(item)
        self.assertTrue(tool.read_only)
        self.assertFalse(tool.can_execute_remotely)
        for change in ({"sideEffect": "MUTATES_STATE"}, {"id": "../secret"}, {"sideEffect": "UNKNOWN"}):
            with self.subTest(change=change), self.assertRaises(InvalidPartnerContract):
                GameHubToolManifest.from_descriptor({**item, **change})

    def test_android_permissions_are_never_inferred_or_executed(self):
        gate = AndroidPermissionGate(["android.permission.RECORD_AUDIO"])
        self.assertFalse(gate.is_allowed("android.permission.RECORD_AUDIO"))
        with self.assertRaises(UnavailableIntegration):
            gate.execute({"action": "tap", "x": 100, "y": 100})
