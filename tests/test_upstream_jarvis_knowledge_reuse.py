"""Regression contracts for reusing permissive upstream Jarvis BM25 without unsafe imports."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class UpstreamKnowledgeReuseContract(unittest.TestCase):
    def test_jarvis_offline_pack_has_explicit_bm25_provenance(self):
        engine = (SRC / "core/PrivateKnowledgePack.kt").read_text()
        self.assertIn("class PrivateKnowledgePack", engine)
        self.assertIn("BM25", engine)
        self.assertIn("kotlin.math.ln", engine)
        self.assertIn("fun clear()", engine)

    def test_existing_chat_invokes_local_pack_only_for_explicit_queries(self):
        dialogue = (SRC / "core/LocalDialogue.kt").read_text()
        self.assertIn("fun importLocalNote(", dialogue)
        self.assertIn("privateKnowledge.search(", dialogue)
        self.assertIn("privateKnowledge.clear()", dialogue)
        voice = dialogue.split("fun replyVoice(",1)[1].split("fun history()",1)[0]
        self.assertNotIn("privateKnowledge.search(", voice)

    def test_android_saf_local_txt_and_session_privacy(self):
        main = (SRC / "MainActivity.kt").read_text()
        self.assertIn("IMPORTAR NOTA LOCAL", main)
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", main)
        self.assertIn("NOTE_PICKER_REQUEST", main)
        self.assertIn("contentResolver.openInputStream", main)
        self.assertIn("LOCAL_CHAT to", main)
        self.assertNotIn("takePersistableUriPermission(", main)
        manifest = (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        for permission in ("READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_AUDIO"):
            self.assertNotIn(permission, manifest)

    def test_upstream_mit_attribution_not_decompiled_claim(self):
        doc = (ROOT / "docs/UPSTREAM_JARVIS_COMPONENT_REUSE_20261020.md").read_text()
        self.assertIn("alwin123098/jarvis-android", doc)
        self.assertIn("judejulius/jarvis", doc)
        self.assertIn("Bwarhness/jarvis-assistant", doc)
        self.assertIn("REA NO EJECUTADO", doc)
        notice = (ROOT / "apps/android/app/src/main/assets/legal/CODOTYPE_JARVIS_MIT_NOTICE.txt").read_text()
        self.assertIn("Copyright (c) 2026 Codotype", notice)
        self.assertIn("MIT License", notice)

if __name__ == "__main__":
    unittest.main()
