"""Voice silence diagnostics and JARVIS dashboard regression contracts."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
RES=ROOT/"apps/android/app/src/main/res"

class VoiceAndUiContracts(unittest.TestCase):
    def test_tts_tracks_actual_utterances_and_audio_route(self):
        src=(APP/"SpokenReplyEngine.kt").read_text()
        for text in ("UtteranceProgressListener", "setOnUtteranceProgressListener", 
                     "AudioAttributes.USAGE_MEDIA", "AudioManager.STREAM_MUSIC",
                     "SpeechUtteranceTracker", "onStart(utteranceId:", "onDone(utteranceId:"):
            self.assertIn(text,src)
        self.assertNotIn('"Android aceptó la respuesta de voz local."',src)

    def test_dashboard_has_voice_test_accessible_near_top(self):
        src=(APP/"MainActivity.kt").read_text()
        for text in ("OmegaUiKit", "PROBAR AUDIO JARVIS", 
                     "OmegaUiKit.hero", "OmegaUiKit.sectionTitle"):
            self.assertIn(text,src)
        self.assertLess(src.index("PROBAR AUDIO JARVIS"),src.index("OMEGA CHAT"))

    def test_installed_launcher_icon_and_secure_window(self):
        manifest=(RES.parent/"AndroidManifest.xml").read_text()
        self.assertIn('@mipmap/ic_launcher',manifest)
        self.assertTrue((RES/"mipmap-anydpi-v26/ic_launcher.xml").is_file())
        self.assertTrue((RES/"drawable/ic_jarvis_omega_foreground.xml").is_file())
        src=(APP/"MainActivity.kt").read_text()
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE",src)
        self.assertIn("SCREEN_PROJECTION",src)
        self.assertIn("SessionLockCoordinator().lock(actions)",src)

if __name__=="__main__": unittest.main()
