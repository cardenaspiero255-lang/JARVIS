# Ultra 2.0 — local inference milestone

This milestone is not a deployed LLM. JARVIS remains offline-first and must not silently restore paid cloud endpoints.

## Acceptance gates

1. Select a locally licensed model compatible with the target Android device; record model license, source, format, SHA-256 and memory budget.
2. Verify model integrity before loading and reject unknown or corrupted weights.
3. Benchmark first-token latency, tokens per second, peak RSS, thermal throttling and battery impact on a physical Android device while gaming.
4. Preserve explicit microphone and vision permissions; never treat model output as authorization to invoke tools.
5. Keep deterministic offline chat as fallback on low memory, missing model or failed inference.
6. Write failing Kotlin tests before implementing the inference adapter; run Android shell, Android 14 instrumentation and contract tests on the exact PR head.
7. Do not declare SSS+ or parity with proprietary hosted models without independent task evaluations.

## Follow-on sequence

After a verified inference adapter: improve speech turn-taking and cancellation; validate opt-in screen understanding and authenticated GameHub IPC; audit with Ultra Sentinel and real-device regression tests.
