#!/usr/bin/env python3
"""Materialize pinned #863 Git blobs for isolated import; no network, executable or original changes."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

PINNED_REF = "09d62dee982e2e89e3653c90b1ee42f05bff4f9d"
PREFIX = "docs/ai-agent-production/"

def materialize(destination):
    root = Path(destination).resolve()
    root.mkdir(parents=True, exist_ok=True)
    tree = subprocess.check_output(["git", "ls-tree", "-r", "-z", PINNED_REF, "--", PREFIX])
    records = []
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        meta, raw_path = entry.split(b"\t", 1)
        mode, kind, oid = meta.decode().split()
        if mode != "100644" or kind != "blob":
            raise ValueError("Only non-executable regular files are accepted")
        relative = Path(raw_path.decode()[len(PREFIX):])
        target = root / relative
        if not target.resolve().is_relative_to(root) or any(p.is_symlink() for p in [target, *target.parents] if p.is_relative_to(root)):
            raise ValueError("Unsafe source path")
        data = subprocess.check_output(["git", "cat-file", "blob", oid])
        if target.exists() and target.read_bytes() != data:
            raise ValueError("Existing materialization differs; source is never overwritten")
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            target.write_bytes(data)
            target.chmod(0o444)
        records.append(dict(path=str(relative), sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
    if len(records) != 536:
        raise ValueError("Expected exactly 536 pinned source files")
    print(json.dumps(dict(sourceRef=PINNED_REF, files=len(records), bytes=sum(r["size"] for r in records))))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination")
    materialize(parser.parse_args().destination)
