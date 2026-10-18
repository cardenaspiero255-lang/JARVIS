"""Offline KWS bundle and future biometric wiring regression contracts."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app"
SRC = APP / "src/main/java/com/cardenaspiero255/jarvis"
SCRIPT = ROOT / "tools/prepare_kws_model.py"


class KwsBundleContract(unittest.TestCase):
    def test_archive_validation_refuses_modified_or_incomplete_files(self):
        spec = importlib.util.spec_from_file_location("kwsprep", SCRIPT)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        b = io.BytesIO()
        with tarfile.open(fileobj=b, mode="w:bz2") as tar:
            f = b"corrupted-model"
            info = tarfile.TarInfo(mod.MODEL + "/" + next(iter(mod.REQUIRED)))
            info.size = len(f)
            tar.addfile(info, io.BytesIO(f))
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            mod.verified_files(b.getvalue())

    def test_build_bundles_only_verified_upstream_assets_and_keyword(self):
        gradle = (APP / "build.gradle.kts").read_text()
        preparation = SCRIPT.read_text()
        self.assertIn("prepare_kws_model.py", gradle)
        self.assertIn("assets.srcDir(kwsAssets)", gradle)
        self.assertIn("dependsOn(bundleVerifiedKws)", gradle)
        self.assertIn("required", preparation.lower())
        self.assertIn('tokenizer.encode("JARVIS"', preparation)
        self.assertIn('@Jarvis\\n', preparation)

    def test_runtime_copies_pinned_model_to_private_storage_before_native_init(self):
        store = (SRC / "VerifiedKwsModelStore.kt").read_text()
        bridge = (SRC / "SherpaNativeWakeBridge.kt").read_text()
        self.assertIn("context.noBackupFilesDir", store)
        self.assertIn("OfflineKwsModelGate().verify(", store)
        self.assertIn("verified_sherpa_kws/", store)
        self.assertIn("VerifiedKwsModelStore(context).ensureInstalled()", bridge)
        self.assertIn("KWS_NATIVE_MODEL_UNAVAILABLE", bridge)

    def test_no_owner_match_without_independent_calibration(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        policy = (SRC / "core/SpeakerEnrollmentPolicy.kt").read_text()
        self.assertIn("SpeakerEvidence.UNAVAILABLE", runner)
        self.assertIn("return SpeakerEvidence.UNCERTAIN", policy)


if __name__ == "__main__":
    unittest.main()
