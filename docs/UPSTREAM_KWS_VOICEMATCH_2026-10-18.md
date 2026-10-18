# JARVIS — KWS / Voice Match upstream reuse gate (2026-10-18)

This PR is a **reproducible model-selection and authorization prerequisite**, **not** a claim that the native KWS or owner speaker verification has shipped.

## Audited upstreams (avoid rebuilding their entire feature sets)
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) — Android `SherpaOnnxKws`, `SherpaOnnxSpeakerIdentification`, streaming KWS and speaker embeddings. Take API/data contracts from those demos, not app UIs.
- [alphacep/vosk-api](https://github.com/alphacep/vosk-api) (Apache-2.0) — currently deployed Spanish recognition fallback, already integrated into JARVIS.
- [microsoft/UFO](https://github.com/microsoft/UFO) (MIT) — Windows desktop agent orchestration ideas, **not** native Android microphone code.
- [morluto/rea](https://github.com/morluto/rea) (MIT) — conditional executor patterns; sandbox before running anything.
- [Dodaios/Dodaios](https://github.com/Dodaios/Dodaios) (Apache-2.0, original audit PR #66) — agent orchestration ideas only, not a bypass of Android privileges.

## KWS model identified and deliberately NOT enabled
Official English KWS archive: `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile.tar.bz2` (~15 MB) from `k2-fsa/sherpa-onnx/releases/tag/kws-models`. Current available upstream models are English / Chinese, not a verified Spanish-Chilean 'Jarvis' model. Individual file SHA-256 pins are from a declared unmodified mirror; **an independent upstream checksum review is required** before embedding or native execution. Native Android AAR/ABI + ONNX initialization are NOT part of this PR.

`OfflineKwsModelGate.verify()` rejects absent, symlinked, oversized, and corrupted components before any native adapter would be allowed. It cannot grant microphone permissions. `KwsCandidateGate` accepts only verified, session-authorized, exact native 'Jarvis' candidates with a 1200 ms cooldown; does NOT recognize audio itself, perform owner identity verification, or execute any commands. The existing on-device Vosk code remains unchanged.

## Mandatory next KWS step
1. Pin verified upstream checksums and compatible Android ARM64/x86_64 native release + JNI ABI; confirm rights/licenses of model and library.
2. Integrate official Kotlin API via a small adapter of `KeywordSpotter`, supply English BPE tokens from `text2token` for 'JARVIS', load model in a background thread and expose secure stop/close lifecycle, using single authorized foreground AudioRecord session.
3. Validate wake/false activations using independently recorded Spanish speech, ambient speech, speaker replay, screen off and low-memory/emulated cases. Never infer real-device performance from CI.
4. If KWS fails to initialize or score thresholds cannot be justified, fall back to existing **bounded** Vosk preview (not silent always-on listening).

## Mandatory Voice Match step AFTER KWS
Follow official sherpa-onnx speaker identification demo, with opt-in local enrollment, model-bound encrypted embeddings, explicit revoke/delete, local-only scoring and rejection of UNAVAILABLE/MISMATCH/UNCERTAIN/REPLAY_SUSPECTED. Speech recognition and wake detection **cannot prove identity**. No privileges or sensitive commands unlocked solely with Voice Match.

## Other restrictions
Android 14+ microphone foreground-service rules, notification visibility, explicit user consent and separate control for stopping listening always apply. Never disguise a service as background OS access.
Sentry: no authenticated read-only token available in execution environment; telemetry investigation not performed, no Sentry validation claimed.
