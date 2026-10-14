"""Omega Memory TDD: verified, minimal error-family knowledge only."""
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from jarvis_contracts.error_memory import (
    VerifiedErrorMemory, UnverifiedResolution, ConflictingResolution,
)

FAMILY = "ANDROID_MIC_CRASH"
VARIANT = "a" * 64
FIX = "b" * 40
DIGEST = "c" * 64
NOW = 1_800_000_000


class ErrorMemoryTests(unittest.TestCase):
    def test_records_only_evidence_verified_fixes(self):
        with tempfile.TemporaryDirectory() as tmp:
            with VerifiedErrorMemory(tmp + "/state.db", verifier=lambda _: False) as store:
                with self.assertRaises(UnverifiedResolution):
                    store.remember(FAMILY, VARIANT, FIX, DIGEST,
                                   now=NOW, expires_at=NOW + 86400)
                self.assertIsNone(store.lookup(FAMILY, VARIANT, now=NOW))

    def test_survives_restart_without_storing_raw_crashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            with VerifiedErrorMemory(path, verifier=lambda evidence: evidence["fix_sha"] == FIX) as store:
                store.remember(FAMILY, VARIANT, FIX, DIGEST, now=NOW, expires_at=NOW + 86400)
            with VerifiedErrorMemory(path, verifier=lambda _: False) as store:
                result = store.lookup(FAMILY, VARIANT, now=NOW)
                self.assertEqual(result.fix_sha, FIX)
                self.assertEqual(result.evidence_digest, DIGEST)
                self.assertNotIn("stacktrace", repr(result).lower())

    def test_conflicting_fix_cannot_replace_prior_verified_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            with VerifiedErrorMemory(tmp + "/state.db", verifier=lambda _: True) as store:
                store.remember(FAMILY, VARIANT, FIX, DIGEST, now=NOW, expires_at=NOW + 86400)
                with self.assertRaises(ConflictingResolution):
                    store.remember(FAMILY, VARIANT, "d" * 40, DIGEST,
                                   now=NOW, expires_at=NOW + 86400)
                self.assertEqual(store.lookup(FAMILY, VARIANT, now=NOW).fix_sha, FIX)

    def test_expiration_and_erase_are_enforced(self):
        with tempfile.TemporaryDirectory() as tmp:
            with VerifiedErrorMemory(tmp + "/state.db", verifier=lambda _: True) as store:
                store.remember(FAMILY, VARIANT, FIX, DIGEST, now=NOW, expires_at=NOW + 60)
                self.assertIsNone(store.lookup(FAMILY, VARIANT, now=NOW + 60))
                store.erase(FAMILY, VARIANT)
                self.assertIsNone(store.lookup(FAMILY, VARIANT, now=NOW))

    def test_invalid_sha_and_infinite_expiry_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with VerifiedErrorMemory(tmp + "/state.db", verifier=lambda _: True) as store:
                for variant, fix, expires in [
                    ("../secret", FIX, NOW + 100),
                    (VARIANT, "bad-sha", NOW + 100),
                    (VARIANT, FIX, float("nan")),
                    (VARIANT, FIX, NOW + 100 * 86400),
                ]:
                    with self.subTest(variant=variant, fix=fix, expires=expires):
                        with self.assertRaises(ValueError):
                            store.remember(FAMILY, variant, fix, DIGEST,
                                           now=NOW, expires_at=expires)

    def test_concurrent_verified_inserts_are_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp) / "state.db")
            def insert(_):
                with VerifiedErrorMemory(path, verifier=lambda _: True) as store:
                    return store.remember(FAMILY, VARIANT, FIX, DIGEST,
                                          now=NOW, expires_at=NOW + 86400).fix_sha
            with ThreadPoolExecutor(max_workers=6) as pool:
                values = list(pool.map(insert, range(6)))
            self.assertEqual(values, [FIX] * 6)

    def test_verifier_exception_does_not_commit_memory(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/state.db"
            def raise_verifier(_):
                raise TimeoutError("proof unavailable")
            with VerifiedErrorMemory(path, verifier=raise_verifier) as store:
                with self.assertRaises(UnverifiedResolution):
                    store.remember(FAMILY, VARIANT, FIX, DIGEST,
                                   now=NOW, expires_at=NOW + 100)
                self.assertIsNone(store.lookup(FAMILY, VARIANT, now=NOW))
