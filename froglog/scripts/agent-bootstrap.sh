#!/usr/bin/env bash
# Full Cloud Agent / local dev bootstrap: JDK tools + base Cocoon reference cache.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
  if command -v sudo >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless unzip curl ca-certificates
  else
    echo "agent-bootstrap: install OpenJDK 11+ and re-run" >&2
    exit 1
  fi
fi

"$SCRIPT_DIR/setup-build-tools.sh"
"$SCRIPT_DIR/prepare-cocoon-reference.sh"

echo "agent-bootstrap: done (tools/ + work/ are gitignored; see AGENTS.md)"
