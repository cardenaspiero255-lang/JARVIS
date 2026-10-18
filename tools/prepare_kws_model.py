#!/usr/bin/env python3
"""Prepare OFFLINE Android KWS assets from the upstream sherpa-onnx release.

The GitHub archive is treated as untrusted bytes. Extract only known regular
files into a staging directory after matching pinned per-file SHA-256.
Reproducible JARVIS BPE keywords are generated at build time, not guessed.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
from urllib.request import Request, urlopen

MODEL = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile"
SOURCE = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/"
          f"kws-models/{MODEL}.tar.bz2")
# These pinned component hashes are reviewed against the actual GitHub asset
# by this downloader. A mismatch fails the APK build, never auto-updates pins.
REQUIRED = {
    "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx": "7d5a25fbc14b423074fe0c58e1be388ccf3f1a815c649980232a0c47f81875e4",
    "decoder-epoch-12-avg-2-chunk-16-left-64.onnx": "f61ebd3eed3773a44d088d53dfae92dbb6aec4839f4dcaee2d402414741663a3",
    "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx": "eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c",
    "tokens.txt": "fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53",
    "bpe.model": "c8a2a0129c4ab8e463164c142f82d25649661b122c8cd0b7aab5c9e80b90ad24",
}
MAX_COMPRESSED = 30_000_000
MAX_ENTRY = 18_000_000


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def download() -> bytes:
    request = Request(SOURCE, headers={"User-Agent": "JARVIS-verifiable-build/1"})
    with urlopen(request, timeout=50) as res:
        if res.url.split(":", 1)[0] != "https":
            raise ValueError("The upstream release redirected to non-HTTPS")
        chunks = []
        total = 0
        while True:
            b = res.read(65536)
            if not b:
                break
            total += len(b)
            if total > MAX_COMPRESSED:
                raise ValueError("KWS download exceeds compressed limit")
            chunks.append(b)
    return b"".join(chunks)


def verified_files(archive_bytes: bytes) -> dict[str, bytes]:
    found = {}
    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:bz2") as tar:
        for member in tar:
            name = member.name.removeprefix("./")
            # Official archives may start with "./" or use the non-mobile
            # directory name. Never EXTRACT arbitrary paths or test data.
            parts = name.split("/")
            relative = parts[-1]
            if relative not in REQUIRED:
                continue
            allowed_roots = ([], [MODEL], [MODEL.removesuffix("-mobile")])
            if parts[:-1] not in allowed_roots:
                raise ValueError("Unexpected archive path for pinned component: " + name)
            if not member.isfile() or relative in found or member.size not in range(1, MAX_ENTRY + 1):
                raise ValueError("Non-regular/duplicate/oversized KWS asset")
            payload = tar.extractfile(member).read(MAX_ENTRY + 1)
            if len(payload) != member.size or sha(payload) != REQUIRED[relative]:
                raise ValueError("Official release model component SHA-256 mismatch: " + relative)
            found[relative] = payload
    if found.keys() != REQUIRED.keys():
        raise ValueError("Required model assets absent: " + str(REQUIRED.keys() - found.keys()))
    return found


def generate_keyword(files: dict[str, bytes], temp_dir: Path) -> bytes:
    try:
        import sentencepiece as spm
    except ImportError as exc:
        raise RuntimeError("Install build-only dependency: pip install sentencepiece==0.2.1") from exc
    vocab = temp_dir / "bpe.model"
    vocab.write_bytes(files["bpe.model"])
    tokenizer = spm.SentencePieceProcessor(model_file=str(vocab))
    pieces = tokenizer.encode("JARVIS", out_type=str)
    token_set = {line.split()[0] for line in files["tokens.txt"].decode("utf-8").splitlines()
                 if len(line.split()) == 2}
    if not pieces or any(p not in token_set or p == "<unk>" for p in pieces):
        raise ValueError("JARVIS is not representable by the official BPE/tokens model")
    return (" ".join(pieces) + " @Jarvis\n").encode("utf-8")


def prepare(destination: Path, payload: bytes | None = None) -> None:
    out = destination / "verified_sherpa_kws"
    if out.is_dir() and all(
        (out / name).is_file() and sha((out / name).read_bytes()) == digest
        for name, digest in REQUIRED.items()
    ) and (out / "keywords.txt").is_file():
        return
    payload = download() if payload is None else payload
    files = verified_files(payload)
    destination.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination) as tmp:
        stage = Path(tmp) / "verified_sherpa_kws"
        stage.mkdir()
        for name, data in files.items():
            (stage / name).write_bytes(data)
        keywords = generate_keyword(files, stage)
        (stage / "keywords.txt").write_bytes(keywords)
        # No executable archives, symlinks, or unverified network data survive.
        if out.exists():
            shutil.rmtree(out)
        os.replace(stage, out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.dest)


if __name__ == "__main__":
    main()
