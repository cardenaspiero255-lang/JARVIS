# GitHub read-only audit — phase 1.1

This consumer uses an **externally authenticated** GitHub REST installation
token, passed through a token-provider callback. It never requests a token,
escalates permissions, writes commits or merges PRs. No user credential is
stored in this module.

## Read-only inventory

- Fetches a specific owner/repository and numeric PR; no implicit PR fallback.
- Retrieves the immutable head SHA, pages through check runs for that SHA
  using bounded 100-item pages, and pages through human review records.
- Rejects missing or inconsistent inventories and any check whose SHA differs.
- Fetches the PR head again after collecting the inventory; rejects a moved HEAD.
- Always sets authorized_to_merge to false. A complete inventory is **not**
  evidence that required checks, authorization or branch policy have passed.

## Caller responsibilities

Use an authenticated, least-privileged GitHub App client with access only to
the repository and read-only pull-request/check-run permissions. Renew
installation tokens outside JARVIS. Do not create a write-capable token for
this adapter.

Before performing any future mutation, a separate trusted operation must
re-check current HEAD, ruleset, statuses, reviews, repository scope,
latest checks, intended diff, owner approval and user-visible consent.

The project has **one owner**: human approval and authorization policies may
be satisfied by that owner alone. This does **not** allow bypassing technical
safety checks or external platform permission requirements.

## Local verification

Run Python tests via:
    python -m unittest discover -s tests -v

These are mocked-contract tests. Live installation-token E2E and production
GitHub App authorizations are still pending, as are Android and partner
runtime integrations. No production readiness claim is made.
