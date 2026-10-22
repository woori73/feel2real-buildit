#!/usr/bin/env bash
# Read-only diagnostics; never trace a secret-bearing environment.
set +x
set -euo pipefail
umask 077
exec python3 "$(dirname "$0")/a1-precheck.py"
