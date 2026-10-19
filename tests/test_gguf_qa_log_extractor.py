"""Regression: AndroidTest uninstalls debug app before run-as can read files."""
import json
import tempfile
import unittest
from pathlib import Path

from tools.extract_gguf_qa_log import extract, LOG_FILE_NAME


class GgufAndroidTestExtraction(unittest.TestCase):
    def test_reads_exact_testcase_log_without_package_or_run_as(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = {"schema_version": 1, "model": {"sha256": "abc"}}
            (root / LOG_FILE_NAME).write_text(
                "05:46:57 I System.out: GGUF_QA_JSON_V1=" +
                json.dumps(data, separators=(",", ":")) + "\n")
            self.assertEqual(data, extract(root))

    def test_missing_or_multiple_log_records_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(ValueError):
                extract(root)
            one = root / LOG_FILE_NAME
            one.write_text("No reported measurement\n")
            with self.assertRaises(ValueError):
                extract(root)
            one.write_text("I System.out: GGUF_QA_JSON_V1={}\n" * 2)
            with self.assertRaises(ValueError):
                extract(root)

    def test_invalid_or_truncated_log_record_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            log = root / LOG_FILE_NAME
            log.write_text("I System.out: GGUF_QA_JSON_V1={broken\n")
            with self.assertRaises(ValueError):
                extract(root)
            log.write_text("I System.out: GGUF_QA_JSON_V1={}\n")
            with self.assertRaises(ValueError):
                extract(root)

    def test_refuses_duplicate_testcase_logs(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for segment in ("deviceA", "deviceB"):
                (root / segment).mkdir()
                (root / segment / LOG_FILE_NAME).write_text(
                    'I System.out: GGUF_QA_JSON_V1={"schema_version":1}\n')
            with self.assertRaises(ValueError):
                extract(root)


if __name__ == "__main__":
    unittest.main()
