#!/usr/bin/env python3
"""Reproduce upstream KWS model failure in SEPARATE native subprocesses.

CI-only diagnostic. Never modifies release artifacts. Prints fingerprints,
shape/metadata and subprocess exit codes for mobile/nonmobile official
GigaSpeech archives. Native process abort cannot kill this orchestrator.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
from urllib.request import urlopen

MODEL = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
NAMES = (
    "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
    "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
    "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
    "tokens.txt", "bpe.model",
)
BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/"
MAX_ARCHIVE = 24_000_000


def sha(b):
    return hashlib.sha256(b).hexdigest()


def load_archive(variant):
    name = MODEL + variant + ".tar.bz2"
    with urlopen(BASE + name, timeout=90) as f:
        b = f.read(MAX_ARCHIVE + 1)
    if not b or len(b) > MAX_ARCHIVE:
        raise RuntimeError("Archive too large or absent")
    print("ARCHIVE", name, len(b), sha(b), flush=True)
    files = {}
    with tarfile.open(fileobj=io.BytesIO(b), mode="r:bz2") as tf:
        for m in tf:
            k = m.name.removeprefix("./").split("/")[-1]
            if k not in NAMES:
                continue
            if not m.isfile() or k in files or not 0 < m.size < 16_000_000:
                raise RuntimeError("Unsafe/duplicate entry")
            value = tf.extractfile(m).read()
            if len(value) != m.size:
                raise RuntimeError("Truncated ONNX model")
            files[k] = value
    return files


def stage(files, directory):
    import sentencepiece as spm
    for k, v in files.items():
        (directory / k).write_bytes(v)
        print("ASSET", k, len(v), sha(v), flush=True)
    sp = spm.SentencePieceProcessor(model_file=str(directory / "bpe.model"))
    pcs = sp.encode("JARVIS", out_type=str)
    (directory / "keywords.txt").write_text(" ".join(pcs) + " @Jarvis\n")


def describe_model(directory, encoder):
    import onnx
    f = directory / encoder
    m = onnx.load(str(f), load_external_data=False)
    for v in m.graph.input:
        dims = [str(d.dim_value) if d.dim_value else d.dim_param
                for d in v.type.tensor_type.shape.dim]
        print("INPUT", encoder, v.name, dims, flush=True)
    for k in m.metadata_props:
        if k.key in ("T", "decode_chunk_len", "encoder_dims"):
            print("META", encoder, k.key, k.value[:400], flush=True)
    for n in m.graph.node:
        if n.name == "/downsample/Reshape_1":
            print("DOWNSAMPLE_RESHAPE_NODE", encoder,
                  n.input, n.output, flush=True)


def probe(directory, encoder, joiner):
    """Runs inside a child so Ort::Exception SIGABRT does not kill workflow."""
    import numpy as np
    import sherpa_onnx
    kws = sherpa_onnx.KeywordSpotter(
        tokens=str(directory / "tokens.txt"),
        encoder=str(directory / encoder),
        decoder=str(directory / "decoder-epoch-12-avg-2-chunk-16-left-64.onnx"),
        joiner=str(directory / joiner),
        keywords_file=str(directory / "keywords.txt"),
        num_threads=1, sample_rate=16000, provider="cpu",
    )
    s = kws.create_stream()
    pcm = np.zeros(1600, dtype=np.float32)
    for i in range(10):
        s.accept_waveform(16000, pcm)
        for _ in range(64):
            if not kws.is_ready(s):
                break
            kws.decode_stream(s)
    print("PROBE_OK", encoder, flush=True)


def main():
    with tempfile.TemporaryDirectory() as root:
        root = Path(root)
        for variant in ("-mobile", ""):
            d = root / (variant or "full")
            d.mkdir()
            files = load_archive(variant)
            stage(files, d)
            for encoder in (
                "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            ):
                if encoder not in files:
                    continue
                describe_model(d, encoder)
                for joiner in ("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                               "joiner-epoch-12-avg-2-chunk-16-left-64.onnx"):
                    if joiner not in files:
                        continue
                    cmd = [sys.executable, __file__, "--child", str(d), encoder, joiner]
                    result = subprocess.run(cmd, capture_output=True,
                                            text=True, timeout=45)
                    print("PROBE_RESULT",variant or "full",
                          encoder, joiner, "exit", result.returncode, flush=True)
                    print(result.stdout[-1000:], result.stderr[-1300:], flush=True)


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "--child":
        probe(Path(sys.argv[2]), sys.argv[3], sys.argv[4])
    else:
        main()
