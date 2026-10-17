"""Regression: explicit Android-inspection phrases must be routed locally.

The spoken words may only open a *preview*. They never authorize Shizuku,
execute commands, request root, or depend on a remote LLM service.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
ROUTER = JAVA / "core/SafeAutomationEngine.kt"
ACTIVITY = JAVA / "MainActivity.kt"
CONSENT = JAVA / "CompanionCenterActivity.kt"


class ShizukuVoiceRoutingContract(unittest.TestCase):
    def test_short_phrase_has_explicit_local_preview_route(self):
        source = ROUTER.read_text(encoding="utf-8")
        self.assertIn('"revisa android", "jarvis revisa android"', source)
        self.assertIn("SHIZUKU_ADVANCED_DIAGNOSTIC", source)
        self.assertNotIn('contains("revisa android")', source)

    def test_router_precedes_general_voice_fallback(self):
        source = ACTIVITY.read_text(encoding="utf-8")
        start = source.index("private fun processRecognizedSpeech(transcript: String)")
        next_section = source.index("private fun openTtsSettingsByTap()", start)
        section = source[start:next_section]
        self.assertLess(section.index("SafeAutomationVoiceRouter.parse(transcript)"),
            section.index("val action = parser.parse(transcript)"))
        self.assertIn('putExtra(CompanionCenterActivity.EXTRA_ROUTINE_PREVIEW, proposed.name)', section)
        self.assertIn('.setPositiveButton("REVISAR PLAN")', section)
        self.assertIn("return", section)

    def test_only_visible_consent_starts_shizuku(self):
        source = CONSENT.read_text(encoding="utf-8")
        self.assertIn('confirmRoutine(routine)', source)
        self.assertIn('.setPositiveButton("AUTORIZAR UNA VEZ")', source)
        self.assertIn('if (report.outcome == SafeAutomationOutcome.COMPLETED &&', source)
        self.assertIn('runAdvancedShizuku()', source)


if __name__ == "__main__":
    unittest.main()
