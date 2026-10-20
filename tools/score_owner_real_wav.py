#!/usr/bin/env python3
"""Local-only WAV -> actual pinned sherpa-onnx owner cosine scores.

Requires 3 real, consented enrollment WAVs and independent, labeled real
probe WAVs. No audio, embeddings, transcripts, file paths or scores leave the
local machine. Synthetic CI tests verify the program, not a person's voice.
Never authorizes voice identity or Android actions.
"""
import argparse
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import stat
import wave
import struct
from array import array

MODEL_SHA256 = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
ROOT = Path(__file__).resolve().parents[1]
HEX = re.compile(r"^[0-9a-f]{64}$")
PSEUDONYM = re.compile(r"^[A-Za-z0-9_-]{3,48}$")
MIN_ENROLL = 24_000
MIN_PROBE = 40_000
MAX_FRAMES = 160_000
MAX_BYTES = 500_000


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _ensure_outside_checkout(path):
    if Path(path).resolve().is_relative_to(ROOT):
        raise ValueError("Private audio, manifests and scores must remain OUTSIDE repository")


def _checked_path(root, relative):
    if not isinstance(relative, str) or not relative or "\x00" in relative:
        raise ValueError("Invalid private WAV path")
    sub = Path(relative)
    if sub.is_absolute() or ".." in sub.parts or sub.suffix.lower() != ".wav":
        raise ValueError("Unsafe or invalid WAV path")
    root = Path(root)
    if root.is_symlink() or not root.is_dir():
        raise ValueError("Untrusted WAV root")
    f = root / sub
    for parent in (root, *tuple(f.parents)):
        if parent == root.parent:
            break
        if parent.is_symlink():
            raise ValueError("Symlinked audio path")
    if f.is_symlink():
        raise ValueError("Symlinked WAV prohibited")
    if not f.resolve().is_relative_to(root.resolve()) or not f.is_file():
        raise ValueError("WAV outside approved local directory or missing")
    return f


def load_checked_pcm(root, relative, expected_sha, min_frames, max_frames=MAX_FRAMES):
    """Return float PCM only in memory, checking real file SHA and WAV format."""
    if not isinstance(expected_sha, str) or not HEX.fullmatch(expected_sha):
        raise ValueError("Missing WAV SHA-256")
    f = _checked_path(root, relative)
    if f.stat().st_size > MAX_BYTES:
        raise ValueError("Oversized private WAV")
    raw = f.read_bytes()
    if len(raw) > MAX_BYTES:
        raise ValueError("Oversized private WAV")
    if _sha(raw) != expected_sha:
        raise ValueError("WAV hash mismatch")
    # Parse the exact immutable bytes that were SHA-verified, not a second
    # filesystem read that can race with an attacker-controlled replacement.
    with wave.open(io.BytesIO(raw), "rb") as w:
        if (w.getnchannels() != 1 or w.getsampwidth() != 2 or
            w.getframerate() != 16_000 or w.getcomptype() != "NONE"):
            raise ValueError("Require 16 kHz 16-bit mono uncompressed PCM WAV")
        count = w.getnframes()
        if not min_frames <= count <= max_frames:
            raise ValueError("Insufficient/oversized voice sample duration")
        pcm = w.readframes(count)
    if len(pcm) != count * 2:
        raise ValueError("Incomplete WAV sample")
    if struct.pack("=H", 1) == b"\x01\x00":
        samples = array("h")
        samples.frombytes(pcm)
    else:
        samples = array("h")
        samples.frombytes(pcm)
        samples.byteswap()
    converted = [value / 32768.0 for value in samples]
    if math.sqrt(sum(value * value for value in converted) / len(converted)) < 1e-4:
        raise ValueError("Silent or near-silent WAV cannot be treated as voice evidence")
    return converted


def verify_model(model):
    """Mandatory SHA check before native inference, no auto-download."""
    model = Path(model)
    if model.is_symlink() or not model.is_file() or not model.name.endswith(".onnx"):
        raise ValueError("Missing verified ONNX model")
    digest = hashlib.sha256()
    with model.open("rb") as stream:
        for block in iter(lambda: stream.read(262144), b""):
            digest.update(block)
    if digest.hexdigest() != MODEL_SHA256:
        raise ValueError("Speaker model SHA-256 differs from Android pinned model")
    return model


def normalized(values):
    values = [float(value) for value in values]
    if len(values) not in range(64, 1025) or any(not math.isfinite(x) for x in values):
        raise ValueError("Invalid native speaker embedding")
    norm2 = sum(x*x for x in values)
    if not math.isfinite(norm2) or norm2 <= 1e-10:
        raise ValueError("Zero or invalid embedding")
    scale = math.sqrt(norm2)
    return [v / scale for v in values]


def enroll_template(embeddings):
    """Mirror SpeakerEnrollmentPolicy.kt: normalise, average, normalise."""
    if len(embeddings) != 3:
        raise ValueError("Exactly three independent enrollment samples needed")
    parts = [normalized(v) for v in embeddings]
    if not all(len(x) == len(parts[0]) for x in parts):
        raise ValueError("Enrollment dimensions differ")
    for i in range(3):
        for j in range(i+1, 3):
            if sum(a*b for a, b in zip(parts[i], parts[j])) < .78:
                raise ValueError("Inconsistent enrollment recordings")
    return normalized([sum(p[i] for p in parts)/3 for i in range(len(parts[0]))])


def similarity(template, probe):
    probe = normalized(probe)
    if len(template) != len(probe):
        raise ValueError("Probe model dimensions differ")
    return max(-1., min(1., sum(a*b for a,b in zip(template, probe))))


def _manifest_entries(manifest):
    if not isinstance(manifest, dict) or manifest.get("schema") != "jarvis.speaker-wav.v1":
        raise ValueError("Incorrect private WAV manifest schema")
    enroll = manifest.get("enrollment")
    trials = manifest.get("trials")
    if not isinstance(enroll, list) or len(enroll) != 3 or not isinstance(trials, list) or not trials:
        raise ValueError("Must supply three enrollment WAVs and actual labeled WAV trials")
    return enroll, trials


def _trial_labels(trial):
    for field in ("trial_id", "kind", "split", "speaker_id", "session_id"):
        if field not in trial:
            raise ValueError("Missing biometric trial provenance")
    if (any(not isinstance(trial[f], str) for f in
            ("trial_id", "kind", "split", "speaker_id", "session_id")) or
        not all(PSEUDONYM.fullmatch(trial[f]) for f in ("trial_id", "speaker_id", "session_id")) or
        trial["kind"] not in ("genuine", "impostor", "replay") or
        trial["split"] not in ("calibration", "holdout") or
        trial.get("consent") is not True or
        (trial["kind"] == "impostor") == (trial["speaker_id"] == "owner")):
        raise ValueError("Invalid labeled/consented trial")
    return {f: trial[f] for f in ("trial_id", "split", "kind", "speaker_id", "session_id")}


def build_scored_rows(manifest, audio_root, embed):
    """Compute real embedding scores, using native callable when invoked by CLI.

    The callable is injectable ONLY for unit tests; public CLI never exposes it
    and always pins the authentic ONNX before creating it.
    """
    enroll, trials = _manifest_entries(manifest)
    hashes = []
    vectors = []
    for row in enroll:
        if not isinstance(row, dict) or set(row) != {"file", "sha256"}:
            raise ValueError("Invalid enrollment WAV metadata")
        if row["sha256"] in hashes:
            raise ValueError("Enrollment WAVs must be three unique recordings")
        pcm = load_checked_pcm(audio_root, row["file"], row["sha256"], MIN_ENROLL)
        hashes.append(row["sha256"])
        vectors.append(embed(pcm))
    owner_template = enroll_template(vectors)
    enrollment_sha = _sha(("jarvis-owner-enrollment-v1\n" + "\n".join(hashes)).encode())
    seen_ids, seen_probe_hashes = set(), set(hashes)
    scored = []
    for trial in trials:
        if not isinstance(trial, dict):
            raise ValueError("Invalid trial row")
        labels = _trial_labels(trial)
        if labels["trial_id"] in seen_ids:
            raise ValueError("Duplicate trial ID")
        seen_ids.add(labels["trial_id"])
        probe_sha = trial.get("recording_sha256")
        if probe_sha in seen_probe_hashes:
            raise ValueError("Probe hash duplicates another recording or enrollment")
        pcm = load_checked_pcm(audio_root, trial.get("file"), probe_sha, MIN_PROBE)
        seen_probe_hashes.add(probe_sha)
        if labels["kind"] == "replay":
            origin_sha = trial.get("replay_source_sha256")
            if origin_sha in hashes or origin_sha == probe_sha:
                raise ValueError("Replay source may not be enrollment or replay probe")
            load_checked_pcm(audio_root, trial.get("replay_source_file"),
                             origin_sha, MIN_ENROLL)
            labels["replay_source_sha256"] = origin_sha
        vector = embed(pcm)
        score = similarity(owner_template, vector)
        labels.update({
            "recording_sha256": probe_sha,
            "enrollment_sha256": enrollment_sha,
            "model_sha256": MODEL_SHA256,
            "consent": True,
            "score": score,
        })
        scored.append(labels)
    return scored


class SherpaNativeSpeakerEmbeddings:
    """Real CPU inference, matching the sherpa-onnx Android extractor."""
    def __init__(self, model):
        import sherpa_onnx
        self.numpy = __import__("numpy")
        config = sherpa_onnx.SpeakerEmbeddingExtractorConfig(
            model=str(verify_model(model)), num_threads=2,
            debug=False, provider="cpu")
        if not config.validate():
            raise ValueError("Invalid pinned speaker model configuration")
        self.engine = sherpa_onnx.SpeakerEmbeddingExtractor(config)

    def __call__(self, pcm):
        stream = self.engine.create_stream()
        waveform = self.numpy.asarray(pcm, dtype=self.numpy.float32)
        stream.accept_waveform(sample_rate=16_000, waveform=waveform)
        stream.input_finished()
        if not self.engine.is_ready(stream):
            raise ValueError("Insufficient voice for native speaker inference")
        return list(self.engine.compute(stream))


def write_private_jsonl(path, rows):
    """Exclusive no-clobber 0600 output, never inside Git checkout."""
    path = Path(path)
    _ensure_outside_checkout(path)
    if not path.parent.is_dir() or path.parent.is_symlink():
        raise ValueError("Create a separate private output directory first")
    flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY
    flags |= getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(str(path), flags, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            for row in rows:
                stream.write(json.dumps(row, sort_keys=True, allow_nan=False) + "\n")
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--audio-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--confirm-consented-local-audio", action="store_true")
    a = parser.parse_args()
    if not a.confirm_consented_local_audio:
        parser.error("Confirm real participant consent before private audio processing")
    _ensure_outside_checkout(a.audio_root)
    _ensure_outside_checkout(a.manifest)
    _ensure_outside_checkout(a.output)
    if a.manifest.is_symlink() or not a.manifest.is_file() or a.manifest.stat().st_size > 2_000_000:
        raise ValueError("Invalid private audio manifest")
    # Always verify the ONNX bytes BEFORE importing or initializing native code.
    verified = verify_model(a.model)
    m = json.loads(a.manifest.read_text(encoding="utf-8"),
                   parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Nonfinite JSON")))
    scored = build_scored_rows(m, a.audio_root, SherpaNativeSpeakerEmbeddings(verified))
    write_private_jsonl(a.output, scored)
    print("LOCAL_REAL_WAV_SCORES_WRITTEN: NOT_CERTIFIED; "
          "no owner authentication or Android permissions activated")


if __name__ == "__main__":
    main()
