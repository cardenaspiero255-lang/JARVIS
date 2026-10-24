"""Regression: upstream-inspired interruption is strictly STOP-only and echo-safe."""
import unittest
from pathlib import Path

APP = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
SRC = APP / "java/com/cardenaspiero255/jarvis"
CORE = SRC / "core"

class BargeInStopReuseContract(unittest.TestCase):
    def test_reuses_native_one_mic_vosk_and_platform_aec_only_in_long_session(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("AcousticEchoCanceler.isAvailable()", runner)
        self.assertIn("AcousticEchoCanceler.create(", runner)
        self.assertIn("MediaRecorder.AudioSource.VOICE_COMMUNICATION", runner)
        self.assertIn("BargeInStopPolicy()", runner)
        self.assertIn("onBargeInStop", runner)
        self.assertIn("bargeRecognizer", runner)
        self.assertIn("BargeInStopDecision.STOP_TTS", runner)
        self.assertIn("responsePlayback.get()", runner)
        self.assertIn("audio.fill(0, 0, received)", runner)
        self.assertNotIn("SpeakerEvidence.MATCHED", runner)
        self.assertNotIn("OmegaHandsService", runner)

    def test_service_stops_tts_without_routing_recognized_text_to_commands(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("onBargeInStop = {", service)
        self.assertIn("tts?.stop()", service)
        self.assertIn("ASR_NATIVE_BARGEIN_STOP", service)
        self.assertIn("nativeRunner?.setResponsePlayback(true, text)", service)
        self.assertIn("nativeRunner?.setResponsePlayback(false)", service)
        self.assertIn("ACTION_STOP", service)

    def test_stale_tts_completion_cannot_unmute_newer_playback(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        # IDs are now issued/consumed by the shared, executable phase tracker.
        # Keep both protections: one-shot utterance AND playback generation.
        self.assertIn("val utterance = ttsUtterances.begin()", service)
        self.assertIn("!ttsUtterances.done(utteranceId)", service)
        self.assertIn("ttsPlaybackTicket != ticket", service)
        self.assertIn("ttsPlaybackTicket == ticket", service)

    def test_upstream_mit_notice_kept_and_scope_documented(self):
        notice=(APP/"assets/legal/JUDEJULIUS_JARVIS_MIT_NOTICE.txt").read_text()
        self.assertIn("Copyright (c) 2026 Mohammad Joud Julius", notice)
        self.assertIn("MIT License", notice)
        doc = (Path(__file__).resolve().parents[1]/
              "docs/JARVIS_UPSTREAM_VOICE_BARGEIN_20261020.md").read_text()
        self.assertIn("REA NO EJECUTADO", doc)
        self.assertIn("no certifica", doc)
        self.assertIn("judejulius/jarvis", doc)

    def test_no_new_dangerous_manifest_permissions(self):
        manifest=(APP/"AndroidManifest.xml").read_text()
        for forbidden in ("READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
                          "BIND_VOICE_INTERACTION", "READ_MEDIA_AUDIO"):
            self.assertNotIn(forbidden, manifest)

if __name__=="__main__":
    unittest.main()
