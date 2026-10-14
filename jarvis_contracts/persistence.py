"""SQLite-backed command journal. Unfinished operations are never replayed automatically."""
import json
import sqlite3


class IdempotencyConflict(ValueError):
    pass


class DurableStore:
    def __init__(self, path):
        self.db = sqlite3.connect(path, timeout=10, isolation_level=None)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("""CREATE TABLE IF NOT EXISTS commands (
            source TEXT NOT NULL, key TEXT NOT NULL, fingerprint TEXT NOT NULL,
            status TEXT NOT NULL, result TEXT, PRIMARY KEY(source, key))""")

    def claim(self, source, key, fingerprint):
        """Atomically reserve a command across processes and SQLite connections."""
        with self.db:
            cursor = self.db.execute(
                "INSERT OR IGNORE INTO commands VALUES (?, ?, ?, 'uncertain', NULL)",
                (source, key, fingerprint))
            self._check(source, key, fingerprint)
            return cursor.rowcount == 1

    def begin(self, source, key, fingerprint):
        with self.db:
            self.db.execute("INSERT OR IGNORE INTO commands VALUES (?, ?, ?, 'uncertain', NULL)",
                            (source, key, fingerprint))
            self._check(source, key, fingerprint)

    def _check(self, source, key, fingerprint):
        row = self.db.execute("SELECT fingerprint, status, result FROM commands WHERE source=? AND key=?",
                              (source, key)).fetchone()
        if row and row[0] != fingerprint:
            raise IdempotencyConflict("idempotency key conflict")
        return row

    def get(self, source, key, fingerprint):
        row = self._check(source, key, fingerprint)
        return json.loads(row[2]) if row and row[1] == "completed" else None

    def status(self, source, key):
        row = self.db.execute("SELECT status FROM commands WHERE source=? AND key=?", (source, key)).fetchone()
        return row[0] if row else None

    def complete(self, source, key, fingerprint, result):
        """Commit an outcome once; never overwrite completed evidence."""
        encoded = json.dumps(result, allow_nan=False)
        self.db.execute("BEGIN IMMEDIATE")
        try:
            existing = self._check(source, key, fingerprint)
            if existing is not None and existing[1] == "completed":
                raise IdempotencyConflict("completed outcome is immutable")
            if existing is None:
                self.db.execute(
                    "INSERT INTO commands VALUES (?, ?, ?, 'uncertain', NULL)",
                    (source, key, fingerprint))
            updated = self.db.execute(
                "UPDATE commands SET status='completed', result=? "
                "WHERE source=? AND key=? AND fingerprint=? AND status='uncertain'",
                (encoded, source, key, fingerprint))
            if updated.rowcount != 1:
                raise IdempotencyConflict("cannot complete a non-pending command")
            self.db.execute("COMMIT")
        except BaseException:
            self.db.execute("ROLLBACK")
            raise

    def close(self):
        self.db.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
