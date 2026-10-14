"""Reference implementation of a strict versioned command envelope.

Not an Android executor, network transport, or authorization server.
"""
from dataclasses import dataclass
import json
from datetime import datetime, timezone
from typing import Callable, Mapping
from uuid import UUID


class ContractError(ValueError):
    pass


class UnsupportedVersion(ContractError):
    pass


class PermissionDenied(ContractError):
    pass


def _parse_time(value: str) -> datetime:
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (ValueError, AttributeError) as exc:
        raise ContractError("invalid timestamp") from exc
    if dt.tzinfo is None or dt.utcoffset() is None:
        raise ContractError("timestamp must include timezone")
    return dt.astimezone(timezone.utc)


@dataclass(frozen=True)
class Envelope:
    schema_version: str
    message_id: str
    correlation_id: str
    timestamp: str
    source: str
    target: str
    type: str
    idempotency_key: str
    deadline: str
    payload: Mapping[str, object]

    @classmethod
    def parse(cls, raw: Mapping[str, object]) -> "Envelope":
        required = {"schemaVersion", "messageId", "correlationId", "timestamp", "source", "target", "type", "idempotencyKey", "deadline", "payload"}
        allowed = required | {"causationId"}
        if not isinstance(raw, dict) or required - raw.keys() or raw.keys() - allowed:
            raise ContractError("missing or unknown envelope fields")
        version = raw["schemaVersion"]
        if not isinstance(version, str) or not version.startswith("1.") or not version[2:].isdigit():
            raise UnsupportedVersion("only 1.x schema versions are supported")
        for name in ("messageId", "correlationId"):
            try:
                UUID(str(raw[name]))
            except (ValueError, AttributeError, TypeError) as exc:
                raise ContractError(f"invalid {name}") from exc
        for name in ("source", "target", "type", "idempotencyKey"):
            if not isinstance(raw[name], str) or not raw[name].strip():
                raise ContractError(f"invalid {name}")
        if not isinstance(raw["payload"], dict):
            raise ContractError("payload must be an object")
        if "causationId" in raw and raw["causationId"] is not None and not isinstance(raw["causationId"], str):
            raise ContractError("invalid causationId")
        if _parse_time(str(raw["deadline"])) <= _parse_time(str(raw["timestamp"])):
            raise ContractError("deadline must be after timestamp")
        return cls(*(raw[k] for k in ("schemaVersion", "messageId", "correlationId", "timestamp", "source", "target", "type", "idempotencyKey", "deadline", "payload")))


class InMemoryGateway:
    """Fail-closed test adapter: allowlisted targets/actions and deduplicated commands."""
    def __init__(self, allowed: Mapping[str, set[str]], handlers: Mapping[str, Callable[[Envelope], object]]):
        self.allowed = allowed
        self.handlers = handlers
        self._completed: dict[tuple[str, str], tuple[str, object]] = {}

    def dispatch(self, message: Mapping[str, object], now: datetime) -> object:
        envelope = Envelope.parse(message)
        if now.tzinfo is None or now.utcoffset() is None:
            raise ContractError("now must include timezone")
        if now.astimezone(timezone.utc) >= _parse_time(envelope.deadline):
            raise ContractError("expired command")
        if envelope.type not in self.allowed.get(envelope.target, set()):
            raise PermissionDenied("capability not authorized")
        key = (envelope.source, envelope.idempotency_key)
        fingerprint = json.dumps({"target": envelope.target, "type": envelope.type, "payload": envelope.payload}, sort_keys=True, separators=(",", ":"))
        if key in self._completed:
            saved_fingerprint, saved_result = self._completed[key]
            if saved_fingerprint != fingerprint:
                raise ContractError("idempotency key reused for different command")
            return saved_result
        handler = self.handlers.get(envelope.target)
        if handler is None:
            raise ContractError("target unavailable")
        result = handler(envelope)
        self._completed[key] = (fingerprint, result)
        return result
