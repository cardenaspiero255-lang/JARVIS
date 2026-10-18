#!/usr/bin/env python3
"""Pin the exact upstream 3D-Speaker ONNX bytes for offline Android enrollment.

Source: k2-fsa/sherpa-onnx speaker-recongition-models release.
Independent SHA-256 cross-check: csukuangfj/speaker-embedding-models LFS.
No voice recordings are downloaded, persisted or sent anywhere.
"""
import argparse
import hashlib
from pathlib import Path
import tempfile
import os
from urllib.request import Request, urlopen

NAME = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
MODEL_SHA256 = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
SOURCE = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/"
          "speaker-recongition-models/" + NAME)
MAX_SIZE = 43_000_000
MIN_SIZE = 39_000_000


def prepare(root: Path):
    target_dir = root / "verified_sherpa_speaker"
    target = target_dir / "model.onnx"
    if target.is_file() and hashlib.sha256(target.read_bytes()).hexdigest() == MODEL_SHA256:
        return
    target_dir.mkdir(parents=True, exist_ok=True)
    fd, path = tempfile.mkstemp(prefix=".speaker-", dir=target_dir)
    try:
        digest = hashlib.sha256()
        with os.fdopen(fd, "wb") as output:
            with urlopen(Request(SOURCE, headers={"User-Agent": "JARVIS-offline-build/1"}),
                         timeout=60) as stream:
                if stream.url.split(":", 1)[0] != "https":
                    raise ValueError("Speaker model redirected away from HTTPS")
                size = 0
                while True:
                    chunk = stream.read(65536)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > MAX_SIZE:
                        raise ValueError("Oversized speaker model")
                    digest.update(chunk)
                    output.write(chunk)
        if size < MIN_SIZE or digest.hexdigest() != MODEL_SHA256:
            raise ValueError("Official speaker embedding model SHA-256 mismatch")
        os.replace(path, target)
    finally:
        if os.path.exists(path):
            os.remove(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    prepare(parser.parse_args().dest)


if __name__ == "__main__":
    main()
