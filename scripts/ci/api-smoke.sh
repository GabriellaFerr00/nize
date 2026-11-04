#!/usr/bin/env bash
# Post-deploy (and in-job) smoke test of the API. Usage: api-smoke.sh <base-url> [max-wait-seconds]
# The wait is long on purpose: a sleeping Render free instance needs tens of seconds to start.
set -euo pipefail
base="${1:?base url}"; max_wait="${2:-300}"

deadline=$(( $(date +%s) + max_wait ))
until body=$(curl -fsS --max-time 30 "$base/actuator/health" 2>/dev/null) && grep -q '"status":"UP"' <<<"$body"; do
  if (( $(date +%s) >= deadline )); then echo "::error::$base/actuator/health not UP after ${max_wait}s"; exit 1; fi
  echo "waiting for $base/actuator/health ..."; sleep 10
done
echo "health: $body"

# Error contract {code, fields} on an invalid request (no state is changed).
status=$(curl -sS --max-time 30 -o /tmp/nize-smoke.json -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -d '{}' "$base/api/auth/login")
if [[ "$status" != 400 ]] || ! jq -e '.code == "VALIDATION_ERROR" and (.fields | type == "object")' /tmp/nize-smoke.json >/dev/null; then
  echo "::error::error contract broken: HTTP $status $(cat /tmp/nize-smoke.json)"; exit 1
fi
echo "error contract ok"
