"""Omega Guardian: append-only audit with external HMAC integrity key."""
import sqlite3
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from jarvis_contracts.integrity_audit import IntegrityAudit, AuditInvalid, AuditTampered

KEY = b"audit-test-key-not-stored-in-database!!"


class IntegrityAuditTests(unittest.TestCase):
    def test_appends_sanitized_records_and_survives_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/audit.db"
            with IntegrityAudit(path, key=KEY) as audit:
                first = audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=1800000000)
                second = audit.record("a"*64, "merge.execute", "DENIED", "c"*64, when=1800000001)
                self.assertEqual(first.sequence, 1)
                self.assertEqual(second.sequence, 2)
                self.assertNotEqual(first.digest, second.digest)
                self.assertTrue(audit.verify())
            with IntegrityAudit(path, key=KEY) as audit:
                self.assertTrue(audit.verify())
                self.assertEqual(len(audit.entries()), 2)

    def test_secret_and_unstructured_fields_are_not_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            with IntegrityAudit(tmp + "/log.db", key=KEY) as audit:
                for args in [
                    ("raw-email@example.com", "pr.read", "SUCCEEDED", "b"*64),
                    ("a"*64, "shell; rm -rf", "DENIED", "b"*64),
                    ("a"*64, "pr.read", "SOMETHING", "b"*64),
                    ("a"*64, "pr.read", "DENIED", "api-key=secret"),
                ]:
                    with self.subTest(args=args), self.assertRaises(AuditInvalid):
                        audit.record(*args, when=1800000000)
                self.assertEqual(audit.entries(), ())

    def test_tampering_with_records_breaks_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/audit.db"
            with IntegrityAudit(path, key=KEY) as audit:
                audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=1800000000)
            with sqlite3.connect(path) as db:
                db.execute("UPDATE audit_entries SET outcome='DENIED' WHERE sequence=1")
            with IntegrityAudit(path, key=KEY) as audit:
                self.assertFalse(audit.verify())
                with self.assertRaises(AuditTampered):
                    audit.entries()
                with self.assertRaises(AuditTampered):
                    audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=1800000001)

    def test_missing_entry_is_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/audit.db"
            with IntegrityAudit(path, key=KEY) as audit:
                audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=1800000000)
                audit.record("a"*64, "pr.read", "SUCCEEDED", "c"*64, when=1800000001)
            with sqlite3.connect(path) as db:
                db.execute("DELETE FROM audit_entries WHERE sequence=1")
            with IntegrityAudit(path, key=KEY) as audit:
                self.assertFalse(audit.verify())

    def test_wrong_integrity_key_cannot_verify(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = tmp + "/audit.db"
            with IntegrityAudit(path, key=KEY) as audit:
                audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=1800000000)
            with IntegrityAudit(path, key=b"x"*32) as audit:
                self.assertFalse(audit.verify())

    def test_parallel_appends_do_not_break_chain(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp) / "audit.db")
            with IntegrityAudit(path, key=KEY):
                pass
            def append(index):
                with IntegrityAudit(path, key=KEY) as audit:
                    return audit.record("a"*64, "pr.read", "SUCCEEDED",
                                        f"{index:064x}", when=1800000000+index).sequence
            with ThreadPoolExecutor(max_workers=8) as pool:
                sequences = list(pool.map(append, range(24)))
            self.assertEqual(sorted(sequences), list(range(1,25)))
            with IntegrityAudit(path, key=KEY) as audit:
                self.assertTrue(audit.verify())

    def test_reject_weak_hmac_key_and_nonfinite_timestamp(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AuditInvalid):
                IntegrityAudit(tmp + "/bad.db", key=b"short")
            with IntegrityAudit(tmp + "/log.db", key=KEY) as audit:
                for when in (float("nan"), float("inf"), -1):
                    with self.subTest(when=when), self.assertRaises(AuditInvalid):
                        audit.record("a"*64, "pr.read", "SUCCEEDED", "b"*64, when=when)
