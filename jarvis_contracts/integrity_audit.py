"""Omega Guardian: minimally disclosed HMAC-linked action audit records.

External trusted code owns the HMAC key, principal identification, and
authorization. This records facts supplied by the caller; it cannot prove
that an action actually happened. Keys are never written to the database.
"""
from dataclasses import dataclass
import hashlib
import hmac
import json
import math
import re
import sqlite3


class AuditInvalid(ValueError):
    pass


class AuditTampered(AuditInvalid):
    pass


_HEX = re.compile(r"[0-9a-f]{64}")
_ACTION = re.compile(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){1,3}")
_OUTCOMES = frozenset({"STARTED", "SUCCEEDED", "FAILED", "DENIED", "UNCERTAIN"})
_GENESIS = "0" * 64


@dataclass(frozen=True)
class AuditEntry:
    sequence: int
    principal_digest: str
    action: str
    outcome: str
    resource_digest: str
    occurred_at: float
    previous_digest: str
    digest: str


class IntegrityAudit:
    """Append-only audit chain with external integrity key and SQLite locks."""

    def __init__(self, path, *, key: bytes):
        if type(key) is not bytes or len(key) < 32:
            raise AuditInvalid("strong externally managed integrity key required")
        self._key = key
        self.db = sqlite3.connect(path, timeout=10, isolation_level=None)
        try:
            self.db.execute("PRAGMA busy_timeout=10000")
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.execute("""CREATE TABLE IF NOT EXISTS audit_entries (
                sequence INTEGER PRIMARY KEY,
                principal_digest TEXT NOT NULL,
                action TEXT NOT NULL,
                outcome TEXT NOT NULL,
                resource_digest TEXT NOT NULL,
                occurred_at REAL NOT NULL,
                previous_digest TEXT NOT NULL,
                digest TEXT NOT NULL
            )""")
        except BaseException:
            self.db.close()
            raise

    @staticmethod
    def _canonical(entry: AuditEntry) -> bytes:
        return json.dumps({
            "sequence": entry.sequence,
            "principal_digest": entry.principal_digest,
            "action": entry.action,
            "outcome": entry.outcome,
            "resource_digest": entry.resource_digest,
            "occurred_at": entry.occurred_at,
            "previous_digest": entry.previous_digest,
        }, allow_nan=False, separators=(",", ":"), sort_keys=True).encode("utf-8")

    def _sign(self, entry: AuditEntry) -> str:
        return hmac.new(self._key, self._canonical(entry), hashlib.sha256).hexdigest()

    @staticmethod
    def _row(raw):
        return AuditEntry(*raw)

    def _rows(self):
        return [self._row(r) for r in self.db.execute(
            "SELECT sequence, principal_digest, action, outcome, "
            "resource_digest, occurred_at, previous_digest, digest "
            "FROM audit_entries ORDER BY sequence ASC"
        ).fetchall()]

    def _verify_unlocked(self, rows):
        previous = _GENESIS
        expected = 1
        for record in rows:
            if (record.sequence != expected
                    or not hmac.compare_digest(record.previous_digest, previous)
                    or not hmac.compare_digest(record.digest, self._sign(record))):
                return False
            previous = record.digest
            expected += 1
        return True

    def verify(self):
        self.db.execute("BEGIN")
        try:
            result = self._verify_unlocked(self._rows())
            self.db.execute("COMMIT")
            return result
        except BaseException:
            self.db.execute("ROLLBACK")
            raise

    def entries(self):
        return tuple(self._rows())

    def record(self, principal_digest, action, outcome, resource_digest, *, when):
        if (type(principal_digest) is not str or not _HEX.fullmatch(principal_digest)
                or type(resource_digest) is not str or not _HEX.fullmatch(resource_digest)
                or type(action) is not str or len(action) > 64
                or _ACTION.fullmatch(action) is None
                or type(outcome) is not str or outcome not in _OUTCOMES
                or type(when) not in (int, float) or not math.isfinite(when)
                or when <= 0):
            raise AuditInvalid("only bounded, hashed audit metadata allowed")
        when = float(when)
        self.db.execute("BEGIN IMMEDIATE")
        try:
            records = self._rows()
            if not self._verify_unlocked(records):
                raise AuditTampered("audit history failed integrity verification")
            sequence = len(records) + 1
            previous = records[-1].digest if records else _GENESIS
            unsigned = AuditEntry(sequence, principal_digest, action, outcome,
                                  resource_digest, when, previous, "")
            new = AuditEntry(sequence, principal_digest, action, outcome,
                             resource_digest, when, previous, self._sign(unsigned))
            self.db.execute("INSERT INTO audit_entries VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            (new.sequence, new.principal_digest, new.action,
                             new.outcome, new.resource_digest, new.occurred_at,
                             new.previous_digest, new.digest))
            self.db.execute("COMMIT")
            return new
        except BaseException:
            self.db.execute("ROLLBACK")
            raise

    def close(self):
        self.db.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
