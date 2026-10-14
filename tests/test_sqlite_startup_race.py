"""SQLite startup race regression: concurrent first-open WAL transitions."""
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from jarvis_contracts.persistence import DurableStore


class SQLiteStartupRegression(unittest.TestCase):
    def test_many_initial_connections_do_not_fail_with_database_locked(self):
        with tempfile.TemporaryDirectory() as tmp:
            for round_id in range(3):
                path = str(Path(tmp) / f"round-{round_id}.db")
                barrier = threading.Barrier(8)

                def initialize(_index):
                    barrier.wait(timeout=5)
                    with DurableStore(path) as store:
                        return store.db.execute(
                            "SELECT COUNT(*) FROM sqlite_master WHERE name='commands'"
                        ).fetchone()[0]

                with ThreadPoolExecutor(max_workers=8) as pool:
                    rows = list(pool.map(initialize, range(8)))
                self.assertEqual(rows, [1] * 8)
