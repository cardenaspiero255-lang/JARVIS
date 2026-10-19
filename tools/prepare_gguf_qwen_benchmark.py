#!/usr/bin/env python3
"""CI-only download of official, pinned Qwen2.5-0.5B Instruct Q4_K_M GGUF.

Never included in the shipping APK, requires a reproducible binary hash,
and refuses redirects that deliver the wrong file or unexpected size.
"""
import argparse
import hashlib
from pathlib import Path
from urllib.request import Request, urlopen

SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
FILE_BYTES = 491_400_032
MAX_SIZE = 505_000_000
MODEL_URL = (
    "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/"
    "df5bf01389a39c743ab467d734bf501681e041c5/"
    "qwen2.5-0.5b-instruct-q4_k_m.gguf"
)


def verified(file: Path) -> bool:
    if not file.is_file() or file.stat().st_size != FILE_BYTES:
        return False
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest() == SHA256


def prepare(dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if verified(dest):
        print(f"QWEN_GGUF_SHA_VERIFIED sha256={SHA256} bytes={FILE_BYTES}")
        return
    partial = dest.with_suffix(".partial")
    partial.unlink(missing_ok=True)
    try:
        digest = hashlib.sha256()
        length = 0
        request = Request(MODEL_URL, headers={"User-Agent": "JARVIS-Qwen-CI/1.0"})
        with urlopen(request, timeout=180) as reply, partial.open("wb") as out:
            for chunk in iter(lambda: reply.read(1024 * 1024), b""):
                length += len(chunk)
                if length > MAX_SIZE:
                    raise ValueError("Official GGUF exceeded 505 MB bounded download")
                digest.update(chunk)
                out.write(chunk)
        if length != FILE_BYTES or digest.hexdigest() != SHA256:
            raise ValueError("Qwen GGUF SHA-256 or exact byte count mismatch")
        partial.replace(dest)
        assert verified(dest)
        print(f"QWEN_GGUF_SHA_VERIFIED sha256={SHA256} bytes={length} license=Apache-2.0")
    finally:
        partial.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.dest.resolve())
