"""Verified published wire shapes, without pretending deployed partner APIs exist.

These adapters only interpret inert data from the published Ultra Sentinel v1
schema and GameHub Kotlin UltraToolDescriptor. An authenticated origin and real
runtime integration must be independently established by the host application.
"""
from dataclasses import dataclass
import re


class InvalidPartnerContract(ValueError):
    pass


class UnavailableIntegration(RuntimeError):
    pass


_REPO = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
_SHA = re.compile(r"^[0-9a-f]{40}$")
_DIGEST = re.compile(r"^[0-9a-f]{64}$")
_TOOL = re.compile(r"^[a-z0-9]+(?:[._-][a-z0-9]+)*$")


@dataclass(frozen=True)
class SentinelAdvisory:
    repository: str
    head_sha: str
    verdict: str
    evidence_state: str
    finding_count: int = 0
    authorized_to_merge: bool = False

    @classmethod
    def from_wire(cls, data, *, repository, head_sha):
        # Matches the published Ultra-Sentinel review-result.v1 wire shape.
        required = {"schemaVersion", "targetRepo", "headSha", "verdict", "evidenceState"}
        allowed = required | {"findingCount", "reportDigest"}
        if type(data) is not dict or not required <= data.keys() or data.keys() - allowed:
            raise InvalidPartnerContract("missing or unknown Sentinel fields")
        if type(data["schemaVersion"]) is not int or data["schemaVersion"] != 1:
            raise InvalidPartnerContract("incompatible Sentinel version")
        rep, sha = data["targetRepo"], data["headSha"]
        if (not isinstance(rep, str) or not _REPO.fullmatch(rep)
                or any(part in (".", "..") for part in rep.split("/"))
                or not isinstance(sha, str) or not _SHA.fullmatch(sha)
                or rep != repository or sha != head_sha):
            raise InvalidPartnerContract("Sentinel result is not bound to requested HEAD")
        if (data["verdict"] not in ("ADVISORY", "BLOCKED", "INCOMPLETE")
                or data["evidenceState"] not in ("UNVERIFIED", "INCOMPLETE")):
            raise InvalidPartnerContract("independent Sentinel attestation unavailable")
        count = data.get("findingCount", 0)
        if type(count) is not int or not 0 <= count <= 10000:
            raise InvalidPartnerContract("invalid findingCount")
        digest = data.get("reportDigest")
        if digest is not None and (not isinstance(digest, str) or not _DIGEST.fullmatch(digest)):
            raise InvalidPartnerContract("invalid reportDigest")
        return cls(rep, sha, data["verdict"], data["evidenceState"], count)


@dataclass(frozen=True)
class GameHubToolManifest:
    tool_id: str
    kind: str
    read_only: bool
    can_execute_remotely: bool = False

    @classmethod
    def from_descriptor(cls, descriptor):
        # Matches UltraToolDescriptor.kt. No Android IPC/API is exposed here.
        required = {"id", "kind", "sideEffect", "requiresNetwork"}
        if type(descriptor) is not dict or not {"id", "kind", "sideEffect"} <= descriptor.keys() or descriptor.keys() - required:
            raise InvalidPartnerContract("invalid GameHub descriptor fields")
        ident = descriptor["id"]
        if not isinstance(ident, str) or not _TOOL.fullmatch(ident):
            raise InvalidPartnerContract("invalid GameHub tool id")
        kind = descriptor["kind"]
        if kind not in ("MEMORY", "RESEARCH", "COMMAND", "OPTIMIZER", "TELEMETRY"):
            raise InvalidPartnerContract("unknown GameHub tool kind")
        if descriptor["sideEffect"] != "READ_ONLY":
            raise InvalidPartnerContract("state-mutating GameHub tools require a trusted executor")
        if type(descriptor.get("requiresNetwork", False)) is not bool:
            raise InvalidPartnerContract("invalid requiresNetwork")
        return cls(ident, kind, True)


class AndroidPermissionGate:
    """No Python-side Android executor. Permissions must come from OS checks."""

    def __init__(self, declared_permissions):
        self.declared_permissions = frozenset(declared_permissions)

    def is_allowed(self, permission):
        return False

    def execute(self, request):
        raise UnavailableIntegration("Android executor has not been installed or authorized")
