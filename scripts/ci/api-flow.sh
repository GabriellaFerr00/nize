#!/usr/bin/env bash
# Backend integration flow against the API running on MySQL (profile prod, runtime user): exercises
# every write path so H2/MySQL differences show up (types, constraints, ddl-auto: validate).
# Usage: api-flow.sh <base-url>     Synthetic user only; requires curl and jq.
set -euo pipefail
base="${1:?base url}"
body=$(mktemp)
mask() { [[ -n "${GITHUB_ACTIONS:-}" ]] && echo "::add-mask::$1"; return 0; }

# step EXPECTED_STATUS METHOD PATH [JSON] — fails the script on any other status
step() {
  local expected="$1" method="$2" path="$3" json="${4:-}"
  local args=(-sS -o "$body" -w '%{http_code}' -X "$method" "$base$path" --max-time 30)
  [[ -n "${token:-}" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$json" ]] && args+=(-H 'Content-Type: application/json' -d "$json")
  local status; status=$(curl "${args[@]}")
  if [[ "$status" != "$expected" ]]; then
    echo "::error::$method $path → HTTP $status (expected $expected): $(head -c 300 "$body")"; exit 1
  fi
  echo "ok $status $method $path"
}

password="Flow-$(openssl rand -hex 12)"; mask "$password"
email="flow-$RANDOM@example.test"
year=$(date +%Y); month=$(date +%Y-%m)
# Far enough that generated installments (which create the next year when needed) never reach it.
next=$((year + 5))

step 201 POST /api/auth/register "{\"name\":\"Flow\",\"email\":\"$email\",\"password\":\"$password\"}"
step 200 POST /api/auth/login "{\"email\":\"$email\",\"password\":\"$password\"}"
token=$(jq -r .token "$body"); mask "$token"

step 200 GET /api/me
step 204 POST /api/me/onboarding-seen
step 200 GET /api/settings
step 200 GET /api/years

# Catalog: categories (15 defaults), cards, people
step 200 GET "/api/years/$year/categories"
[[ $(jq length "$body") -ge 15 ]] || { echo "::error::default categories missing"; exit 1; }
category=$(jq -r '.[0].id' "$body")
step 201 POST "/api/years/$year/categories" '{"name":"Flow categoria","description":"teste"}'
own_category=$(jq -r .id "$body")
step 200 PUT "/api/categories/$own_category" '{"name":"Flow categoria 2","description":null,"active":false}'
step 200 GET "/api/years/$year/categories?active=true"
step 201 POST "/api/years/$year/cards" '{"name":"Flow cartão","dueDay":10}'
card=$(jq -r .id "$body")
step 201 POST "/api/years/$year/people" '{"name":"Flow pessoa"}'
person=$(jq -r .id "$body")

# Expenses: fixed by Pix, card with a third party, generated installments (money in cents in the API; DECIMAL(12,2) reais in the database)
step 201 POST "/api/months/$month/expenses" \
  "{\"name\":\"Aluguel\",\"amountCents\":9000000000001,\"type\":\"FIXED\",\"paymentMethod\":\"PIX\",\"categoryId\":$category,\"date\":\"$month-05\"}"
fixed=$(jq -r .id "$body")
[[ $(jq -r .amountCents "$body") == 9000000000001 ]] || { echo "::error::cents were not kept"; exit 1; }
step 201 POST "/api/months/$month/expenses" \
  "{\"name\":\"Mercado\",\"amountCents\":12345,\"type\":\"CARD\",\"paymentMethod\":\"CREDIT\",\"categoryId\":$category,\"cardId\":$card,\"personId\":$person,\"date\":\"$month-12\"}"
step 201 POST "/api/months/$month/expenses" \
  "{\"name\":\"Notebook\",\"amountCents\":10000,\"type\":\"CARD\",\"paymentMethod\":\"CREDIT\",\"categoryId\":$category,\"cardId\":$card,\"date\":\"$month-15\",\"generateInstallments\":true,\"installmentCount\":3}"
step 200 PATCH "/api/expenses/$fixed/paid" '{"paid":true}'
step 200 PUT "/api/expenses/$fixed" \
  "{\"name\":\"Aluguel novo\",\"amountCents\":150000,\"type\":\"FIXED\",\"paymentMethod\":\"PIX\",\"categoryId\":$category,\"paid\":false,\"date\":\"$month-06\"}"
step 204 DELETE "/api/expenses/$fixed"

# A category in use cannot be deleted; an unused one can
step 409 DELETE "/api/categories/$category"
step 204 DELETE "/api/categories/$own_category"

# New year copies the catalog
step 201 POST "/api/years/$next"
step 200 GET "/api/years/$next/cards"
[[ $(jq length "$body") -ge 1 ]] || { echo "::error::new year did not copy the cards"; exit 1; }

rm -f "$body"
echo "API flow passed against $base"
