"""Ensure all activation instructions point to files inside the repository."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HandsDocumentationLinks(unittest.TestCase):
    def test_local_hands_links_resolve_inside_repository(self):
        doc = ROOT / "docs/ANDROID_MVP.md"
        body = doc.read_text(encoding="utf-8")
        links = re.findall(r"\[README-HANDS\]\(([^)]+)\)", body)
        self.assertTrue(links, "Hands activation guide must be discoverable")
        for link in links:
            target = (doc.parent / link).resolve()
            self.assertTrue(target.is_relative_to(ROOT.resolve()), link)
            self.assertTrue(target.is_file(), link)
