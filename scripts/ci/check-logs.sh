#!/usr/bin/env bash
# NFR-008: fails if the application log contains a password, an e-mail, a token or a secret.
# Usage: check-logs.sh <log-file> [literal values to search for...]
set -euo pipefail
log="${1:?log file}"; shift
[[ -f "$log" ]] || { echo "::error::log file $log not found"; exit 1; }

failed=0
for value in "$@"; do
  [[ -z "$value" ]] && continue
  if grep -qF -- "$value" "$log"; then
    echo "::error::a secret or personal value (${#value} chars) appears in $log"; failed=1
  fi
done
# Any JWT (header.payload.signature starting with {"alg":...).
if grep -qE 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.' "$log"; then
  echo "::error::a JWT appears in $log"; failed=1
fi
# Any e-mail address of the synthetic users.
if grep -qiE '[A-Za-z0-9._%+-]+@example\.(test|com)' "$log"; then
  echo "::error::an e-mail address appears in $log"; failed=1
fi
(( failed == 0 )) && echo "log clean: $log"
exit "$failed"
