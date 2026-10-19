#!/usr/bin/env python3
"""Fail-closed QA coverage inventory for JARVIS' 50 SSS+ improvements.

A test file existing is *not* an executed or passing test. CI determines
execution status independently; this report only inventories defined evidence.
Never certify an untested feature, owner voice, or Funtouch/physical behavior.
"""
import argparse
import json
from pathlib import Path
import re

TRACKER = "docs/SSS_PLUS_50_TRACKER.md"

# These paths are individually reviewed associations, not fuzzy keyword matches.
# New features default to uncovered until a relevant test is explicitly mapped.
TESTS = {
    1: ("tests/test_task_plan.py",),
    2: ("tests/test_plan_dependency_risks.py",),
    3: (),
    4: (),
    5: ("tests/test_verified_error_memory.py",),
    6: (),
    7: (),
    8: ("tests/test_concurrency_hardening.py",),
    9: (),
    10: (),
    11: (),
    12: ("tests/test_vision_button_hints.py",),
    13: (),
    14: (),
    15: ("tests/test_android_speech_manifest.py",),
    16: (),
    17: ("tests/test_gamehub_launch_security.py",),
    18: ("tests/test_hands_voice_integration.py",),
    19: (),
    20: ("tests/test_recovery_inspector.py",),
    21: ("tests/test_github_snapshot_audit.py",),
    22: ("tests/test_guardian_check_evidence.py",),
    23: ("tests/test_adversarial_families.py",),
    24: ("tests/test_security_regressions.py",),
    25: ("tests/test_github_snapshot_audit.py",),
    26: ("tests/test_partner_authority_regressions.py",),
    27: ("tests/test_gamehub_verified_handoff.py",),
    28: ("tests/test_durable_security.py",),
    29: (),
    30: ("tests/test_guardian_check_evidence.py",),
    31: ("tests/test_adversarial_security.py",),
    32: ("tests/test_security_regressions.py",),
    33: ("tests/test_secure_transport.py",),
    34: ("tests/test_integrity_audit.py",),
    35: ("apps/android/app/src/test/java/com/cardenaspiero255/jarvis/core/SessionLockCoordinatorTest.kt",
         "tests/test_privacy_session_lock_contract.py"),
    36: (),
    37: ("tests/test_partner_authority_regressions.py",),
    38: ("tests/test_speech_timeout_safety.py",),
    39: ("tests/test_private_android_memory_contract.py",),
    40: ("tests/test_plan_dependency_risks.py",),
    41: ("apps/android/app/src/test/java/com/cardenaspiero255/jarvis/core/SpeechUtteranceTrackerTest.kt",),
    42: ("tests/test_screen_projection_privacy.py",),
    43: (),
    44: (),
    45: (),
    46: (),
    47: ("apps/android/app/src/androidTest/java/com/cardenaspiero255/jarvis/PrivateConversationStoreInstrumentedTest.kt",),
    48: ("tests/test_device_health_privacy.py",),
    49: ("tests/test_omega_voice_onboarding.py",),
    50: ("tests/test_adversarial_security.py",
         "tests/test_lockscreen_biometric_contract.py"),
}

# These CI-facing checks have separate observational limits.
VOICE_SCENARIOS = {
    "screen_off_emulator_pcm": (
        "apps/android/app/src/androidTest/java/com/cardenaspiero255/jarvis/LockScreenAudioContinuityInstrumentedTest.kt",
        "EMULATOR_ONLY"
    ),
    "kws_onnx_silence": (
        "apps/android/app/src/androidTest/java/com/cardenaspiero255/jarvis/SherpaNativeOfflineInstrumentedTest.kt",
        "EMULATOR_ONLY"
    ),
    "owner_match_stays_unavailable": (
        "apps/android/app/src/test/java/com/cardenaspiero255/jarvis/OwnerVoiceCosineTest.kt",
        "SIMULATED_VECTORS"
    ),
    "owner_holdout_no_leakage": (
        "tests/test_owner_biometric_holdout_evaluation.py",
        "SYNTHETIC_SCORES"
    ),
    "session_privacy_timeouts": (
        "tests/test_long_listening_owner_probe_contract.py",
        "SOURCE_CONTRACT_ONLY"
    ),
    "physical_funtouch_30min": (None, "DEVICE_REQUIRED"),
    "owner_replay_attack_real": (None, "CONSENTED_AUDIO_REQUIRED"),
    "owner_far_frr_real": (None, "CONSENTED_AUDIO_REQUIRED"),
    "latency_battery_real": (None, "DEVICE_REQUIRED"),
}


def _tracker_rows(root):
    text = (root / TRACKER).read_text(encoding="utf8")
    rows = {}
    for line in text.splitlines():
        if not re.match(r"^\|\s*\d+\s*\|", line):
            continue
        cells = [v.strip() for v in line.split("|")]
        if len(cells) < 5:
            raise ValueError("Malformed SSS+ tracker row")
        number = int(cells[1])
        if number in rows:
            raise ValueError("Repeated SSS+ feature ID")
        rows[number] = {"name": cells[3], "category": cells[2],
                        "tracker_status": cells[4]}
    if sorted(rows) != list(range(1, 51)):
        raise ValueError("Expected exactly fifty distinct feature IDs 1..50")
    return rows


def build_report(root):
    root = Path(root)
    rows = _tracker_rows(root)
    if sorted(TESTS) != list(range(1, 51)):
        raise ValueError("Evidence registry must include exactly IDs 1..50")
    features = []
    for number, row in sorted(rows.items()):
        mapped = TESTS[number]
        for relative in mapped:
            if not (root / relative).is_file():
                raise ValueError(f"Missing mapped test for feature {number}: {relative}")
        features.append({
            "id": number, **row, "defined_tests": list(mapped),
            "automated_coverage":
                "TEST_DEFINED_NOT_EXECUTION_EVIDENCE" if mapped else "NO_MAPPED_TESTS",
            "verified_on_physical_android": False,
            "fully_certified": False
        })
    scenarios = {}
    for key, (path, tier) in VOICE_SCENARIOS.items():
        if path and not (root / path).is_file():
            raise ValueError(f"Missing mapped voice scenario: {key} -> {path}")
        scenarios[key] = {"test": path, "tier": tier,
                          "executed_here": False, "certified": False}
    covered = sum(bool(row["defined_tests"]) for row in features)
    return {
        "schema": "jarvis.qa-inventory.v1",
        "total_features": 50,
        "features_with_defined_tests": covered,
        "features_without_mapped_tests": 50 - covered,
        "features_certified": 0,
        "product_status": "NOT_CERTIFIED",
        "device_test_executed_here": False,
        "funtouch_screen_off_certified": False,
        "voice_owner_authorized": False,
        "unverified_evidence_classes": [
            "hardware", "real_owner_biometrics", "real_kws_audio",
            "power_thermal", "third_party_integrations"
        ],
        "voice_scenarios": scenarios,
        "features": features,
        "caveat": (
            "Registry means TEST DEFINED, not PASSED. The host GitHub workflow "
            "reports execution separately. Real Vivo/Funtouch, owner FAR/FRR, "
            "replay resistance and third-party adapters remain unverified."
        )
    }


def markdown_report(report):
    lines = [
        "# JARVIS 50-feature automated evidence inventory",
        "",
        f"**Mapped tests:** {report['features_with_defined_tests']}/50 "
        "(presence only; **not** 50 successful features).",
        f"**Missing mapped tests:** {report['features_without_mapped_tests']}. "
        "**Fully certified by this report:** 0.",
        "",
        "**This inventory does not run the tests.** Check the other CI jobs "
        "for pass/fail. Emulator cannot validate actual Vivo/Funtouch, owner's "
        "FAR/FRR, recorded attacks or battery life.",
        "",
        "| ID | Improvement | Current tracker | Automated evidence |",
        "|---:|---|---|---|",
    ]
    for entry in report["features"]:
        state = entry["automated_coverage"].replace("_", " ")
        name = entry["name"].replace("|", "/")
        status = entry["tracker_status"].replace("|", "/")
        lines.append(f"| {entry['id']} | {name} | {status} | {state} |")
    lines += ["", "## Voice-specific scenarios", "",
              "| Scenario | Test tier | Evidence exists |",
              "|---|---|---|"]
    for name, scenario in report["voice_scenarios"].items():
        lines.append(f"| {name} | {scenario['tier']} | "
                     f"{'defined only' if scenario['test'] else 'not available'} |")
    lines += ["", "**Release verdict: NOT_CERTIFIED**", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--markdown-out", type=Path)
    args = parser.parse_args()
    report = build_report(args.root)
    markdown = markdown_report(report)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(report, indent=2, ensure_ascii=False)+"\n")
    if args.markdown_out:
        args.markdown_out.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_out.write_text(markdown)
    print(f"QA inventory: {report['features_with_defined_tests']}/50 tests mapped; "
          f"{report['features_without_mapped_tests']} gaps. Verdict: NOT_CERTIFIED")


if __name__ == "__main__":
    main()
