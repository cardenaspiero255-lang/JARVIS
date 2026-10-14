"""In-process reference bridge for contract-tested providers.

No network, Android automation or production authorization is implemented.
"""
from datetime import datetime, timezone
import hashlib
import json

from .authorization import ApprovalDenied
from typing import Callable, Mapping

from .integration import CapabilityRegistry
from .protocol import Envelope, InMemoryGateway, ContractError, _parse_time


class ApprovalRequired(ValueError):
    pass


class ProviderFailure(RuntimeError):
    pass


class ProviderBridge:
    # Anything not explicitly classified as a low-risk request requires
    # specific, one-time consent. Unknown operations always fail closed.
    SAFE_AUTOMATIC_ACTIONS = frozenset({"review.requested", "status.read", "telemetry.read", "pull_request.read"})
    SENSITIVE_ACTIONS = frozenset({"merge.execute", "repository.delete", "payment.submit"})

    def __init__(
        self,
        registry: CapabilityRegistry,
        handlers: Mapping[str, Callable[[Envelope], object]],
        approved_actions: frozenset[str] = frozenset(),
        durable_store=None,
        approval_store=None,
    ):
        self.durable_store = durable_store
        self.approval_store = approval_store
        self.registry = registry
        self.handlers = handlers
        # A global action allowlist is not proof of approval for a specific request.
        self.approved_actions = approved_actions
        self.gateway = InMemoryGateway(
            {name: set(provider[1]) for name, provider in registry.providers.items()},
            {name: self._safe_handler(handler) for name, handler in handlers.items()},
        )

    @staticmethod
    def _safe_handler(handler: Callable[[Envelope], object]) -> Callable[[Envelope], object]:
        def invoke(envelope: Envelope) -> object:
            try:
                return handler(envelope)
            except Exception as exc:
                raise ProviderFailure("provider execution failed") from exc
        return invoke

    @staticmethod
    def fingerprint(message):
        envelope = Envelope.parse(message)
        body = {"target": envelope.target, "type": envelope.type,
                "payload": envelope.payload, "version": envelope.schema_version}
        return hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()

    def dispatch(self, message: Mapping[str, object], now: datetime) -> object:
        envelope = Envelope.parse(message)
        # Validate deadline before consuming approval or writing durable state.
        if now.tzinfo is None or now.utcoffset() is None:
            raise ContractError("now must include timezone")
        if now.astimezone(timezone.utc) >= _parse_time(envelope.deadline):
            raise ContractError("expired command")
        self.registry.require(envelope.target, envelope.schema_version, envelope.type)
        fingerprint = self.fingerprint(message)
        if envelope.type not in self.SAFE_AUTOMATIC_ACTIONS:
            if self.approval_store is None:
                raise ApprovalRequired("explicit authorization required")
            try:
                self.approval_store.consume(envelope.source, envelope.target, envelope.type,
                                            fingerprint, now.timestamp())
            except ApprovalDenied as exc:
                raise ApprovalRequired("explicit authorization required") from exc
        if self.durable_store is None:
            return self.gateway.dispatch(message, now)
        previous = self.durable_store.status(envelope.source, envelope.idempotency_key)
        cached = self.durable_store.get(envelope.source, envelope.idempotency_key, fingerprint)
        if previous == "completed":
            return cached
        if previous is not None:
            raise ProviderFailure("operation outcome uncertain; manual reconciliation required")
        # Record before dispatch. Failure leaves uncertain status, preventing unsafe replay.
        if not self.durable_store.claim(envelope.source, envelope.idempotency_key, fingerprint):
            raise ProviderFailure("operation already claimed; manual reconciliation required")
        result = self.gateway.dispatch(message, now)
        self.durable_store.complete(envelope.source, envelope.idempotency_key, fingerprint, result)
        return result
