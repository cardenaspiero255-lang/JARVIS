# JARVIS Ultra 2.0 — real-device acceptance protocol (Vivo V25 Pro)

## Scope and evidence status

Android source uses a **prebuilt arm64-v8a llama.cpp native AAR** pinned to
`dev.ffmpegkit-maintained:llama-android:0.1.1` (llama.cpp build b9878).
This repository compiles/links the Android APK with the native libraries; it
does **not** rebuild llama.cpp from C++ source. The model is not shipped in
the APK and JARVIS does not automatically download weights.

Model: [official Qwen2.5-0.5B-Instruct-GGUF Q4_K_M](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/main/qwen2.5-0.5b-instruct-q4_k_m.gguf),
Apache 2.0, around 491 MB.
SHA-256 (from official model repository):
`74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`

## Test the actual phone

1. Install the APK produced by Android shell CI on the **Vivo V25 Pro**.
2. Download the **exact** Q4_K_M file from the official Qwen repository. In
   JARVIS choose **IMPORTAR MODELO GGUF — QWEN 0.5B** and select it. Import
   copies to private storage and verifies the exact pinned SHA-256. A wrong
   model must be rejected.
3. Switch the phone to **airplane mode** (Wi-Fi and mobile data off). In OMEGA
   CHAT, ask `¿Qué es un agujero negro?`, `Explica la fotosíntesis`, and
   `Dame tres consejos para ahorrar batería`. Verify that answers are genuinely
   generated locally (different from the known deterministic fallback).
4. Run 5 identical warm prompts (same question and 128-token cap) after an
   initial cold prompt. Record the reported **tokens/s**, end-to-end elapsed
   milliseconds, **PSS** and process high-water **VmHWM RSS**. Keep the phone
   plugged/unplugged state, OS version and room temperature recorded.
5. Repeat after opening a game to put pressure on memory. Document which game
   is resident and whether Android killed/recreated either process. This is a
   **co-resident process test** until background/overlay conversation is
   implemented; do not present it as verified simultaneous gameplay + chat.
6. Capture Android `dumpsys meminfo com.cardenaspiero255.jarvis`,
   `dumpsys thermalservice`, and `dumpsys battery`, before and after each
   5-prompt series. A successful inference result also shows benchmark data
   inside JARVIS. Record any crashes, ANRs, OOM kills or throttling.
7. Lock JARVIS or background it while generating: the UI must discard stale
   results and release native memory once an already-running native completion
   returns. **Immediate native cancellation is not supported by this free AAR.**

## Acceptance criteria

- SHA mismatch rejected, no paid endpoint or network request, offline prompt
  returns coherent model text, model failure falls back to safe local dialogue.
- Android CI green at exact PR HEAD; on-device tests pass without a crash, ANR
  or OOM; thermal state does not enter severe/critical thresholds in repeated
  use.
- Report **actual** cold/warm tokens/s (median and range), end-to-end latency,
  memory PSS/VmHWM, and battery/thermal observations. Do not invent a speed
  estimate; do not substitute emulator output or GitHub CI for device evidence.
- The free JNI API supplies completed text, not token streaming. It does not
  expose true *time-to-first-token*: do **not** report TTFB as measured.
- VMHWM is **process-lifetime peak RSS**, not peak RSS attributable only to a
  single inference. PSS is a sampled post-response value; both limitations
  must accompany measured reports.

## Optional ADB observations

With the device connected to an authorized computer:

```bash
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys meminfo com.cardenaspiero255.jarvis
adb shell dumpsys thermalservice
adb shell dumpsys battery
# After five prompts, capture these again; never capture actual chat prompts.
```

No device metrics are claimed until this protocol has been executed on the
physical Vivo V25 Pro and the resulting evidence reviewed.
