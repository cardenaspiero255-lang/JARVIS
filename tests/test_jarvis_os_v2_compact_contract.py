"""Regression contracts for user-operated, reversible JARVIS OS V2."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android/app"
JAVA = ANDROID / "src/main/java/com/cardenaspiero255/jarvis"


class JarvisOsV2CompactContract(unittest.TestCase):
    def test_four_module_hub_preserves_android_default_home(self):
        home = (JAVA / "JarvisHomeActivity.kt").read_text(encoding="utf-8")
        manifest = (ANDROID / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        modules = re.findall(r'private const val MODULE_\w+ = "(JARVIS|APPS|GAMING|SISTEMA)"', home)
        self.assertEqual(modules, ["JARVIS", "APPS", "GAMING", "SISTEMA"])
        self.assertIn("modules.forEach", home)
        self.assertIn("panel.visibility = if (key == name) View.VISIBLE else View.GONE", home)
        self.assertIn("if (name == MODULE_APPS", home)
        self.assertIn("if (activeModule == MODULE_APPS", home)
        self.assertIn("Settings.ACTION_HOME_SETTINGS", home)
        self.assertIn("android.intent.category.HOME", manifest)
        self.assertNotIn("setPreferredActivity(", home)

    def test_drawer_does_not_render_sixty_buttons_on_start(self):
        home = (JAVA / "JarvisHomeActivity.kt").read_text(encoding="utf-8")
        self.assertIn("val maxVisible = if (query.isBlank() && !showAllApps) 12 else 60", home)
        self.assertIn('action("VER TODAS LAS APLICACIONES")', home)
        self.assertIn("full.size > maxVisible && query.isBlank() && !showAllApps", home)
        self.assertIn("JarvisHomeCatalog.visible(installedApps, query, packageName, 200)", home)

    def test_original_assistant_callbacks_preserved_behind_opt_in_groups(self):
        main = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
        panels = (JAVA / "OmegaCompactPanels.kt").read_text(encoding="utf-8")
        expected_groups = (
            "CHAT Y MEMORIA", "JUEGOS Y RENDIMIENTO",
            "VISIÓN Y PANTALLA", "SISTEMA Y PRIVACIDAD",
        )
        for label in expected_groups:
            self.assertIn(f'compactPanels.addGroup("{label}"', main) if label != "SISTEMA Y PRIVACIDAD" else self.assertIn('"SISTEMA Y PRIVACIDAD"', main)
        self.assertIn("group.content.visibility = if (selected) View.VISIBLE else View.GONE", panels)
        self.assertIn("visibility = View.GONE", panels)
        self.assertIn("setMinimumHeight(dp(56))", panels)
        self.assertNotIn("minHeight = dp(56)", panels)
        # Deep-linked gaming panel must be shown after ScrollView exists, so
        # its panel can actually scroll into view rather than only be VISIBLE.
        self.assertIn('omegaScroll.post { compactPanels.show(requestedPanel) }', main)
        self.assertIn("views.forEach { view ->", panels)
        self.assertIn("heading.setOnClickListener", panels)
        # All formerly exposed actions must still exist and retain explicit handlers.
        required = (
            "ENVIAR MENSAJE", "BORRAR HISTORIAL Y CLAVE CIFRADA",
            "ACTIVAR MEMORIA PRIVADA CIFRADA",
            "PERMITIR CAPTURAS 60 S — DEPURACIÓN",
            "BLOQUEAR SESIÓN — PRIVACIDAD", "HABLAR CON JARVIS",
            "INICIAR VISIÓN DE PANTALLA",
            "PARADA DE EMERGENCIA HANDS",
            "CONFIRMAR APERTURA SEGURA DE GAMEHUB",
            "CONFIRMAR AJUSTES SOLICITADOS POR VOZ",
            "PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH",
        )
        for name in required:
            self.assertIn(f'primaryButton("{name}")', main)
        self.assertEqual(37, len(re.findall(r'primaryButton\("', main)))

    def test_no_new_launcher_privilege_or_automatic_sensitive_actions(self):
        home = (JAVA / "JarvisHomeActivity.kt").read_text(encoding="utf-8")
        panels = (JAVA / "OmegaCompactPanels.kt").read_text(encoding="utf-8")
        manifest = (ANDROID / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        gradle = (ANDROID / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationIdSuffix = ".osv2preview"', gradle)
        self.assertNotIn('applicationIdSuffix = ".ospreview"', gradle)
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS OS V2 PREVIEW")', gradle)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", manifest)
        for content in (home, panels):
            for forbidden in (
                "Runtime.getRuntime()", "ProcessBuilder(", "su -c",
                "fastboot", "addPersistentPreferredActivity(", "performGlobalAction(",
                "requestPermissions(", "setPreferredActivity(",
            ):
                self.assertNotIn(forbidden, content)


if __name__ == "__main__":
    unittest.main()
