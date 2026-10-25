"""Source wiring checks, NOT Android/TTS acoustic validation.

Executable phase/cancellation regressions live in SpeechUtteranceTrackerTest.
These checks ensure the foreground service actually uses that tested tracker.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/HandsFreePreviewService.kt"


class PreviewTtsLifecycleContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SERVICE.read_text()

    def section(self, start, end):
        return self.source.split(start, 1)[1].split(end, 1)[0]

    def test_started_speech_has_a_separate_completion_deadline(self):
        started = self.section("override fun onStart(utteranceId: String?)",
                               "override fun onDone(utteranceId: String?)")
        self.assertIn("ttsUtterances.started(utteranceId)", started)
        self.assertIn("clearTtsWatchdog()", started)
        self.assertIn("ttsUtterances.playbackTimedOut(utteranceId)", started)
        self.assertIn("TTS_PLAYBACK_TIMEOUT", started)
        self.assertIn("60_000L", started)
        self.assertNotIn("finishOrResume(", started)

    def test_start_timeout_does_not_unmute_a_live_or_late_tts_queue(self):
        deadline = self.section("val deadline = Runnable", "private fun closeRecognizer()")
        self.assertIn("ttsUtterances.timedOut(utterance)", deadline)
        self.assertIn('finish("TTS_NO_PLAYBACK_CALLBACK:', deadline)
        self.assertNotIn("finishOrResume(", deadline)

    def test_stop_revokes_tts_before_async_service_destruction(self):
        finish = self.section("private fun finish(reason: String)",
                              "private fun acquireShortWakeLock()")
        self.assertIn("ttsUtterances.cancel()", finish)
        self.assertIn("clearTtsWatchdog()", finish)
        self.assertIn("pendingSpeech = null", finish)
        self.assertLess(finish.index("ttsUtterances.cancel()"), finish.index("tts?.stop()"))
        init = self.section("private fun prepareTts()", "private fun finishOrResume")
        self.assertIn("closed || endedNormally || !isRunning", init)

    def test_one_turn_also_bounds_wait_for_tts_initialization(self):
        pending = self.section("pendingSpeech = text", "private fun play(text: String)")
        self.assertNotIn("if (extendedListening)", pending)
        self.assertIn("ttsPlaybackTicket == ticket", pending)
        self.assertIn('finish("TTS_INIT_TIMEOUT:', pending)

    def test_callbacks_are_one_shot_and_errors_close_the_session(self):
        play = self.section("private fun play(text: String)", "private fun closeRecognizer()")
        self.assertIn("ttsUtterances.done(utteranceId)", play)
        self.assertIn("ttsUtterances.failed(utteranceId)", play)
        errors = play.split('@Deprecated("Deprecated in Java")', 1)[1]
        self.assertNotIn("finishOrResume(", errors)

    def test_new_inference_invalidates_a_pending_echo_tail_resume(self):
        question = self.section("private fun processOfflineQuestion(question: String)",
                                "private fun batteryAnswer()")
        self.assertIn("++ttsPlaybackTicket", question)
        self.assertIn("ttsUtterances.cancel()", question)
        self.assertLess(question.index("++ttsPlaybackTicket"),
                        question.index("nativeRunner?.setResponsePlayback(true)"))


if __name__ == "__main__":
    unittest.main()
