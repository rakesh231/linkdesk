#!/usr/bin/env bash
set -euo pipefail
base="${BASE_URL:-http://localhost:8092}"
alias="smoke-$(date +%s)-$$"
folder="$(mktemp -d)"
trap 'rm -rf "$folder"' EXIT
request() {
  local expected="$1"; shift
  local actual
  actual=$(curl --silent --show-error --max-time 15 -D "$folder/headers" -o "$folder/body" -w '%{http_code}' "$@")
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected HTTP $expected, got $actual" >&2
    cat "$folder/body" >&2
    exit 1
  fi
}
request 200 "$base/actuator/health"
request 201 -H 'Content-Type: application/json' -d "{\"url\":\"https://example.com/demo\",\"alias\":\"$alias\"}" "$base/api/links"
request 302 --head "$base/s/$alias"
request 200 "$base/api/links/$alias"
grep -Eq '"visits"[[:space:]]*:[[:space:]]*0' "$folder/body"
request 302 "$base/s/$alias"
grep -qi '^Location: https://example.com/demo' "$folder/headers"
request 200 "$base/api/links/$alias"
grep -Eq '"visits"[[:space:]]*:[[:space:]]*1' "$folder/body"
request 200 "$base/api/links/$alias/stats"
request 409 -H 'Content-Type: application/json' -d "{\"url\":\"https://example.com\",\"alias\":\"$alias\"}" "$base/api/links"
request 400 -H 'Content-Type: application/json' -d '{"url":"javascript:alert(1)"}' "$base/api/links"
request 204 -X DELETE "$base/api/links/$alias"
request 410 "$base/s/$alias"
request 204 -X DELETE "$base/api/links/$alias"
request 404 "$base/s/not-created-$$"
echo 'PASS: health, creation, HEAD, redirect, counts, stats, conflict, URL validation, disable and missing links.'
