#!/usr/bin/env bash
# Run the Postman collection with Newman on the compose network (no bind mounts).
# Starts postgres + app if needed. Extra arguments replace the default reporter options, e.g.
#   bash scripts/postman.sh --reporters cli,junit
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# -gt 0 ]]; then
  exec docker compose run --rm --build newman run url-shortener.postman_collection.json \
    -e local.postman_environment.json --env-var baseUrl=http://app:8080 "$@"
fi
exec docker compose run --rm --build newman
