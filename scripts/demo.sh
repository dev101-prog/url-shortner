#!/usr/bin/env bash
# End-to-end walkthrough of CUJ-1..CUJ-4 (design §7.7), re-runnable against the same database.
# Same steps as the design; differences: a unique alias per run (so repeated runs do not 409 on
# step 1), an expiry computed relative to today, and PASS/FAIL per check with a non-zero exit code
# when any expected status is wrong.
# Usage: bash scripts/demo.sh            (BASE defaults to http://localhost:8080)
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
ALICE="X-API-Key: demo-key-alice-0001"
BOB="X-API-Key: demo-key-bob-0002"
RUN_ID="$(date +%s)-$RANDOM"
ALIAS="fall-promo-${RUN_ID}"                      # 4-32 chars of [A-Za-z0-9_-]
EXPIRES=$(date -u -v+30d +%Y-%m-%dT00:00:00Z 2>/dev/null || date -u -d '+30 days' +%Y-%m-%dT00:00:00Z)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
PASSED=0; FAILED=0

# check <label> <expected-status> <curl args...>; prints the body (if any) and PASS/FAIL
check() {
  local label=$1 expected=$2; shift 2
  local status
  status=$(curl -s -o "$TMP/body" -w '%{http_code}' "$@") || true   # curl prints 000 if it cannot connect
  if [[ "$status" == "$expected" ]]; then
    PASSED=$((PASSED + 1)); printf 'PASS  %-28s %s\n' "$label" "$status"
  else
    FAILED=$((FAILED + 1)); printf 'FAIL  %-28s got %s, expected %s\n' "$label" "$status" "$expected"
  fi
  if [[ -s "$TMP/body" ]]; then cut -c1-300 "$TMP/body"; echo; fi
}

echo "== Health"
check "healthz" 200 "$BASE/healthz"
check "readyz" 200 "$BASE/readyz"

echo "== Create random code"
check "create random code" 201 -X POST "$BASE/api/v1/links" -H "$ALICE" -H 'Content-Type: application/json' \
  -d '{"url":"https://openjdk.org/projects/jdk/21/"}'
CODE=$(sed -nE 's/.*"code":"([^"]+)".*/\1/p' "$TMP/body" 2>/dev/null || true)
CODE=${CODE:-missing-code}
echo "   code=$CODE"

echo "== Redirect ($CODE)"
check "redirect 302" 302 "$BASE/$CODE"

echo "== Custom alias ($ALIAS)"
check "custom alias 201" 201 -X POST "$BASE/api/v1/links" -H "$ALICE" -H 'Content-Type: application/json' \
  -d "{\"url\":\"https://example.org/promo\",\"alias\":\"$ALIAS\",\"expires_at\":\"$EXPIRES\"}"
check "alias conflict 409" 409 -X POST "$BASE/api/v1/links" -H "$BOB" -H 'Content-Type: application/json' \
  -d "{\"url\":\"https://example.org/x\",\"alias\":\"$ALIAS\"}"
check "reserved alias 422" 422 -X POST "$BASE/api/v1/links" -H "$ALICE" -H 'Content-Type: application/json' \
  -d '{"url":"https://example.org/x","alias":"Docs"}'
check "bad scheme 422" 422 -X POST "$BASE/api/v1/links" -H "$ALICE" -H 'Content-Type: application/json' \
  -d '{"url":"javascript:alert(1)"}'
check "revoked key 401" 401 -X POST "$BASE/api/v1/links" \
  -H 'X-API-Key: demo-key-revoked-0003' -H 'Content-Type: application/json' -d '{"url":"https://a.io"}'
check "expired 410" 410 "$BASE/Xy9Kp2Q"
check "inactive 404" 404 "$BASE/Qm4Rt8Z"
check "not owner 404" 404 -H "$ALICE" "$BASE/api/v1/links/bob-blog"
sleep 2
check "metadata 200" 200 -H "$ALICE" "$BASE/api/v1/links/$CODE"
check "stats (seeded) 200" 200 -H "$ALICE" "$BASE/api/v1/links/aB3dE7x/stats"
check "deactivate 204" 204 -X DELETE -H "$ALICE" "$BASE/api/v1/links/$CODE"
check "redirect after delete 404" 404 "$BASE/$CODE"

echo "== Summary: $PASSED passed, $FAILED failed"
[[ $FAILED -eq 0 ]]
