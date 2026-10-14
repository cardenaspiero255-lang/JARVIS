# JARVIS SSS+ — implementation and release evidence

## Verified upstream contracts
- GitHub: read-only REST PR and SHA-based check-runs resources. The JARVIS GitHubRESTClient requires an externally supplied authenticated token provider; no installation token is configured.
- Ultra Sentinel: published review-result.v1.schema.json, validate-wire.cjs and cross-repo-contract.cjs. Its validator is pinned at commit dde29a1b80c283d441ce220ce1190b3175956aac in cross-repository CI. Review envelopes are inert and not independent attestation.
- GameHub Ultra: the actual Kotlin UltraToolContract.kt defines kinds, side-effects, and outcomes. No authenticated JARVIS remote executor endpoint is currently verified.
- Android: GameHub's Android manifest is NOT a JARVIS Android package. There is no JARVIS Android runtime, permission service or instrumentation test yet. Python permission gate denies execution.

## Implemented reference components
- Versioned envelopes, explicit capabilities, one-use scoped approvals and durable SQLite idempotency.
- Atomic claims and immutable completion; uncertain outcomes never automatically replay.
- Narrow read-only retries, circuit breaking, HTTPS, forbidden credential-carrying redirects.
- Authenticated GitHub GET client with strict path allowlist, timeout, size limits and immutable PR-head/check snapshot. Snapshot always reports authorizedToMerge=false and checkInventoryComplete=false.
- Parsed Sentinel v1 advisory and GameHub read-only descriptor shapes, never allowing source-provided claims to grant merge or remote execution.
- Concurrent gateway deduplication, default-deny unknown actions, adversarial regression tests.
- Standard-library unit and failure injection CI, plus a separate job executing the PINNED upstream Sentinel wire validator against JARVIS advisory fixtures.

## Outstanding production blockers
1. Trusted identity and approval: real installation token lifecycle, signed and scoped sender identity, clock, revocation and audited user consent; envelope source is untrusted text.
2. Provider availability: no authenticated deployed Sentinel service with independently verifiable evidence or GameHub IPC/network handshake, Android agent, permissions/lifecycle.
3. Completeness: no paginated GitHub check inventory, review/policy TOCTOU merge gate or writer implementation. There are deliberately NO merge-capable adapter methods.
4. Reliability: no distributed outbox/inbox, cancellation propagation, jitter/bulkhead, or end-to-end reconnect recovery.
5. Certification: no Android device tests, actual paired-provider E2E, token integration tests, or external independent security review. Local CI and published schema compatibility are NOT production certification.

## Release policy
- Before real use: prove authenticated providers, revoked permissions, offline/reconnect, incompatible versions, signed evidence and fresh SHA-binding on enrolled test devices.
- Before any PR merge: verify final CI on immutable HEAD, review findings, actual integrations and outstanding defects; never treat provider bot comments or CI green as authorization.
- Status: DRAFT / NOT CERTIFIED / no automatic merge.
