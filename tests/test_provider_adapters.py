import unittest
from jarvis_contracts.adapters import GitHubReadOnlyAdapter, UnsupportedProvider, ProviderNotConfigured


class AdapterTests(unittest.TestCase):
    def test_github_reads_pr_from_injected_authenticated_client(self):
        calls = []
        def fetch(path):
            calls.append(path)
            return {"number": 2, "head": {"sha": "a" * 40}}
        adapter = GitHubReadOnlyAdapter(fetch)
        result = adapter.get_pull_request("cardenaspiero255-lang/JARVIS", 2)
        self.assertEqual(result["head"]["sha"], "a" * 40)
        self.assertEqual(calls, ["/repos/cardenaspiero255-lang/JARVIS/pulls/2"])

    def test_github_rejects_untrusted_repo_path(self):
        adapter = GitHubReadOnlyAdapter(lambda path: {})
        with self.assertRaises(ValueError):
            adapter.get_pull_request("../other", 2)

    def test_write_actions_are_not_exposed(self):
        adapter = GitHubReadOnlyAdapter(lambda path: {})
        self.assertFalse(hasattr(adapter, "merge"))

    def test_unconfigured_provider_fails_closed(self):
        with self.assertRaises(ProviderNotConfigured):
            UnsupportedProvider("gamehub").execute({"type": "game.launch"})
