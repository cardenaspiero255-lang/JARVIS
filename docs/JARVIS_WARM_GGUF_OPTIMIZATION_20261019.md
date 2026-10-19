# JARVIS — warm GGUF weight cache / optimization stage

## Baseline (PR #85, Android 14 emulator)

Reference only, SHA-pinned `ggml-org/SmolLM2-135M-GGUF`:
- First request: 68,301 ms.
- Four subsequent requests: P50 66,068 ms; P95 68,317 ms.
- Process PSS peak: 266,176 KiB.
- Spanish lexical proxy: 1/4. **Not semantic accuracy.**
- Each request loaded/unloaded weights; subsequent was **not** warm-model reuse.
- No hardware evidence from Vivo V25 Pro and no ASR → GGUF → TTS E2E.

## Stage 2: what changed

1. **Native single-weight cache**: `jarvis_llm.cpp` keeps at most
   one GGUF model in memory across requests, exclusively under
   `inference_mutex`. Reuse requires BOTH the same private filename
   and an exact lowercase SHA-256 already verified against the file bytes
   by `LocalGgufModelStore.withVerifiedModelRevision()`.
2. **No cached conversational state**: llama context and sampler still
   initialize fresh per question; a model cache HIT **does not mean**
   a KV cache, speculative decoding, or retained history.
3. **Memory budget**: only verified GGUF files <=300,000,000 bytes can
   retain native weights. Larger user models keep the old per-request
   loading/free behavior. CPU-only, no new Android permissions, network
   access, downloads or cloud API fees.
4. **No model-file races**: import and delete run on the same single
   worker that performs JNI; they signal cancellation, wait until the
   previous native call returns, evict weights under native mutex,
   then replace/delete private GGUF bytes. The UI is not blocked.
5. **Deterministic teardown**: `close()` seals the engine against new
   requests and queues eviction after existing work. Cancellation is
   cooperative; it never pretends native work finished because a Future
   was marked cancelled.
6. **Real E2E instrumentation**: with the genuine 101,016,128-byte
   Apache-2.0 model and SHA pinned by the existing pipeline, require
   EXACTLY ONE model load across five actual JNI generations and >=4
   cache hits. Then delete through the engine and assert private file
   removal and a native eviction. Reject absent or fabricated metrics.
7. **Benchmark report**: emitted JSON/Markdown includes native
   model load/hit/eviction counters, latency P50/P95, process PSS,
   original Spanish lexical proxy and explicit NOT_CERTIFIED flags.

## Acceptance

The PR is **NOT complete until** the current HEAD passes Android shell,
Contract tests, QA50, Android14 instrumented and the pinned GGUF real
generation E2E. Count and measure actual JNI loads/hits, not guessed
speedups. Compare new latency against the PR #85 baseline, with the
important caveat of noisy emulated CPU speed. Improvement in model
loading does not imply equivalent improvement in total token decoding.

## Remaining phases

- Time to first token, actual emitted tokens/s and native prefill/decoding
  breakdown (the current API returns only the completed answer).
- Multi-model Spanish factual independent benchmark, quality and hallucination
  rate with representatively frozen questions and evaluation.
- Authorised hands-free ASR → on-device GGUF → TTS E2E with STOP, privacy,
  consent, screen-off recovery and owner-only wake validation.
- Once ready, a SINGLE optional physical test on Vivo V25 Pro/Funtouch.
  Never import emulator PSS/speed values as Vivo evidence.

If native cache retention consumes too much RAM, the existing model
deletion action can evict it and the Activity close queues cleanup.
Do not certify physical energy/thermal properties from emulator.
