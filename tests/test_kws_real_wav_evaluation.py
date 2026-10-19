"""KWS evaluation cannot certify performance without real, hash-locked WAVs."""
import unittest
from pathlib import Path
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "tools/evaluate_kws_real_wav.py"

def load():
    spec = importlib.util.spec_from_file_location("kws_eval", PATH)
    mod = importlib.util.module_from_spec(spec)
    import sys
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod

class RealKwsEvaluationContract(unittest.TestCase):
    def test_binary_confusion_with_real_labels(self):
        m = load()
        counts = m.summarize([(True, True), (True, False), (False, False), (False, True)])
        self.assertEqual(counts["tp"], 1)
        self.assertEqual(counts["fn"], 1)
        self.assertEqual(counts["fp"], 1)
        self.assertEqual(counts["tn"], 1)
        self.assertEqual(counts["recall"], 0.5)
        self.assertEqual(counts["fpr"], 0.5)

    def test_no_fake_precision_from_empty_or_single_class_corpus(self):
        m = load()
        with self.assertRaises(ValueError): m.summarize([])
        with self.assertRaises(ValueError): m.summarize([(True, True)])
        with self.assertRaises(ValueError): m.summarize([(False, False)])

    def test_manifest_requires_verified_audio_hashes(self):
        m = load()
        with self.assertRaises(ValueError):
            m.validate_manifest([{
                "file": "owner.wav", "sha256": "not-a-hash",
                "wake": True, "speaker": "owner-a", "split": "holdout"
            }])
        with self.assertRaises(ValueError):
            m.validate_manifest([{
                "file": "../owner.wav", "sha256": "a"*64,
                "wake": True, "speaker": "owner-a", "split": "holdout"
            }])

if __name__ == "__main__": unittest.main()
