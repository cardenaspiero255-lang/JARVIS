"""Behavioral checks: GGUF benchmark reports cannot invent physical or quality evidence."""
import json
import tempfile
import unittest
from pathlib import Path

from tools.gguf_qa_report import evaluate, nearest_rank, as_markdown


def payload():
    return {
        "schema_version": 1,
        "device": {"source": "emulator", "manufacturer": "Google", "model": "sdk_gphone",
                   "sdk_int": 34},
        "model": {"sha256": "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5",
                  "bytes": 101016128},
        "inference": {
            "cold_request_ms": 900,
            "subsequent_request_ms": [100, 200, 300, 400],
            "generated_characters": [36, 30, 45, 50, 40],
            "baseline_pss_kib": 55000, "peak_pss_kib": 175000,
            "peak_java_heap_kib": 8500,
            "warm_cache": {
                "native_model_loads": 1,
                "native_cache_hits": 4,
                "native_cache_evictions": 0,
                "verified_sha_revision": True,
            }
        },
        "quality": {
            "method": "keyword_proxy_v1",
            "cases": [
                {"id": "chile_capital", "keyword_matched": True},
                {"id": "arithmetic", "keyword_matched": False},
                {"id": "red_planet", "keyword_matched": True},
                {"id": "computer_processor", "keyword_matched": False}
            ]
        },
        "voice": {
            "gguf_voice_pipeline": "NOT_IMPLEMENTED",
            "asr_audio_validated": False, "tts_audio_validated": False,
            "llm_tool_authority": False
        }
    }


class GgufReportTest(unittest.TestCase):
    def test_computes_p50_p95_and_quality_without_calling_it_certification(self):
        record = evaluate(payload())
        self.assertEqual(250, record["latency_ms"]["subsequent_p50"])
        self.assertEqual(400, record["latency_ms"]["subsequent_p95"])
        self.assertEqual(900, record["latency_ms"]["first"])
        self.assertEqual(120000, record["memory_kib"]["peak_pss_increase"])
        self.assertEqual(2, record["quality_proxy"]["matched"])
        self.assertEqual(4, record["quality_proxy"]["count"])
        self.assertEqual(50.0, record["quality_proxy"]["percent"])
        self.assertEqual(1, record["warm_model"]["native_model_loads"])
        self.assertEqual(4, record["warm_model"]["native_cache_hits"])
        self.assertTrue(record["warm_model"]["verified_sha_revision"])
        self.assertFalse(record["certification"]["physical_vivo_verified"])
        self.assertFalse(record["certification"]["voice_e2e_verified"])
        self.assertFalse(record["certification"]["semantic_quality_certified"])
        self.assertEqual("NOT_CERTIFIED", record["certification"]["overall"])
        # The one-turn voice code exists; this GGUF-only fixture does NOT test it.
        self.assertEqual("NOT_MEASURED_IN_GGUF_E2E",
                         record["voice"]["gguf_voice_pipeline"])
        self.assertIn("NO MEDIDO POR ESTA SUITE", as_markdown(record))

    def test_rejects_forged_missing_or_inconsistent_warm_cache_counters(self):
        missing = payload()
        missing["inference"].pop("warm_cache")
        with self.assertRaises(ValueError):
            evaluate(missing)
        for field, invalid in (
            ("native_model_loads", 0),
            ("native_model_loads", 2),
            ("native_cache_hits", 3),
            ("native_cache_hits", True),
            ("native_cache_evictions", -1),
            ("verified_sha_revision", False),
        ):
            forged = payload()
            forged["inference"]["warm_cache"][field] = invalid
            with self.subTest(field=field, invalid=invalid), self.assertRaises(ValueError):
                evaluate(forged)

    def test_nearest_rank_quantiles_use_real_observations(self):
        self.assertEqual(3, nearest_rank([1, 2, 3, 4, 5], 0.5))
        self.assertEqual(5, nearest_rank([1, 2, 3, 4, 5], 0.95))
        with self.assertRaises(ValueError):
            nearest_rank([], 0.95)

    def test_rejects_missing_samples_or_fake_physical_claim(self):
        bad = payload()
        bad["inference"]["subsequent_request_ms"] = []
        with self.assertRaises(ValueError):
            evaluate(bad)
        bad = payload()
        bad["voice"]["gguf_voice_pipeline"] = "FULLY_VALIDATED"
        with self.assertRaises(ValueError):
            evaluate(bad)
        bad = payload()
        bad["device"]["source"] = "physical"
        # Physical evidence must carry explicit evidence from a connected
        # device and cannot be inferred from a CI emulator.
        with self.assertRaises(ValueError):
            evaluate(bad)

    def test_rejects_missing_quality_cases_and_unrealistic_memory(self):
        bad = payload()
        bad["quality"]["cases"] = []
        with self.assertRaises(ValueError):
            evaluate(bad)
        bad = payload()
        bad["inference"]["peak_pss_kib"] = -1
        with self.assertRaises(ValueError):
            evaluate(bad)
        bad = payload()
        bad["quality"]["cases"][0]["keyword_matched"] = "yes"
        with self.assertRaises(ValueError):
            evaluate(bad)


if __name__ == "__main__":
    unittest.main()
