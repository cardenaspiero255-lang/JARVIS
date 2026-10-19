#!/usr/bin/env python3
"""Evaluate real, labeled, SHA-pinned 16k PCM WAV with native sherpa-onnx.

Usage:
 python tools/evaluate_kws_real_wav.py --model-dir /tmp/verified_sherpa_kws \
   --wav-root /data/consented-wav --manifest /data/manifest.json \
   --output /tmp/kws-evidence.json

This runs ACTUAL model inference. Without real held-out recordings it refuses
to emit accuracy metrics. Do NOT treat upstream model demo audio as independent.
No audio is uploaded by this tool.
"""
import argparse
import hashlib
import json
from pathlib import Path
import wave

MODEL_FILES = (
    "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
    "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
)
MAX_WAV_BYTES = 5_000_000


def validate_manifest(rows):
    if not isinstance(rows, list) or not rows:
        raise ValueError("At least one labeled, verified WAV is required")
    seen = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {
            "file", "sha256", "wake", "speaker", "split"
        }:
            raise ValueError("WAV fields: file, sha256, wake, speaker, split")
        relative = Path(row["file"])
        if not row["file"] or relative.is_absolute() or ".." in relative.parts:
            raise ValueError("Unsafe or missing WAV path")
        if relative.suffix.lower() != ".wav":
            raise ValueError("Only WAV PCM16")
        if (not isinstance(row["sha256"], str) or len(row["sha256"]) != 64 or
                any(ch not in "0123456789abcdef" for ch in row["sha256"])):
            raise ValueError("Invalid audio SHA256")
        if type(row["wake"]) is not bool or row["split"] != "holdout":
            raise ValueError("Explicit boolean labels from independent holdout only")
        if not isinstance(row["speaker"], str) or not row["speaker"].strip():
            raise ValueError("Speaker source/group is required for leakage audit")
        if row["sha256"] in seen:
            raise ValueError("Duplicate audio in held-out evaluation")
        seen.add(row["sha256"])


def summarize(outcomes):
    if not outcomes:
        raise ValueError("No genuine evaluation data")
    tp = sum(1 for truth, pred in outcomes if truth and pred)
    fn = sum(1 for truth, pred in outcomes if truth and not pred)
    fp = sum(1 for truth, pred in outcomes if not truth and pred)
    tn = sum(1 for truth, pred in outcomes if not truth and not pred)
    if not (tp + fn) or not (tn + fp):
        raise ValueError("Both wake-positive and wake-negative WAVs are mandatory")
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn)
    return {
        "n": len(outcomes), "tp": tp, "fp": fp, "fn": fn, "tn": tn,
        "precision": precision, "recall": recall,
        "fpr": fp / (fp + tn),
        "fnr": fn / (fn + tp)
    }


def check_wav(path, expected_sha):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > MAX_WAV_BYTES:
        raise ValueError("Missing, linked or oversized WAV")
    payload = path.read_bytes()
    if hashlib.sha256(payload).hexdigest() != expected_sha:
        raise ValueError("Audio SHA256 mismatch")
    import numpy as np
    with wave.open(str(path), "rb") as w:
        if w.getnchannels() != 1 or w.getframerate() != 16_000 or w.getsampwidth() != 2 or w.getcomptype() != "NONE":
            raise ValueError("Expect only mono PCM16 16kHz")
        pcm = w.readframes(w.getnframes())
    return np.frombuffer(pcm, dtype="<i2").astype(np.float32) / 32768.0


def verify_model(folder):
    # Reuse the build-time pinned manifest: NEVER trust arbitrary downloaded
    # ONNX files, even for offline benchmark.
    from prepare_kws_model import REQUIRED
    for name, digest in REQUIRED.items():
        path = folder / name
        if not path.is_file() or path.is_symlink():
            raise ValueError("Unverified model missing: " + name)
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError("Unverified model hash: " + name)
    keywords = folder / "keywords.txt"
    if not keywords.is_file() or not keywords.read_text().strip().endswith("@Jarvis"):
        raise ValueError("Model BPE keyword list missing")


def detect(spotter, pcm):
    import numpy as np
    stream = spotter.create_stream()
    hits = []
    for offset in range(0, len(pcm), 2048):
        stream.accept_waveform(16000, pcm[offset:offset + 2048])
        for _ in range(300):
            if not spotter.is_ready(stream):
                break
            spotter.decode_stream(stream)
            result = spotter.get_result(stream)
            if result:
                hits.append(result)
                spotter.reset_stream(stream)
    stream.accept_waveform(16000, np.zeros(10560, dtype=np.float32))
    stream.input_finished()
    for _ in range(2000):
        if not spotter.is_ready(stream):
            break
        spotter.decode_stream(stream)
        result = spotter.get_result(stream)
        if result:
            hits.append(result)
            spotter.reset_stream(stream)
    else:
        raise RuntimeError("KWS native decoder did not converge")
    return any(hit.casefold() == "jarvis" for hit in hits)


def evaluate(model_dir, wav_root, rows):
    validate_manifest(rows)
    verify_model(model_dir)
    import sherpa_onnx
    spotter = sherpa_onnx.KeywordSpotter(
        tokens=str(model_dir / "tokens.txt"),
        encoder=str(model_dir / MODEL_FILES[0]),
        decoder=str(model_dir / MODEL_FILES[1]),
        joiner=str(model_dir / MODEL_FILES[2]),
        keywords_file=str(model_dir / "keywords.txt"),
        num_threads=2, sample_rate=16000, provider="cpu"
    )
    labels_and_predictions = []
    cases = []
    for row in rows:
        f = (wav_root / row["file"]).resolve()
        if not f.is_relative_to(wav_root.resolve()):
            raise ValueError("WAV escapes approved corpus directory")
        predicted = detect(spotter, check_wav(f, row["sha256"]))
        labels_and_predictions.append((row["wake"], predicted))
        cases.append({
            "sha256": row["sha256"], "wake": row["wake"],
            "detected": predicted, "speaker": row["speaker"],
            "split": row["split"]
        })
    return {"status": "EVALUATED_REAL_AUDIO", "metrics": summarize(labels_and_predictions), "cases": cases}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model-dir", type=Path, required=True)
    p.add_argument("--wav-root", type=Path, required=True)
    p.add_argument("--manifest", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    result = evaluate(a.model_dir, a.wav_root, json.loads(a.manifest.read_text()))
    a.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
