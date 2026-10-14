"""Contract-first tests for provider handshake and safe dispatch."""
import unittest
from jarvis_contracts.integration import CapabilityRegistry, IncompatibleProvider, MissingCapability


class IntegrationContractTests(unittest.TestCase):
    def test_compatible_sentinel_provider(self):
        registry = CapabilityRegistry()
        registry.register("sentinel", "1.2", {"review.requested"})
        self.assertTrue(registry.supports("sentinel", "1.0", "review.requested"))

    def test_incompatible_major_rejected(self):
        registry = CapabilityRegistry()
        registry.register("gamehub", "2.0", {"game.launch"})
        with self.assertRaises(IncompatibleProvider):
            registry.require("gamehub", "1.0", "game.launch")

    def test_unknown_capability_rejected(self):
        registry = CapabilityRegistry()
        registry.register("sentinel", "1.0", {"review.requested"})
        with self.assertRaises(MissingCapability):
            registry.require("sentinel", "1.0", "merge.execute")

    def test_missing_provider_fails_closed(self):
        with self.assertRaises(IncompatibleProvider):
            CapabilityRegistry().require("sentinel", "1.0", "review.requested")

    def test_malformed_version_rejected(self):
        registry = CapabilityRegistry()
        with self.assertRaises(ValueError):
            registry.register("sentinel", "one", {"review.requested"})

if __name__ == "__main__":
    unittest.main()
