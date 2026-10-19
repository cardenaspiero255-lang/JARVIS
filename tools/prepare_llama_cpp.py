#!/usr/bin/env python3
"""Obtain the exact audited upstream llama.cpp revision for local NDK builds.

Only the source is fetched on the BUILD machine, never the user's phone.
GGUF weights are not downloaded and must be explicitly imported by the user.
"""
import argparse
from pathlib import Path
import subprocess
import sys

UPSTREAM = "https://github.com/ggml-org/llama.cpp.git"
PINNED_COMMIT = "31288c9c66e59377f7788d920678a565bb89dd37"


def call(*argv):
    subprocess.run(argv, check=True, timeout=300)


def output(*argv):
    return subprocess.check_output(argv, text=True, timeout=60).strip()


def prepare(destination):
    if destination.is_dir() and not (destination / ".git").exists():
        # Gradle may precreate outputs.dir before invoking Exec. Only an
        # entirely empty, auto-created folder may be removed; fail closed
        # rather than deleting arbitrary user-owned files.
        if any(destination.iterdir()):
            raise RuntimeError("Refusing to replace a non-empty untracked upstream folder")
        destination.rmdir()
    if destination.exists():
        if not (destination / ".git").is_dir():
            raise RuntimeError("Refusing to use a non-Git upstream folder")
        if output("git", "-C", str(destination), "rev-parse", "HEAD") != PINNED_COMMIT:
            raise RuntimeError("Upstream source has a different commit; not replacing it")
        if output("git", "-C", str(destination), "status", "--porcelain"):
            raise RuntimeError("Upstream source is modified; refusing to build")
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        call("git", "clone", "--filter=blob:none", "--no-checkout", UPSTREAM, str(destination))
        call("git", "-C", str(destination), "fetch", "--depth=1", "origin", PINNED_COMMIT)
        call("git", "-C", str(destination), "checkout", "--detach", PINNED_COMMIT)
    if output("git", "-C", str(destination), "rev-parse", "HEAD") != PINNED_COMMIT:
        raise RuntimeError("Pinned source HEAD verification failed")
    for filename in ("include/llama.h", "CMakeLists.txt", "LICENSE"):
        if not (destination / filename).is_file():
            raise RuntimeError("Pinned llama.cpp source is incomplete: " + filename)
    print("Pinned native LLM source verified:", PINNED_COMMIT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.dest.resolve())


if __name__ == "__main__":
    main()
