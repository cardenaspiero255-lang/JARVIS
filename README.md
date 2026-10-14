# JARVIS OMEGA SSS+

Early-stage **Python reference implementation**, not a production Android assistant.
See [architecture](docs/architecture/SSS_PLUS_ARCHITECTURE.md),
[implementation evidence and blockers](docs/IMPLEMENTATION_STATUS.md) and
[master issue](https://github.com/cardenaspiero255-lang/JARVIS/issues/1).

Included: versioned envelopes, capability allowlists, scoped one-use consent
reference, SQLite journal and duplicate-execution protection, safe network
boundaries, read-only GitHub REST adapter, inert Ultra Sentinel v1 review
contract and GameHub tool descriptor parsing. No real Sentinel/GameHub IPC,
Android runtime, deployment or production authorization authority exists.

Tests: `python -m unittest discover -s tests -v`.
The GitHub Actions workflow additionally validates an advisory against a
SHA-pinned upstream Ultra Sentinel wire contract using Node.js.

**Security:** Never authorize destructive work using model text, unverified
webhooks, a fixture, a GitHub check alone, or an `authorizedToMerge` claim.
The project is NOT certified SSS+ and this PR remains in draft until its
documented release gates are satisfied.
