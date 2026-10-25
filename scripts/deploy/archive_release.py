#!/usr/bin/env python3
"""Archive only deployment payloads; never touch live keys, IAM, DB, or cleanup rules."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from datetime import datetime, timezone


OWNERS = {
    "f2r-deploy-425805163960": "425805163960",
    "feel2real-buildit-deploy": "067573916809",
}


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result


def put(bucket, key, path):
    checksum = digest(path)
    expected = base64.b64encode(checksum.digest()).decode("ascii")
    # S3 verifies the supplied checksum. A repeat run cannot overwrite an existing key.
    result = subprocess.run([
        "aws", "s3api", "put-object", "--bucket", bucket,
        "--expected-bucket-owner", OWNERS[bucket], "--key", key,
        "--body", str(path), "--if-none-match", "*",
        "--server-side-encryption", "AES256", "--checksum-sha256", expected,
        "--metadata", f"sha256={checksum.hexdigest()}",
        "--query", "ChecksumSHA256", "--output", "text",
    ], check=True, capture_output=True, text=True)
    if result.stdout.strip() != expected:
        raise RuntimeError(f"S3 checksum acknowledgement mismatch: {path.name}")
    return {"key": key, "sha256": checksum.hexdigest(), "bytes": path.stat().st_size}


def archive(bucket, sha, run_id, attempt, jar, wiki, deploy_script, receipt):
    if bucket not in OWNERS or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid deployment bucket or full commit SHA")
    if not all(re.fullmatch(r"[1-9][0-9]*", str(n)) for n in (run_id, attempt)):
        raise ValueError("Invalid Actions run ID or attempt")
    payloads = [("app.jar", Path(jar)), ("buildit-wiki-backend.tgz", Path(wiki)),
                ("deploy-buildit.sh", Path(deploy_script))]
    # Validate every file before the first remote write. No globbing or env/key uploads.
    for name, path in payloads:
        if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 5 * 1024**3:
            raise ValueError(f"Invalid deployment payload: {name}")
    prefix = f"releases/{sha}/{run_id}-{attempt}"
    manifest = {"format": 1, "commit": sha, "run_id": str(run_id),
                "attempt": str(attempt), "created_utc": datetime.now(timezone.utc).isoformat(),
                "artifacts": []}
    for name, path in payloads:
        manifest["artifacts"].append(put(bucket, f"{prefix}/{name}", path))
    with tempfile.TemporaryDirectory(prefix="f2r-release-") as folder:
        marker = Path(folder) / "manifest.json"
        marker.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        put(bucket, f"{prefix}/manifest.json", marker)
    result = {"bucket": bucket, "prefix": prefix, "complete": True, **manifest}
    Path(receipt).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Release archived: {prefix} (3 payloads, SHA-256 verified; manifest last)")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"\n### Release archive\n\n`{prefix}` — 3 payloads; SHA-256 verified. "
                          "Manifest is the completion marker. No env/key files archived.\n")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("bucket", "sha", "run-id", "attempt", "jar", "wiki", "deploy-script", "receipt"):
        parser.add_argument(f"--{option}", required=True)
    args = parser.parse_args()
    try:
        archive(**vars(args))
    except subprocess.CalledProcessError as error:
        # Do not echo arbitrary AWS error bodies into public logs.
        parser.exit(1, f"Release archive failed (AWS exit {error.returncode}); live deployment not started.\n")
    except (ValueError, RuntimeError, OSError) as error:
        parser.exit(1, f"Release archive failed: {error}\n")


if __name__ == "__main__":
    main()
