#!/usr/bin/env bash
# Smoke test of a Cloudflare Pages deployment. Usage: frontend-smoke.sh <site-url> [check-api: true|false]
set -euo pipefail
site="${1:?site url}"; check_api="${2:-true}"
fail() { echo "::error::$*"; exit 1; }

# A fresh deployment can answer 5xx (e.g. 522) for a short while: retry up to 2 minutes.
for attempt in $(seq 1 12); do
  if headers=$(curl -fsS --max-time 30 -D - -o /tmp/nize-index.html "$site/"); then break; fi
  (( attempt == 12 )) && fail "$site/ did not answer 200 within 2 minutes"
  echo "waiting for $site to propagate ..."; sleep 10
done
for h in content-security-policy strict-transport-security x-content-type-options referrer-policy; do
  grep -qi "^$h:" <<<"$headers" || fail "missing header $h on $site/"
done
grep -qi "^content-security-policy:.*script-src 'self'" <<<"$headers" || fail "CSP without script-src 'self'"
if grep -qi "^content-security-policy:.*script-src[^;]*unsafe-inline" <<<"$headers"; then fail "CSP allows inline scripts"; fi
grep -q '<div id="root">' /tmp/nize-index.html || fail "index.html not served at /"

# Deep link of the SPA (_redirects) must return the same index.html with 200.
curl -fsS --max-time 30 -o /tmp/nize-deep.html "$site/sign-in" || fail "/sign-in did not return 200"
cmp -s /tmp/nize-index.html /tmp/nize-deep.html || fail "/sign-in did not return index.html"

if [[ "$check_api" == true ]]; then
  # Through the Pages Function proxy (D-15); allows for a Render cold start.
  for _ in $(seq 1 18); do
    status=$(curl -sS --max-time 60 -o /tmp/nize-proxy.json -w '%{http_code}' -X POST \
      -H 'Content-Type: application/json' -d '{}' "$site/api/auth/login" || true)
    [[ "$status" == 400 ]] && break
    echo "waiting for the API behind $site/api (HTTP $status) ..."; sleep 10
  done
  jq -e '.code == "VALIDATION_ERROR"' /tmp/nize-proxy.json >/dev/null || fail "/api proxy not answering: HTTP $status"
fi
echo "frontend smoke ok: $site"
