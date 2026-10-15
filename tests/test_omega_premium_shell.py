"""Premium JARVIS shell assets and performance/privacy contracts."""
import hashlib
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]/"apps/android/app/src/main"
JAVA=ROOT/"java/com/cardenaspiero255/jarvis"
RES=ROOT/"res"
ANDROID="{http://schemas.android.com/apk/res/android}"

class OmegaPremiumShell(unittest.TestCase):
    def test_launcher_uses_uploaded_bitmap_unchanged_in_design(self):
        source=RES/"drawable-nodpi/jarvis_reference.jpg"
        self.assertTrue(source.is_file(),"The uploaded image must be packaged as binary drawable")
        self.assertGreater(source.stat().st_size,10_000)
        icon=ET.parse(RES/"mipmap-anydpi-v26/ic_launcher.xml").getroot()
        foreground=icon.find("foreground")
        self.assertEqual("@drawable/jarvis_reference",foreground.get(ANDROID+"drawable"))
        self.assertIn('@mipmap/ic_launcher',(ROOT/"AndroidManifest.xml").read_text())

    def test_gpu_animation_pauses_during_scrolling_and_background(self):
        view=(JAVA/"OmegaPulseCore.kt").read_text()
        main=(JAVA/"MainActivity.kt").read_text()
        for marker in ("ObjectAnimator", "OmegaMotionPolicy", "onDetachedFromWindow",
                       "onWindowFocusChanged", "ValueAnimator.areAnimatorsEnabled()",
                       "setLayerType(View.LAYER_TYPE_HARDWARE"):
            self.assertIn(marker,view)
        for marker in ("OmegaPulseCore(", "setOnScrollChangeListener",
                       "pauseMotion()", "resumeMotion()", "scrollResumeRunnable"):
            self.assertIn(marker,main)
        self.assertNotIn("Thread.sleep(",view)

    def test_privacy_and_function_buttons_remain_connected(self):
        src=(JAVA/"MainActivity.kt").read_text()
        for marker in ('"BLOQUEAR SESIÓN — PRIVACIDAD"','"PROBAR AUDIO JARVIS"',
                       '"INICIAR VISIÓN DE PANTALLA"','"DETENER VISIÓN DE PANTALLA"',
                       '"HABLAR CON JARVIS"',"WindowManager.LayoutParams.FLAG_SECURE",
                       "SessionLockCoordinator().lock(actions)"):
            self.assertIn(marker,src)
        self.assertIn("OmegaUiKit.quickActions",src)
        self.assertIn("OmegaUiKit.hero",src)

if __name__=="__main__":
    unittest.main()
