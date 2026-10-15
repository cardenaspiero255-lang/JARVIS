"""Regression tests for inherited risk and immutable root-cause attribution."""
import unittest

from jarvis_contracts.task_plan import ActionRisk, PlanCompiler


class TransitivePlanRiskTests(unittest.TestCase):
    def setUp(self):
        self.compiler = PlanCompiler()

    def compile(self, *steps):
        return self.compiler.compile({"steps": list(steps)})

    def step(self, identifier, action, *dependencies):
        return {"id": identifier, "action": action, "dependsOn": list(dependencies)}

    def test_unknown_action_blocks_all_descendants_not_independent_siblings(self):
        preview = self.compile(
            self.step("final", "status.read", "middle"),
            self.step("unrelated", "checks.read"),
            self.step("middle", "pull_request.read", "unknown"),
            self.step("unknown", "untrusted.execute"),
        )
        self.assertEqual(preview.risks["unknown"], ActionRisk.BLOCKED)
        self.assertEqual(preview.risks["middle"], ActionRisk.BLOCKED)
        self.assertEqual(preview.risks["final"], ActionRisk.BLOCKED)
        self.assertEqual(preview.risks["unrelated"], ActionRisk.READ_ONLY)
        self.assertEqual(preview.direct_risks["final"], ActionRisk.READ_ONLY)
        self.assertEqual(preview.risk_origins["final"], ("unknown",))
        self.assertEqual(preview.risk_origins["unrelated"], ())
        self.assertEqual(preview.blocked_count, 3)
        self.assertFalse(preview.can_execute)

    def test_approval_requirement_cascades_through_multiple_read_steps(self):
        preview = self.compile(
            self.step("inspect", "checks.read", "followup"),
            self.step("followup", "status.read", "mutation"),
            self.step("mutation", "merge.execute"),
        )
        self.assertEqual(preview.risks["followup"], ActionRisk.REQUIRES_APPROVAL)
        self.assertEqual(preview.risks["inspect"], ActionRisk.REQUIRES_APPROVAL)
        self.assertEqual(preview.risk_origins["inspect"], ("mutation",))
        self.assertEqual(preview.approval_count, 3)

    def test_blocked_dependency_overrides_direct_approval_and_multiple_causes(self):
        preview = self.compile(
            self.step("blocked1", "shell.execute"),
            self.step("blocked2", "ui.tap"),
            self.step("final", "payment.submit", "blocked2", "blocked1"),
        )
        self.assertEqual(preview.direct_risks["final"], ActionRisk.REQUIRES_APPROVAL)
        self.assertEqual(preview.risks["final"], ActionRisk.BLOCKED)
        self.assertEqual(preview.risk_origins["final"], ("blocked1", "blocked2"))

    def test_risk_attribution_is_read_only_and_plan_never_executes(self):
        preview = self.compile(self.step("inspect", "status.read"))
        with self.assertRaises(TypeError):
            preview.risks["inspect"] = ActionRisk.BLOCKED
        with self.assertRaises(TypeError):
            preview.risk_origins["inspect"] = ("fake",)
        with self.assertRaises(TypeError):
            preview.direct_risks["inspect"] = ActionRisk.BLOCKED
        self.assertEqual(preview.risk_origins["inspect"], ())
        self.assertFalse(preview.can_execute)


if __name__ == "__main__":
    unittest.main()
