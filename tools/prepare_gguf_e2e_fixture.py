#!/usr/bin/env python3
"""CI ONLY: acquire one REAL, licensed GGUF to prove JNI token generation.

The model is NOT shipped in JARVIS, imported into production, or retained in
GitHub artifacts. SHA-256 is the upstream Hugging Face LFS file checksum.
"""
import argparse
import hashlib
from pathlib import Path
import urllib.request

SOURCE = (
    "https://huggingface.co/ggml-org/SmolLM2-135M-GGUF/resolve/main/"
    "SmolLM2-135M-Q4_K_M.gguf"
)
MODEL_CARD = "https://huggingface.co/ggml-org/SmolLM2-135M-GGUF"
LICENSE = "Apache-2.0"
EXPECTED_SHA256 = "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"
MAX_BYTES = 120_000_000


def verified(path: Path) -> bool:
    if not path.is_file() or not (40_000_000 < path.stat().st_size <= MAX_BYTES):
        return False
    checksum = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest() == EXPECTED_SHA256


def prepare(destination: Path):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if verified(destination):
        print("GGUF fixture already verified:", EXPECTED_SHA256)
        return
    temp = destination.with_suffix(".downloading")
    try:
        temp.unlink(missing_ok=True)
        checksum = hashlib.sha256()
        total = 0
        request = urllib.request.Request(
            SOURCE, headers={"User-Agent": "JARVIS-CI-GGUF-E2E/1.0"})
        with urllib.request.urlopen(request, timeout=90) as response, temp.open("wb") as f:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_BYTES:
                    raise RuntimeError("GGUF fixture exceeds strict size limit")
                checksum.update(chunk)
                f.write(chunk)
        if total <= 40_000_000 or checksum.hexdigest() != EXPECTED_SHA256:
            raise RuntimeError(
                "Real GGUF checksum/size mismatch; refusing simulated generation")
        temp.replace(destination)
        assert verified(destination), "Downloaded file verification changed"
        print(f"GGUF_E2E_FIXTURE_VERIFIED sha256={EXPECTED_SHA256} bytes={total} license={LICENSE}")
    finally:
        temp.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.dest.resolve())
