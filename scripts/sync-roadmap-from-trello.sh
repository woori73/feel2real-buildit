#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$HOME/.config/f2r/env" ]]; then
  # shellcheck disable=SC1090
  source "$HOME/.config/f2r/env"
fi
exec python3 "$ROOT/scripts/sync-roadmap-from-trello.py" "$@"
