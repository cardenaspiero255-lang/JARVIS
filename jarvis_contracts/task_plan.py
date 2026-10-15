"""Omega Brain: bounded deterministic dependency planning, simulation only.

No execution capability is provided. Text in a plan is *untrusted data*, never
a permission, instruction to a tool, or implicit authorisation.
"""
from dataclasses import dataclass
from enum import Enum
import re
from types import MappingProxyType


class InvalidPlan(ValueError):
    pass


class ActionRisk(str, Enum):
    READ_ONLY = "read-only"
    REQUIRES_APPROVAL = "requires-approval"
    BLOCKED = "blocked"


@dataclass(frozen=True)
class PlannedStep:
    identifier: str
    action: str
    dependencies: tuple[str, ...]


@dataclass(frozen=True)
class PlanPreview:
    steps: tuple[PlannedStep, ...]
    risks: object

    @property
    def read_only_count(self) -> int:
        return sum(value is ActionRisk.READ_ONLY for value in self.risks.values())

    @property
    def approval_count(self) -> int:
        return sum(value is ActionRisk.REQUIRES_APPROVAL for value in self.risks.values())

    @property
    def blocked_count(self) -> int:
        return sum(value is ActionRisk.BLOCKED for value in self.risks.values())

    @property
    def can_execute(self) -> bool:
        # Even a read-only plan must be dispatched by a separate authorised
        # executor that verifies source identity, capabilities and deadlines.
        return False


class PlanCompiler:
    _IDENTIFIER = re.compile(r"[a-z][a-z0-9_-]{0,47}")
    _ACTION = re.compile(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){1,3}")
    READ_ONLY = frozenset({
        "status.read", "pull_request.read", "memory.lookup", "device.status",
        "checks.read",
    })
    NEEDS_APPROVAL = frozenset({
        "merge.execute", "payment.submit", "message.send",
        "review.requested", "repository.delete",
        "game.launch", "network.optimize",
    })

    @classmethod
    def _parse_step(cls, raw):
        if (type(raw) is not dict or set(raw) != {"id", "action", "dependsOn"}):
            raise InvalidPlan("missing or unknown plan step fields")
        ident, action, dependencies = raw["id"], raw["action"], raw["dependsOn"]
        if (type(ident) is not str or cls._IDENTIFIER.fullmatch(ident) is None
                or type(action) is not str or len(action) > 64 or cls._ACTION.fullmatch(action) is None
                or type(dependencies) is not list or len(dependencies) > 50
                or any(type(d) is not str or cls._IDENTIFIER.fullmatch(d) is None
                       for d in dependencies)
                or len(dependencies) != len(set(dependencies))
                or ident in dependencies):
            raise InvalidPlan("invalid step identifier, action or dependencies")
        return PlannedStep(ident, action, tuple(dependencies))

    @classmethod
    def _risk(cls, action):
        if action in cls.READ_ONLY:
            return ActionRisk.READ_ONLY
        if action in cls.NEEDS_APPROVAL:
            return ActionRisk.REQUIRES_APPROVAL
        return ActionRisk.BLOCKED

    def compile(self, raw):
        if (type(raw) is not dict or set(raw) != {"steps"}
                or type(raw["steps"]) is not list
                or not 1 <= len(raw["steps"]) <= 50):
            raise InvalidPlan("plan must contain 1 to 50 steps")
        parsed = [self._parse_step(step) for step in raw["steps"]]
        indexed = {step.identifier: step for step in parsed}
        if len(indexed) != len(parsed):
            raise InvalidPlan("duplicate step ids")
        if any(dep not in indexed for step in parsed for dep in step.dependencies):
            raise InvalidPlan("missing referenced dependency")

        # Kahn's algorithm: stable id-order tie-break. No recursion or
        # side-effectful handler invocation during plan evaluation.
        pending = set(indexed)
        completed = set()
        ordered = []
        while pending:
            ready = sorted(i for i in pending
                           if set(indexed[i].dependencies) <= completed)
            if not ready:
                raise InvalidPlan("cyclic dependencies")
            for identifier in ready:
                ordered.append(indexed[identifier])
                completed.add(identifier)
                pending.remove(identifier)
        risks = MappingProxyType({step.identifier: self._risk(step.action)
                                  for step in ordered})
        return PlanPreview(tuple(ordered), risks)
