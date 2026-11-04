#!/usr/bin/env bash
# Deploy gate (design.md "CI/CD"): waits until the given workflows finished successfully for the
# same commit. Separate workflows cannot use `needs`, so the deploy jobs call this first.
# Usage: wait-for-workflows.sh <commit-sha> "<workflow name>"...   Env: GH_TOKEN, GITHUB_REPOSITORY
set -euo pipefail
sha="${1:?commit sha}"; shift
now() { date +%s; }
deadline=$(( $(now) + ${WAIT_MINUTES:-60} * 60 ))
appear_deadline=$(( $(now) + 10 * 60 ))   # a workflow that never started for this commit is a failure

for workflow in "$@"; do
  while :; do
    run=$(gh run list --repo "$GITHUB_REPOSITORY" --commit "$sha" --workflow "$workflow" --limit 1 \
      --json status,conclusion,url --jq '.[0] // empty')
    if [[ -z "$run" ]]; then
      if (( $(now) >= appear_deadline )); then
        echo "::error::workflow '$workflow' did not run for $sha"; exit 1
      fi
    elif [[ $(jq -r .status <<<"$run") == completed ]]; then
      conclusion=$(jq -r .conclusion <<<"$run")
      if [[ "$conclusion" == success ]]; then
        echo "$workflow: success"; break
      fi
      echo "::error::$workflow ended as $conclusion: $(jq -r .url <<<"$run")"; exit 1
    fi
    if (( $(now) >= deadline )); then
      echo "::error::timed out waiting for $workflow"; exit 1
    fi
    echo "waiting for $workflow on $sha ..."; sleep 30
  done
done
