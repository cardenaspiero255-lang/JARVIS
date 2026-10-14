"""TDD: audited read-only GitHub snapshots are not merge authorizations."""
import json
import unittest

from jarvis_contracts.github_audit import GitHubReadOnlyAudit, IncompleteSnapshot, StaleSnapshot
from jarvis_contracts.github_rest import GitHubRESTClient


REPO = "cardenaspiero255-lang/JARVIS"
SHA = "a" * 40
NEW_SHA = "b" * 40


def check(sha=SHA, number=0, *, conclusion="success"):
    return {"name": "test-%d" % number, "head_sha": sha,
            "status": "completed", "conclusion": conclusion}


class ReadOnlySnapshotTests(unittest.TestCase):
    def test_multi_page_checks_and_reviews_are_bound_to_head(self):
        paths = []
        def get(path):
            paths.append(path)
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if path.endswith("/check-runs?per_page=100&page=1"):
                return {"total_count": 101, "check_runs": [check(number=i) for i in range(100)]}
            if path.endswith("/check-runs?per_page=100&page=2"):
                return {"total_count": 101, "check_runs": [check(number=100)]}
            if path.endswith("/reviews?per_page=100&page=1"):
                return [{"state": "COMMENTED", "commit_id": SHA, "user": {"login": "reviewer"}}]
            raise AssertionError("unexpected URL: " + path)
        audit = GitHubReadOnlyAudit(get)
        snapshot = audit.snapshot(REPO, 2)
        self.assertEqual(snapshot.head_sha, SHA)
        self.assertEqual(len(snapshot.checks), 101)
        self.assertEqual(len(snapshot.reviews), 1)
        self.assertTrue(snapshot.check_inventory_complete)
        self.assertFalse(snapshot.authorized_to_merge)
        self.assertEqual(paths.count("/repos/" + REPO + "/pulls/2"), 2)

    def test_check_list_truncation_fails_closed(self):
        def get(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if "/check-runs?" in path:
                return {"total_count": 101, "check_runs": [check(number=i) for i in range(99)]}
            raise AssertionError(path)
        with self.assertRaises(IncompleteSnapshot):
            GitHubReadOnlyAudit(get).snapshot(REPO, 2)

    def test_head_changes_during_audit_fails_closed(self):
        pr_calls = []
        def get(path):
            if path.endswith("/pulls/2"):
                pr_calls.append(path)
                return {"number": 2, "head": {"sha": SHA if len(pr_calls) == 1 else NEW_SHA}}
            if "/check-runs?" in path:
                return {"total_count": 1, "check_runs": [check()]}
            if "/reviews?" in path:
                return []
            raise AssertionError(path)
        with self.assertRaises(StaleSnapshot):
            GitHubReadOnlyAudit(get).snapshot(REPO, 2)

    def test_mismatched_check_sha_fails_closed(self):
        def get(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if "/check-runs?" in path:
                return {"total_count": 1, "check_runs": [check(NEW_SHA)]}
            raise AssertionError(path)
        with self.assertRaises(StaleSnapshot):
            GitHubReadOnlyAudit(get).snapshot(REPO, 2)

    def test_approving_review_never_grants_merge_authority(self):
        def get(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if "/check-runs?" in path:
                return {"total_count": 1, "check_runs": [check()]}
            if "/reviews?" in path:
                return [{"state": "APPROVED", "commit_id": SHA, "user": {"login": "admin"}}]
            raise AssertionError(path)
        snap = GitHubReadOnlyAudit(get).snapshot(REPO, 2)
        self.assertFalse(snap.authorized_to_merge)

    def test_missing_check_inventory_never_looks_green(self):
        def get(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if "/check-runs?" in path:
                return {"total_count": 0, "check_runs": []}
            if "/reviews?" in path:
                return []
            raise AssertionError(path)
        snap = GitHubReadOnlyAudit(get).snapshot(REPO, 2)
        self.assertTrue(snap.check_inventory_complete)
        self.assertFalse(snap.authorized_to_merge)
        self.assertEqual(snap.checks, ())

    def test_review_pagination_is_bounded_to_avoid_partial_verdicts(self):
        def get(path):
            if path.endswith("/pulls/2"):
                return {"number": 2, "head": {"sha": SHA}}
            if "/check-runs?" in path:
                return {"total_count": 1, "check_runs": [check()]}
            if "/reviews?" in path:
                return [{"state": "COMMENTED", "commit_id": SHA}] * 100
            raise AssertionError(path)
        with self.assertRaises(IncompleteSnapshot):
            GitHubReadOnlyAudit(get, max_pages=2).snapshot(REPO, 2)

    def test_rest_client_accepts_only_whitelisted_paginated_gets(self):
        requests = []
        class Reply:
            status = 200
            def __init__(self, data): self.data = json.dumps(data).encode()
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, size): return self.data[:size]
        def opener(req, timeout):
            requests.append((req.full_url, req.get_method()))
            if "/reviews?" in req.full_url:
                return Reply([{"state": "COMMENTED"}])
            return Reply({"total_count": 0, "check_runs": []})
        client = GitHubRESTClient(lambda: "runtime-token", opener=opener)
        checks = client.get("/repos/acme/widget/commits/" + SHA + "/check-runs?per_page=100&page=1")
        self.assertEqual(checks["total_count"], 0)
        reviews = client.get("/repos/acme/widget/pulls/2/reviews?per_page=100&page=1")
        self.assertEqual(len(reviews), 1)
        self.assertTrue(all(method == "GET" for _, method in requests))
        with self.assertRaises(ValueError):
            client.get("/repos/acme/widget/merges")
