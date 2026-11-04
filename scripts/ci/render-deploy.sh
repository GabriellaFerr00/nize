#!/usr/bin/env bash
# Deploys an exact image (by digest) to the Render service and waits until it is live (D-13).
# Usage: render-deploy.sh <image-ref@sha256:...>     Env: RENDER_API_KEY, RENDER_SERVICE_ID.
# The API (not the deploy hook) is used so the pipeline knows when the new version is live
# before the smoke test, and gets the deploy id for rollback.
set -euo pipefail
image="${1:?image reference with digest}"
: "${RENDER_API_KEY:?}" "${RENDER_SERVICE_ID:?}"
api="https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys"
auth=(-H "Authorization: Bearer $RENDER_API_KEY" -H 'Accept: application/json')

started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
status=$(curl -sS -o /tmp/render-deploy.json -w '%{http_code}' -X POST "${auth[@]}" \
  -H 'Content-Type: application/json' -d "$(jq -n --arg i "$image" '{imageUrl: $i}')" "$api")
case "$status" in
  201) deploy_id=$(jq -r '.id' /tmp/render-deploy.json) ;;
  202) # queued: find the deploy created after the request
       sleep 5
       deploy_id=$(curl -fsS "${auth[@]}" "$api?limit=5" \
         | jq -r --arg t "$started" '[.[] | .deploy | select(.createdAt >= $t)] | last | .id') ;;
  *) echo "::error::Render refused the deploy (HTTP $status)"; exit 1 ;;
esac
[[ -n "$deploy_id" && "$deploy_id" != null ]] || { echo "::error::no deploy id returned"; exit 1; }
echo "deploy_id=$deploy_id" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Render deploy $deploy_id started for $image"

for _ in $(seq 1 60); do   # up to 15 minutes
  state=$(curl -fsS "${auth[@]}" "$api/$deploy_id" | jq -r '.status')
  case "$state" in
    live) echo "Render deploy $deploy_id is live"; exit 0 ;;
    build_failed|update_failed|canceled|deactivated|pre_deploy_failed)
      echo "::error::Render deploy $deploy_id ended as $state"; exit 1 ;;
  esac
  echo "Render deploy $deploy_id: $state"; sleep 15
done
echo "::error::Render deploy $deploy_id not live after 15 minutes"
exit 1
