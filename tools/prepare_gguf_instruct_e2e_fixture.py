#!/usr/bin/env python3
"""Fetch an authentic, hash-pinned Apache-2.0 Instruct GGUF for Android QA ONLY.

No weights are put into the production APK or committed to the repository.
"""
import argparse
import hashlib
from pathlib import Path
from urllib.request import Request, urlopen

MODEL_URL = (
    "https://huggingface.co/unsloth/SmolLM2-135M-Instruct-GGUF/"
    "resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
)
MODEL_CARD = "https://huggingface.co/unsloth/SmolLM2-135M-Instruct-GGUF"
LICENSE = "apache-2.0"
SHA256 = "ed5fa30c487b282ec156c29062f1222e5c20875a944ac98289dbd242e947f747"
MIN_SIZE = 90_000_000
MAX_SIZE = 125_000_000


def fingerprint(path: Path):
    if not path.is_file() or not MIN_SIZE <= path.stat().st_size <= MAX_SIZE:
        return None
    hash_value = hashlib.sha256()
    with path.open("rb") as source:
        for part in iter(lambda: source.read(1048576), b""):
            hash_value.update(part)
    return hash_value.hexdigest()


def prepare(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if fingerprint(path) == SHA256:
        print("INSTRUCT_GGUF_VERIFIED sha256=" + SHA256)
        return
    temporary = path.with_suffix(".partial")
    temporary.unlink(missing_ok=True)
    try:
        checksum = hashlib.sha256()
        copied = 0
        request = Request(MODEL_URL, headers={"User-Agent": "Jarvis-GGUF-CI/1.0"})
        with urlopen(request, timeout=90) as stream, temporary.open("wb") as out:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                copied += len(chunk)
                if copied > MAX_SIZE:
                    raise ValueError("GGUF file exceeded strict 125 MB cap")
                checksum.update(chunk)
                out.write(chunk)
        if copied < MIN_SIZE or checksum.hexdigest() != SHA256:
            raise ValueError("GGUF source failed pinned SHA-256 verification")
        temporary.replace(path)
        assert fingerprint(path) == SHA256
        print(f"INSTRUCT_GGUF_VERIFIED sha256={SHA256} bytes={copied} license={LICENSE}")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.dest.resolve())
