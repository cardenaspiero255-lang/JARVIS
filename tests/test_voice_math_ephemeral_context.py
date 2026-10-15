"""Voice math continuity must remain ephemeral, numeric-only and deny actions."""
from pathlib import Path
import unittest

JAVA=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class EphemeralVoiceMathContract(unittest.TestCase):
    def test_lifecycle_revokes_voice_math_without_recording_transcripts(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        source=(JAVA/"core/VoiceMathSession.kt").read_text()
        self.assertIn("voiceMathSession.clear()", activity)
        self.assertIn("if (!speechRecognitionPending) voiceMathSession.clear()",activity)
        self.assertIn("SessionLockChannel.VOICE_REQUEST to {",activity)
        self.assertIn("voiceMathSession.answer(transcript,",activity)
        self.assertIn("speechRecognitionPending = false",activity)
        self.assertIn("private var previous: BigDecimal? = null",source)
        self.assertNotIn("private var transcript",source)
        self.assertNotIn("SharedPreferences",source)
        self.assertNotIn("FileOutputStream",source)
        self.assertNotIn("HttpURLConnection",source)
        self.assertNotIn("Runtime.getRuntime()",source)
        self.assertNotIn("startActivity(",source)

    def test_previous_number_expires_and_cannot_survive_a_command(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        session=(JAVA/"core/VoiceMathSession.kt").read_text()
        self.assertIn("const val MAX_AGE_MILLIS = 45_000L",session)
        self.assertIn("nowMillis >= previousAtMillis",session)
        self.assertIn("if (action != VoiceAction.UNSUPPORTED) voiceMathSession.clear()",activity)
        self.assertIn("clear() // Invalid/non-math/new instructions",session)
        self.assertIn("OfflineArithmetic.evaluate(transcript, freshValue)",session)

if __name__=="__main__":
    unittest.main()
