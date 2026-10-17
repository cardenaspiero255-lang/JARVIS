"""Regression checks for the Qwen install/benchmark UX on Android.

The old UI wrote all progress to a status view near the top of a long scroll,
so the installer and five-test benchmark appeared to do nothing.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class QwenFeedbackContract(unittest.TestCase):
    def test_model_state_is_visible_next_to_qwen_buttons(self):
        activity = (ANDROID / "MainActivity.kt").read_text()
        self.assertIn("section.addView(llmStatusText)", activity)
        self.assertLess(
            activity.index("section.addView(llmStatusText)"),
            activity.index('primaryButton("INSTALAR QWEN OFICIAL — 491 MB")'),
        )
        self.assertIn("private fun showLlmStatus(message: String)", activity)
        self.assertIn("llmStatusText.text = message", activity)

    def test_horizontal_progress_bar_is_beside_install_controls(self):
        activity = (ANDROID / "MainActivity.kt").read_text()
        self.assertIn("ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)", activity)
        self.assertIn("section.addView(qwenDownloadBar)", activity)
        self.assertLess(
            activity.index("section.addView(qwenDownloadBar)"),
            activity.index('primaryButton("INSTALAR QWEN OFICIAL — 491 MB")'),
        )
        self.assertIn("qwenDownloadBar.isIndeterminate = snapshot.phase == QwenInstallProgress.Phase.CONNECTING", activity)
        self.assertIn("qwenDownloadBar.progress = snapshot.percent", activity)

    def test_progress_bytes_are_reported_from_download_stream(self):
        activity = (ANDROID / "MainActivity.kt").read_text()
        engine = (ANDROID / "OfflineLlmEngine.kt").read_text()
        self.assertIn("onProgress = { percent, downloadedBytes ->", activity)
        self.assertIn("QwenInstallProgress.fromBytes(downloadedBytes)", activity)
        self.assertIn("onProgress(percentage, count)", engine)
        self.assertIn("QwenInstallProgress.verified()", activity)

    def test_network_failures_are_actionable_not_silently_swallowed(self):
        engine = (ANDROID / "OfflineLlmEngine.kt").read_text()
        self.assertIn("QWEN_DOWNLOAD", engine)
        self.assertIn("QWEN_TLS", engine)
        self.assertIn("QWEN_HTTP", engine)

    def test_download_reports_progress_and_terminal_outcome(self):
        activity = (ANDROID / "MainActivity.kt").read_text()
        self.assertIn("QwenInstallProgress.fromBytes(downloadedBytes)", activity)
        self.assertIn('setTitle(if (success) "Qwen instalado" else "Qwen no instalado")', activity)
        self.assertIn('showLlmStatus("Qwen: descarga interrumpida al salir', activity)
        self.assertIn("setQwenDownloadProgress(QwenInstallProgress.verified())", activity)

    def test_status_requires_exact_size_and_digest(self):
        engine = (ANDROID / "OfflineLlmEngine.kt").read_text()
        self.assertIn("fun checkLocalStatus(", engine)
        self.assertIn("modelPath.length() != LocalLlmPolicy.MODEL_BYTES", engine)
        self.assertIn("!LocalModelIntegrity.verify(modelPath, LocalLlmPolicy.SHA256)", engine)
        self.assertIn("LocalModelGate.canRun(", engine)

    def test_benchmark_cannot_claim_success_without_preflight(self):
        activity = (ANDROID / "MainActivity.kt").read_text()
        body = activity.split("private fun runLocalLlmBenchmark()", 1)[1].split(
            "private fun showLlmStatus(", 1
        )[0]
        self.assertIn("offlineLlm.checkLocalStatus { ready, report ->", body)
        self.assertIn("if (!ready)", body)
        self.assertIn("Las 5 pruebas no se iniciaron.", body)
        self.assertIn("if (metric == null || generated == null)", body)
        self.assertIn("benchmark 5/5 completado", body)


if __name__ == "__main__":
    unittest.main()
