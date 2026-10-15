"""A stalled local TTS request is stopped, not left speaking late."""
from pathlib import Path
import unittest

ENGINE = Path(__file__).resolve().parents[1] / (
    "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/SpokenReplyEngine.kt"
)

class SpeechTimeoutSafety(unittest.TestCase):
    def test_timeout_revokes_engine_queue_and_ui_progress(self):
        source = ENGINE.read_text()
        timeout = source.split("if (!closed && tracker.timedOut(ticket)) {",1)[1].split("\n            }",1)[0]
        self.assertIn("synth?.stop()",timeout)
        self.assertIn("onSpeechProgress(false)",timeout)
        self.assertIn("showStatus(",timeout)

if __name__ == "__main__":
    unittest.main()
