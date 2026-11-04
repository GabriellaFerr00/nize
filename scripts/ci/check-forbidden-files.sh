#!/usr/bin/env bash
# Blocks database dumps, backups, spreadsheets and env files in the repository (real data must
# never be committed). Only Flyway migrations and the CI SQL checks may be .sql files.
set -euo pipefail
bad=$(git ls-files -- '*.sql' '*.dump' '*.bak' '*.csv' '*.tsv' '*.xlsx' '*.sqlite' '*.db' '*.env' '.env' '.env.*' \
  | grep -vE '^backend/src/main/resources/db/migration/V[0-9]+__[a-z0-9_]+\.sql$' \
  | grep -vE '^scripts/ci/[a-z0-9-]+\.sql$' \
  | grep -vE '^\.env\.example$' \
  | grep -vE '^\.zap/rules\.tsv$' || true)
if [[ -n "$bad" ]]; then
  echo "::error::files that may hold real data are tracked:"; echo "$bad"; exit 1
fi
echo "no forbidden files tracked"
