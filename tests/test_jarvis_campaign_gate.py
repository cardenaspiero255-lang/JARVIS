"""Adversarial integrity tests for campaign gate. All fixtures are synthetic."""
import json
import unittest
from tools.jarvis_campaign_gate import summarize

SPEAKER_SHA = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
GGUF_SHA = "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"


def gguf(source="emulator"):
    return {
        "schema_version": 1, "model_sha256": GGUF_SHA,
        "device": {"source": source, "model": "sdk_gphone" if source == "emulator"
                   else "V2158", "manufacturer": "Google" if source == "emulator"
                   else "vivo", "sdk_int": 34,
                   "physical_provenance_supplied": source == "physical_reported"},
        "latency_ms": {"first": 900, "subsequent_samples": 4,
                       "subsequent_p50": 200, "subsequent_p95": 400},
        "memory_kib": {"peak_pss": 180000, "baseline_pss": 55000},
        "voice": {"gguf_voice_pipeline": "NOT_IMPLEMENTED",
                  "asr_audio_validated": False, "tts_audio_validated": False,
                  "llm_tool_authority": False},
        "certification": {"overall": "NOT_CERTIFIED",
                          "physical_vivo_verified": False,
                          "voice_e2e_verified": False}
    }


def owner(sealed=False, enough=False):
    counts = lambda n: {"trials": n, "false_accepts": 0,
                        "error_rate": 0.0, "error_rate_upper95": .009}
    return {
        "authentication": "NOT_CERTIFIED",
        "model_sha256": SPEAKER_SHA, "holdout_precommitted": sealed,
        "sufficient_for_1pct_far": enough,
        "baseline_non_regression_verified": False,
        "calibration": {"impostor": counts(300), "replay": counts(300)},
        "holdout": {"impostor": counts(300), "replay": counts(300),
                    "genuine": {"trials": 30, "false_rejects": 1,
                                "error_rate": 1/30}}
    }


def kws():
    return {"status": "EVALUATED_REAL_AUDIO",
            "metrics": {"n": 2, "tp": 1, "fp": 0, "tn": 1, "fn": 0,
                        "precision": 1.0, "recall": 1.0,
                        "fpr": 0.0, "fnr": 0.0},
            "cases": [
                {"sha256": "a"*64, "wake": True, "detected": True,
                 "speaker": "owner-a", "split": "holdout"},
                {"sha256": "b"*64, "wake": False, "detected": False,
                 "speaker": "impostor-01", "split": "holdout"}
            ]}


class JarvisCampaignGateTest(unittest.TestCase):
    def test_missing_real_evidence_is_not_certification(self):
        result = summarize()
        self.assertEqual("NOT_CERTIFIED", result["certification"])
        self.assertFalse(result["real_audio_voice_e2e_verified"])
        self.assertFalse(result["vivo_full_session_verified"])
        self.assertTrue(all(value == "PENDING" for value in result["sources"].values()))

    def test_gguf_emulator_cannot_certify_vivo_or_audio(self):
        result = summarize(gguf_report=gguf())
        self.assertEqual("EMULATOR_METRICS_REPORTED", result["sources"]["gguf"])
        self.assertEqual("PENDING", result["sources"]["vivo_gguf"])
        self.assertFalse(result["real_audio_voice_e2e_verified"])
        result = summarize(gguf_report=gguf("physical_reported"))
        self.assertEqual("PHYSICAL_GGUF_METRICS_REPORTED", result["sources"]["vivo_gguf"])
        self.assertFalse(result["vivo_full_session_verified"])

    def test_forged_gguf_certification_or_model_mismatch_is_rejected(self):
        fake = gguf()
        fake["certification"]["overall"] = "CERTIFIED"
        with self.assertRaises(ValueError): summarize(gguf_report=fake)
        fake = gguf()
        fake["model_sha256"] = "0" * 64
        with self.assertRaises(ValueError): summarize(gguf_report=fake)
        fake = gguf()
        fake["voice"]["llm_tool_authority"] = True
        with self.assertRaises(ValueError): summarize(gguf_report=fake)

    def test_biometric_requires_pinned_model_and_holdout_seal(self):
        result = summarize(owner_report=owner())
        self.assertEqual("COUNTS_REPORTED_NOT_SEALED",
                         result["sources"]["owner_biometrics"])
        result = summarize(owner_report=owner(sealed=True, enough=True))
        self.assertEqual("STATISTICS_REPORTED_AUDIT_REQUIRED",
                         result["sources"]["owner_biometrics"])
        self.assertEqual("NOT_CERTIFIED", result["certification"])
        fake = owner(sealed=True, enough=True)
        fake["authentication"] = "MATCHED"
        with self.assertRaises(ValueError): summarize(owner_report=fake)

    def test_kws_count_and_source_integrity(self):
        result = summarize(kws_report=kws())
        self.assertEqual("REAL_WAV_EVALUATION_REPORTED_AUDIT_REQUIRED",
                         result["sources"]["kws"])
        self.assertEqual("NOT_CERTIFIED", result["certification"])
        bad = kws()
        bad["cases"][1]["sha256"] = "a" * 64
        with self.assertRaises(ValueError): summarize(kws_report=bad)
        bad = kws()
        bad["metrics"]["fp"] = 2
        with self.assertRaises(ValueError): summarize(kws_report=bad)

    def test_no_private_labels_hashes_scores_or_filenames_in_public_summary(self):
        report = summarize(gguf_report=gguf(), owner_report=owner(),
                           kws_report=kws())
        serialized = json.dumps(report)
        for private_value in ("owner-a", "impostor-01", "a"*64, "b"*64,
                              "holdout_sha256", "session_id", "recording_sha256"):
            self.assertNotIn(private_value, serialized)
        self.assertFalse(report["owner_actions_authorized"])
        self.assertEqual("NOT_CERTIFIED", report["certification"])


    def test_unmeasured_gguf_voice_cannot_be_falsely_declared_validated(self):
        altered = gguf()
        altered["voice"]["gguf_voice_pipeline"] = "FULLY_VALIDATED"
        with self.assertRaises(ValueError):
            summarize(gguf_report=altered)

    def test_gguf_rejects_inverted_latency_and_memory(self):
        impossible = gguf()
        impossible["latency_ms"]["subsequent_p50"] = 500
        impossible["latency_ms"]["subsequent_p95"] = 400
        with self.assertRaises(ValueError):
            summarize(gguf_report=impossible)
        impossible = gguf()
        impossible["memory_kib"]["baseline_pss"] = 200000
        impossible["memory_kib"]["peak_pss"] = 180000
        with self.assertRaises(ValueError):
            summarize(gguf_report=impossible)

    def test_owner_claiming_sufficient_trials_must_have_calibration_genuines(self):
        forged = owner(sealed=True, enough=True)
        forged["calibration"]["genuine"] = {"trials": 1, "false_rejects": 0}
        with self.assertRaises(ValueError):
            summarize(owner_report=forged)

    def test_owner_rejects_impossible_forged_zero_error_rates(self):
        forged = owner(sealed=True, enough=True)
        forged["holdout"]["impostor"]["false_accepts"] = 301
        with self.assertRaises(ValueError):
            summarize(owner_report=forged)



if __name__ == "__main__":
    unittest.main()
