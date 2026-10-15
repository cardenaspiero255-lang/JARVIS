"""Omega Brain: deterministic dependency planner and non-executing risk preview."""
import unittest
from jarvis_contracts.task_plan import PlanCompiler, InvalidPlan, ActionRisk


class PlanCompilerTests(unittest.TestCase):
    def setUp(self):
        self.compiler = PlanCompiler()
        self.simple = {"steps": [
            {"id": "review", "action": "review.requested", "dependsOn": ["read"]},
            {"id": "read", "action": "pull_request.read", "dependsOn": []},
            {"id": "status", "action": "status.read", "dependsOn": ["read"]},
        ]}

    def test_dependencies_are_scheduled_deterministically(self):
        p = self.compiler.compile(self.simple)
        self.assertEqual([s.identifier for s in p.steps],
                         ["read", "review", "status"])
        self.assertEqual(p.risks["read"], ActionRisk.READ_ONLY)
        self.assertEqual(p.risks["review"], ActionRisk.REQUIRES_APPROVAL)
        self.assertFalse(p.can_execute)

    def test_cycle_and_missing_dependency_rejected(self):
        samples = [
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": ["b"]},
                       {"id": "b", "action": "status.read", "dependsOn": ["a"]}]},
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": ["missing"]}]},
        ]
        for raw in samples:
            with self.subTest(raw=raw), self.assertRaises(InvalidPlan):
                self.compiler.compile(raw)

    def test_duplicate_steps_rejected_before_execution(self):
        with self.assertRaises(InvalidPlan):
            self.compiler.compile({"steps": [
                {"id": "a", "action": "status.read", "dependsOn": []},
                {"id": "a", "action": "status.read", "dependsOn": []},
            ]})

    def test_sensitive_actions_never_execute_automatically(self):
        raw = {"steps": [
            {"id": "merge", "action": "merge.execute", "dependsOn": []},
            {"id": "pay", "action": "payment.submit", "dependsOn": []},
            {"id": "shell", "action": "shell.execute", "dependsOn": []},
            {"id": "tap", "action": "ui.tap", "dependsOn": []},
        ]}
        p = self.compiler.compile(raw)
        self.assertFalse(p.can_execute)
        self.assertEqual(p.risks["merge"], ActionRisk.REQUIRES_APPROVAL)
        self.assertEqual(p.risks["pay"], ActionRisk.REQUIRES_APPROVAL)
        self.assertEqual(p.risks["shell"], ActionRisk.BLOCKED)
        self.assertEqual(p.risks["tap"], ActionRisk.BLOCKED)

    def test_unknown_action_is_blocked_not_guessed(self):
        p = self.compiler.compile({"steps": [
            {"id": "future", "action": "omnipotent.execute", "dependsOn": []},
        ]})
        self.assertEqual(p.risks["future"], ActionRisk.BLOCKED)
        self.assertFalse(hasattr(p, "execute"))

    def test_malformed_input_fails_closed(self):
        invalid = [
            {},
            {"steps": []},
            {"steps": "not a list"},
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": [], "approved": True}]},
            {"steps": [{"id": "../bad", "action": "status.read", "dependsOn": []}]},
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": "b"}]},
            {"steps": [{"id": "a", "action": True, "dependsOn": []}]},
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": ["a"]}]},
        ]
        for raw in invalid:
            with self.subTest(raw=raw), self.assertRaises(InvalidPlan):
                self.compiler.compile(raw)

    def test_duplicate_dependencies_and_limit_fail_closed(self):
        with self.assertRaises(InvalidPlan):
            self.compiler.compile({"steps": [
                {"id": "a", "action": "status.read", "dependsOn": []},
                {"id": "b", "action": "status.read", "dependsOn": ["a", "a"]},
            ]})
        many = {"steps": [
            {"id": f"s{i}", "action": "status.read", "dependsOn": []}
            for i in range(51)
        ]}
        with self.assertRaises(InvalidPlan):
            self.compiler.compile(many)

    def test_read_only_plan_is_still_a_preview_not_an_execution_authority(self):
        p = self.compiler.compile({"steps": [
            {"id": "inspect", "action": "pull_request.read", "dependsOn": []}
        ]})
        self.assertEqual(p.risks["inspect"], ActionRisk.READ_ONLY)
        self.assertFalse(p.can_execute)

    def test_action_name_has_bounded_length(self):
        with self.assertRaises(InvalidPlan):
            self.compiler.compile({"steps": [
                {"id": "a", "action": "status." + "x" * 1000, "dependsOn": []}
            ]})

    def test_impact_summary_reports_risk_counts_without_permission(self):
        preview = self.compiler.compile({"steps": [
            {"id": "a", "action": "status.read", "dependsOn": []},
            {"id": "b", "action": "review.requested", "dependsOn": ["a"]},
            {"id": "c", "action": "shell.execute", "dependsOn": ["b"]},
        ]})
        self.assertEqual(preview.read_only_count, 1)
        self.assertEqual(preview.approval_count, 1)
        self.assertEqual(preview.blocked_count, 1)
        self.assertFalse(preview.can_execute)
