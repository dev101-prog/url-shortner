#!/usr/bin/env bash
# Provision an API key for an owner (design §7.8, URL-NFR-4.2). There is no sign-up flow: an
# operator runs this against the local Docker database. The raw key is printed ONCE to stdout and
# is never written to disk or logs; only its SHA-256 is stored.
# Usage: bash scripts/create-api-key.sh <owner-name> [label]
#        (creates the owner if it does not exist; CONTAINER defaults to urlshortener-db)
set -euo pipefail
OWNER=${1:?usage: create-api-key.sh <owner-name> [label]}
LABEL=${2:-"key for ${OWNER}"}
CONTAINER=${CONTAINER:-urlshortener-db}
[[ "$OWNER" =~ ^[A-Za-z0-9_.-]{1,100}$ ]] || { echo "owner name must match ^[A-Za-z0-9_.-]{1,100}\$" >&2; exit 2; }
[[ ${#LABEL} -le 100 ]] || { echo "label must be at most 100 characters" >&2; exit 2; }

KEY=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')
if command -v sha256sum >/dev/null 2>&1; then
  HASH=$(printf '%s' "$KEY" | sha256sum | cut -c1-64)
else
  HASH=$(printf '%s' "$KEY" | shasum -a 256 | cut -c1-64)
fi
PREFIX=${KEY:0:12}

# Values are passed as psql variables and quoted with :'var', never interpolated into SQL text.
docker exec -i "$CONTAINER" psql -U postgres -d urlshortener -q -v ON_ERROR_STOP=1 \
  -v owner="$OWNER" -v hash="$HASH" -v prefix="$PREFIX" -v label="$LABEL" >/dev/null <<'SQL'
INSERT INTO owners (name) VALUES (:'owner') ON CONFLICT (name) DO NOTHING;
INSERT INTO api_keys (owner_id, key_hash, key_prefix, label)
SELECT id, :'hash', :'prefix', :'label' FROM owners WHERE name = :'owner';
SQL

echo "$KEY"
