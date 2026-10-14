"""Omega Memory: expiring, minimal, verified error-family evidence.

Stores only identifiers and digests: never raw prompts, stack traces, secrets,
voice recordings, or inferred claims of a working fix. Trusted caller supplies
a verifier; this module neither authenticates GitHub nor executes repairs.
"""
from dataclasses import dataclass
import math
import re

from .persistence import DurableStore


class UnverifiedResolution(PermissionError):
    pass


class ConflictingResolution(ValueError):
    pass


_FAMILY = re.compile(r"[A-Z][A-Z0-9_]{2,63}")
_SHA = re.compile(r"[a-f0-9]{40}")
_DIGEST = re.compile(r"[a-f0-9]{64}")
_MAX_TTL = 90 * 86400


def _finite(value):
    return type(value) in (int, float) and math.isfinite(value)


@dataclass(frozen=True)
class ErrorEvidence:
    family: str
    variant_fingerprint: str
    fix_sha: str
    evidence_digest: str
    expires_at: float


class VerifiedErrorMemory:
    """Persistence of verified fix references, not autonomous learning."""

    def __init__(self, path, *, verifier):
        if not callable(verifier):
            raise ValueError("trusted evidence verifier required")
        self._verifier = verifier
        self._journal = DurableStore(path)
        self._db = self._journal.db
        self._db.execute("""CREATE TABLE IF NOT EXISTS error_knowledge (
            family TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            fix_sha TEXT NOT NULL,
            evidence_digest TEXT NOT NULL,
            expires_at REAL NOT NULL,
            PRIMARY KEY (family, fingerprint)
        )""")

    @staticmethod
    def _validate_key(family, fingerprint):
        if (type(family) is not str or not _FAMILY.fullmatch(family)
                or type(fingerprint) is not str or not _DIGEST.fullmatch(fingerprint)):
            raise ValueError("invalid structured error family or fingerprint")

    @staticmethod
    def _read(row):
        return ErrorEvidence(*row) if row is not None else None

    def remember(self, family, variant_fingerprint, fix_sha, evidence_digest,
                 *, now, expires_at):
        self._validate_key(family, variant_fingerprint)
        if (type(fix_sha) is not str or not _SHA.fullmatch(fix_sha)
                or type(evidence_digest) is not str
                or not _DIGEST.fullmatch(evidence_digest)
                or not _finite(now) or not _finite(expires_at)
                or now <= 0 or not now < expires_at <= now + _MAX_TTL):
            raise ValueError("invalid proof reference or expiration")
        evidence = ErrorEvidence(
            family, variant_fingerprint, fix_sha, evidence_digest, expires_at
        )
        # The verifier is external and must inspect trusted attestation
        # over this exact evidence, not caller-supplied "verified=true".
        try:
            approved = self._verifier({
                "family": family, "variant_fingerprint": variant_fingerprint,
                "fix_sha": fix_sha, "evidence_digest": evidence_digest,
                "expires_at": expires_at,
            }) is True
        except Exception as exc:
            raise UnverifiedResolution("evidence authority unavailable") from exc
        if not approved:
            raise UnverifiedResolution("fix evidence not verified")
        self._db.execute("BEGIN IMMEDIATE")
        try:
            self._db.execute("DELETE FROM error_knowledge WHERE expires_at <= ?", (now,))
            row = self._db.execute(
                "SELECT family, fingerprint, fix_sha, evidence_digest, expires_at "
                "FROM error_knowledge WHERE family = ? AND fingerprint = ?",
                (family, variant_fingerprint),
            ).fetchone()
            if row is not None:
                old = self._read(row)
                if old.fix_sha != fix_sha or old.evidence_digest != evidence_digest:
                    raise ConflictingResolution("verified evidence cannot be overwritten")
                evidence = old  # Never extend TTL via a duplicate report.
            else:
                self._db.execute(
                    "INSERT INTO error_knowledge VALUES (?, ?, ?, ?, ?)",
                    (family, variant_fingerprint, fix_sha, evidence_digest, expires_at),
                )
            self._db.execute("COMMIT")
            return evidence
        except BaseException:
            self._db.execute("ROLLBACK")
            raise

    def lookup(self, family, variant_fingerprint, *, now):
        self._validate_key(family, variant_fingerprint)
        if not _finite(now) or now <= 0:
            raise ValueError("finite trusted clock required")
        row = self._db.execute(
            "SELECT family, fingerprint, fix_sha, evidence_digest, expires_at "
            "FROM error_knowledge "
            "WHERE family = ? AND fingerprint = ? AND expires_at > ?",
            (family, variant_fingerprint, now),
        ).fetchone()
        return self._read(row)

    def erase(self, family, variant_fingerprint):
        self._validate_key(family, variant_fingerprint)
        self._db.execute(
            "DELETE FROM error_knowledge WHERE family = ? AND fingerprint = ?",
            (family, variant_fingerprint),
        )

    def close(self):
        self._journal.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
