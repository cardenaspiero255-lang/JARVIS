"""Never let an untrusted constructor set execution-authority flags."""
import unittest

from jarvis_contracts.partner_contracts import SentinelAdvisory, GameHubToolManifest


class InertContractTests(unittest.TestCase):
    def test_sentinel_advisory_cannot_be_constructed_as_merge_authority(self):
        with self.assertRaises(TypeError):
            SentinelAdvisory("cardenaspiero255-lang/JARVIS", "a"*40, "ADVISORY",
                             "UNVERIFIED", 0, authorized_to_merge=True)
        advisory = SentinelAdvisory("cardenaspiero255-lang/JARVIS", "a"*40,
                                    "ADVISORY", "UNVERIFIED", 0)
        self.assertFalse(advisory.authorized_to_merge)

    def test_gamehub_descriptor_cannot_be_constructed_as_remote_executor(self):
        with self.assertRaises(TypeError):
            GameHubToolManifest("network.metrics", "TELEMETRY", True,
                                can_execute_remotely=True)
        descriptor = GameHubToolManifest("network.metrics", "TELEMETRY", True)
        self.assertFalse(descriptor.can_execute_remotely)
