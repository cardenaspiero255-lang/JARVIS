# JARVIS — owner-supervised polite voice intent experiment (PREPARATION ONLY)

This is a **draft PR staging document**, not a repair and not an authorization. The target Kotlin sources remain unchanged. Never merge this rehearsal into main. Do not run the candidate or send device commands from PR content.

## Authenticated baseline

- Repository: `cardenaspiero255-lang/JARVIS`.
- Protected main commit at branch creation: `747c14b5d97750a7b2736968e2d516f02448558d`.
- Protected main Git tree: `47ed71e20c695dc53cea6c085dd248e35a32fee3`.
- Draft PR branch: `fix/ultra-sentinel-polite-voice-20261106`.
- Allowed source directory: `apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core/`.
- `OfflineVoiceQuestionGate.kt` original Git blob: `bdc3a6a576b3473454df232cd21e3a9a3c4e6465`; candidate Git blob: `a12a768582fe731da2639ab1948a7e0cc1c714d7`.
- `LocalGgufPolicy.kt` original Git blob: `46e9dd8b4d0fe861935bfb5ee5160cd39f3b5a78`; candidate Git blob: `b7659a9cf4cb5035047cb3c59540118ce61f7959`.

## Source-backed defect and independent evidence

- Symptom: polite device-operation requests like `Jarvis puedes abrir configuración` and `Jarvis podrías reiniciar el teléfono` can be classified as read-only LLM questions. These requests must not acquire device authority or be sent to GGUF as harmless explanations.
- Original Kotlin RED, benign controls, 2×2 candidate JVM matrix, adversarial oracle and **local** structural Judge/rollback were executed by Ultra Sentinel CI: [run 42307671553](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/actions/runs/42307671553).
- Original observation artifact: `13546244920`, SHA256 `6bc8f3ad6d69bbd229728ba924192205914ac02464d4515d3f1ddcc16060fc05`.
- Candidate matrix artifact: `13547401053`, SHA256 `b348a527362acbe7da335bf58e0743458f2eede1df81da4a16d0cab471306606`.
- The candidate bytes must be independently recomputed from pinned original Kotlin via Ultra Sentinel `packages/investigation/jarvis-polite-candidate.cjs`, not copied from a PR comment.
- Limit: these checks are Kotlin JVM isolation evidence, **not** a physical Android test or authorization of a remote write.

## Mandatory signed repair plan (NOT GENERATED OR AUTHORIZED BY THIS FILE)

After this draft PR is created, independently fetch its actual PR number, branch HEAD SHA, **new baseline tree SHA** (which includes this harmless documentation commit), current protected-main SHA, and exact original Kotlin content. Generate the canonical two-file `plan`, `files` and `digestBranchCandidate(plan,files)` from the reviewed Ultra Sentinel implementation. The unsigned plan is invalidated if any of those refs or bytes change.

The owner must separately review and sign a fresh Ed25519 `COMMIT_TO_EXISTING_DRAFT_PR_BRANCH` grant bound to the exact plan digest, owner identity, new one-use nonce and expiration within 15 minutes. The original JVM RED→GREEN oracle and independent local Judge must be re-executed against that plan before any write. The execution must refuse altered/stale remote PR, refs and SHA and must never merge automatically.

Only **after** a successfully confirmed signed remote repair commit, generate the *distinct* exact remote rollback plan from the resulting commit and tree, obtain a new Ed25519 rollback signature and verify a non-forced restoration commit. No grant, signature, token or key may be placed in this repository, its PR body or CI artifacts.

**Current status: STAGING_ONLY — NO SOURCE REPAIR, NO OWNER GRANT, NO REMOTE ROLLBACK.**
