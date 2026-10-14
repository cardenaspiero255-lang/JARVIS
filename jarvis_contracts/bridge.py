"""In-process reference bridge for contract-tested providers.

No network, Android automation or production authorization is implemented.
"""
from datetime import datetime
from typing import Callable, Mapping

from .integration import CapabilityRegistry
from .protocol import Envelope, InMemoryGateway


class ApprovalRequired(ValueError):
    pass


class ProviderFailure(RuntimeError):
    pass


class ProviderBridge:
    SENSITIVE_ACTIONS = frozenset({"merge.execute", "repository.delete", "payment.submit"})

    def __init__(
        self,
        registry: CapabilityRegistry,
        handlers: Mapping[str, Callable[[Envelope], object]],
        approved_actions: frozenset[str] = frozenset(),
    ):
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

    def dispatch(self, message: Mapping[str, object], now: datetime) -> object:
        envelope = Envelope.parse(message)
        self.registry.require(envelope.target, envelope.schema_version, envelope.type)
        if envelope.type in self.SENSITIVE_ACTIONS:
            raise ApprovalRequired("explicit authorization required")
        return self.gateway.dispatch(message, now)
