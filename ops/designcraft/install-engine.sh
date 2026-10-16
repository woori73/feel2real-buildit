#!/usr/bin/env bash
set -euo pipefail
# Explicit destination required; never changes /opt symlinks, env, services or DB.
DEST=${1:?usage: install-engine.sh EMPTY_DESTINATION}
VERSION=0.8.0
SHA256=96a835fd89ebf6970b7dec93652e1b2f081123a69c78e19998ca6c84ca322d77
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo "Only Linux x86_64 is pinned" >&2; exit 1; }
[[ ! -e "$DEST" ]] || { echo "Destination must not exist" >&2; exit 1; }
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
curl --fail --location --retry 2 --connect-timeout 15 --max-time 180 \
  "https://github.com/storytold/designcraft/releases/download/v${VERSION}/designcraft-${VERSION}-linux-x86_64.tar.gz" -o "$TEMP/engine.tar.gz"
printf '%s  %s\n' "$SHA256" "$TEMP/engine.tar.gz" | sha256sum --check --status
tar --no-same-owner -xzf "$TEMP/engine.tar.gz" -C "$TEMP"
ROOT="$TEMP/designcraft-${VERSION}-linux-x86_64"
[[ $("$ROOT/bin/designcraft-cli" --version) == "designcraft-cli $VERSION" ]]
mkdir -p "$(dirname "$DEST")"
# The whole release includes MIT/Apache and font license notices.
mv "$ROOT" "$DEST"
printf 'Installed %s; set PUBLISHING_CLI_PATH=%s/bin/designcraft-cli after approval\n' "$VERSION" "$DEST"
