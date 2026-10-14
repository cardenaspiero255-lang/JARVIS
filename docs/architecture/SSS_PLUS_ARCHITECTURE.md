# JARVIS OMEGA — SSS+ Architecture RFC v1

Status: **PROPOSED**. Design only; integrations are NOT implemented. Related: #1.

## Architectural principles
- Hexagonal / Clean Architecture: domain and application layers never depend on Android, GitHub, LLM vendors, or partner apps.
- Explicit ports and adapters; API-first, contract-first, backward-compatible evolution.
- Secure-by-default; least privilege, explicit user consent, separate execution authority from AI planning.
- Offline-first for safe local functions; graceful degradation when network/partners unavailable.
- Idempotent commands, bounded retries, observability, cancellable tasks, auditable outcomes.
- No direct access to other projects' internal databases/classes. No cross-repo source coupling.

## Proposed module boundaries
```
apps/android/                 # Android shell, activities, foreground services, permissions
core/domain/                  # Tasks, plans, actions, permissions, outcomes, events
core/application/             # Orchestration, policies, state machines, task lifecycle
core/contracts/               # Versioned command/event DTOs and JSON schemas
core/security/                # Consent, scoped capabilities, credential handling
core/observability/           # Structured logs, trace context, metrics, redaction
features/voice/               # ASR/TTS/wake word adapters
features/vision/              # Accessibility tree and permitted visual interpretation
features/hands/               # Accessibility executor, gesture/click/text adapters
features/memory/              # Local encrypted storage, retrieval, retention
features/planner/             # AI planner, policy-constrained plan validation
features/recovery/            # Retry, checkpoints, rollback where supported
features/automation/          # Scheduled jobs and execution queue
integrations/github/          # GitHub API adapter
integrations/ultra-sentinel/  # Sentinel API/event adapter
integrations/gamehub-ultra/   # GameHub API/event adapter
integrations/chatgpt/         # Authorized interface/API adapter where available
integrations/desktop/         # Optional paired desktop agent
testing/contracts/            # Consumer/provider compatibility suites
testing/integration/          # Fake services and failure injection
testing/e2e/                  # Android instrumentation and permission tests
docs/adr/                     # Architecture decision records
docs/runbooks/                # Incident, migration, rollback procedures
```
These are *target paths*, not currently implemented modules.

## Contract-first communication
All integrations must publish versioned schema, capability manifest, authentication requirements, permissions, timeouts, error taxonomy and ownership. Recommended envelope:
```json
{
  "schemaVersion": "1.0",
  "messageId": "uuid",
  "correlationId": "uuid",
  "causationId": "uuid-or-null",
  "timestamp": "ISO-8601",
  "source": "jarvis",
  "target": "ultra-sentinel",
  "type": "review.requested",
  "idempotencyKey": "stable-operation-key",
  "deadline": "ISO-8601",
  "payload": {}
}
```
Validate against JSON Schema. Unknown major versions rejected with actionable error; minor additive changes tolerated where contract allows. Deprecate with compatibility windows and migration notes. Use explicit adapters per provider. No silent field guessing.

## Integration topology
- JARVIS is the orchestration/control plane; not a privileged bypass.
- Ultra Sentinel is the independent review and verification service; returns findings, evidence, confidence and verdict.
- GameHub Ultra is the gaming capability provider; publishes supported operations and device requirements.
- GitHub adapter reads PR SHA, checks, reviews, permissions and write outcomes directly; ChatGPT UI text is never treated as GitHub execution proof.
- Android app is a local execution agent. External communication over TLS, authenticated and scoped; paired devices require explicit enrollment and revocation.
- Prefer direct APIs for durable operations, AccessibilityService only for authorized user-visible UI tasks; UI interactions are inherently best-effort.

## Commands and events
Commands: TaskRequested, TaskCancelRequested, ReviewPRRequested, PrepareFixRequested, LaunchGameRequested, UIActionRequested.
Events: TaskAccepted, TaskStarted, StepSucceeded, StepFailed, TaskAwaitingApproval, TaskCompleted, TaskFailed, TaskCancelled, ReviewCompleted.
Every task has stable taskId and state machine: CREATED -> VALIDATED -> WAITING_APPROVAL? -> QUEUED -> RUNNING -> VERIFYING -> SUCCEEDED / FAILED / CANCELLED / NEEDS_USER.
Durable outbox/inbox when persistence exists, at-least-once delivery plus deduplication; do not claim exactly-once delivery. Persist checkpoints and reconcile uncertain outcomes before retrying non-idempotent operations.

## Reliability and connectivity
- Health/capability handshake before first operation and on reconnection; fail closed on missing permissions or incompatible major version.
- Timeouts per action, exponential backoff with jitter and caps, circuit breaker, bulkheads and concurrency limits.
- Network offline queue only for explicitly safe, expiring, idempotent tasks. No blind replay of destructive actions.
- Use cancellation propagation and dead-letter handling with diagnostics.
- Fallback modes: API unavailable -> read-only advisory; Sentinel unavailable -> block merge decisions requiring Sentinel; GameHub unavailable -> explain unsupported capability.
- Android lifecycle: no Thread.sleep on main thread, asynchronous waits, foreground service where permitted, battery constraints respected, no guarantee of continuous background execution.
- Accessibility node matching must check package, role, labels, locale and live window state; each action verifies postcondition. Secure windows remain inaccessible.

## Security and approvals
- Planner produces *proposals*, executor independently validates policy.
- Capability tokens scoped by action, repository, resource, time, and user approval; keep tokens in secure storage and redact logs.
- Explicit confirmation for merges, destructive GitHub writes, payments, sensitive messages, privacy-impacting actions and permission escalation.
- Guard against prompt injection in screen text, PR descriptions, issue comments and model outputs; untrusted text cannot grant tool permissions.
- Emergency stop, revocation, action logs, data minimization, consent-based screen/audio collection.
- Accessibility and Shizuku do not override OS restrictions or third-party policies.

## Contract example: GitHub PR review
1. Parse owner/repo/PR explicitly; reject ambiguous names or missing PR numbers (never default to arbitrary PR).
2. Resolve immutable PR head SHA and snapshot check/review state.
3. Request Sentinel review with correlationId, head SHA and contract version.
4. Validate signed/authenticated response and evidence, confirm SHA unchanged.
5. Prepare fix in isolated branch; TDD, static analysis, CI, independent review.
6. Before merge re-fetch SHA, checks, branch protection, reviews and findings. Abort if any known blocker; require configured approval.
7. Record GitHub response with resulting merge SHA; never infer success from UI text.

## Verification matrix / release gates
- Schema compatibility: current and previous supported versions, malformed/unknown versions, missing capabilities.
- Connectivity: offline, timeouts, partial responses, duplicate/out-of-order messages, retries, reconnect, cancellation.
- Security: expired tokens, permission revocation, injected UI text, cross-repo authorization, sensitive action confirmation.
- Android: API-level coverage, background restrictions, locales, accessibility tree changes, denied permissions.
- GitHub: stale SHA, red CI, unresolved review, rate limits, merge conflicts.
- Sentinel/GameHub: absent partner, incompatible API, partial failure, capability discovery.
- TDD + contract tests + integration tests + Android E2E + adversarial suite mandatory before production rollout.
- Feature flags, staged rollout, rollback plan, ADR and release checklist.

## SSS+ proposed metrics (not measured)
>=95% success on defined supported Android tasks; zero unauthorized sensitive actions; zero known critical regressions at release; >=90% coverage on critical core; >=95% recovery of classified recoverable failures; 100% audit trails and verification for critical tasks. Measure on representative devices and independent datasets.

## Implementation sequence
1. ADRs, dependency rules, schemas, error taxonomy, permission matrix and mock providers.
2. Contract tests and handshake/compatibility suite.
3. Android lifecycle + Hands/Voice/Vision with policy-gated executor.
4. GitHub, Sentinel and GameHub adapters against mocks, then sandbox integration.
5. Durable orchestration, observability, failure injection, recovery.
6. Real end-to-end test environment, performance/security review, staged release.

## Definition of done
No module may bypass core security; all provider adapters must have contract tests, timeouts, retries, cancellation, telemetry and version checks. Critical workflows must have end-to-end evidence. No PR merged with confirmed blocking errors. Maintain ADRs for changes to contracts and dependency direction.
