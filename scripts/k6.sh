#!/usr/bin/env bash
# Run a k6 script on the compose network; the script is fed on stdin (no bind mounts).
# Usage: bash scripts/k6.sh perf/k6/redirect-hit.js [extra k6 args, e.g. -e VUS=5 -e STEADY=10s]
# Start the app first (for real perf runs with the overrides in docs/perf/README.md); --no-deps
# leaves the running app and its configuration untouched.
set -euo pipefail
cd "$(dirname "$0")/.."
SCRIPT=${1:?usage: k6.sh <script.js> [k6 args...]}
shift
exec docker compose run --rm --no-deps -T k6 run -e BASE_URL=http://app:8080 "$@" - < "$SCRIPT"
