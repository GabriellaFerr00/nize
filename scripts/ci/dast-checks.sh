#!/usr/bin/env bash
# DAST checks against an API started inside the job (profile h2, synthetic users only; never production).
# Usage: dast-checks.sh <base-url>     Requires curl and jq.
set -uo pipefail
base="${1:?base url}"
failed=0
fail() { echo "::error::$*"; failed=1; }
mask() { [[ -n "${GITHUB_ACTIONS:-}" ]] && echo "::add-mask::$1"; return 0; }
LEAK='(exception|sqlstate|syntax error|hibernate|jdbc|select .* from|at br\.com|stack ?trace|org\.springframework|app_user)'
body=$(mktemp)
secs=0

# call METHOD URL [TOKEN] [JSON]: sets $status, $secs; the response body is in $body
call() {
  local args=(-sS -o "$body" -w '%{http_code} %{time_total}' -X "$1" "$2" --max-time 20)
  [[ -n "${3:-}" ]] && args+=(-H "Authorization: Bearer $3")
  [[ -n "${4:-}" ]] && args+=(-H 'Content-Type: application/json' -d "$4")
  local out
  out=$(curl "${args[@]}" || echo "000 99")
  status=${out%% *}
  secs=${out#* }
}

# 0. Two synthetic users
password="Dast-$(openssl rand -hex 12)"; mask "$password"
token_for() {
  local email="$1"
  call POST "$base/api/auth/register" "" "{\"name\":\"Dast\",\"email\":\"$email\",\"password\":\"$password\"}"
  call POST "$base/api/auth/login" "" "{\"email\":\"$email\",\"password\":\"$password\"}"
  jq -r .token "$body"
}
token_a=$(token_for "dast-a-$RANDOM@example.test"); mask "$token_a"
token_b=$(token_for "dast-b-$RANDOM@example.test"); mask "$token_b"
[[ "$token_a" == ey* && "$token_b" == ey* ]] || { echo "::error::could not create the synthetic users"; exit 1; }
year=$(date +%Y); month=$(date +%Y-%m)

# 1. Security headers on API responses
headers=$(curl -sS -D - -o /dev/null -H "Authorization: Bearer $token_a" "$base/api/me")
grep -qi '^x-content-type-options: *nosniff' <<<"$headers" || fail "API without X-Content-Type-Options: nosniff"
grep -qi '^x-frame-options: *deny' <<<"$headers" || fail "API without X-Frame-Options: DENY"
grep -qi '^cache-control:.*no-store' <<<"$headers" || fail "API responses with personal data are cacheable"
if [[ "$base" == https://* ]]; then
  grep -qi '^strict-transport-security:' <<<"$headers" || fail "API over https without HSTS"
fi

# 2. CORS: no foreign origin is ever allowed (the front reaches the API through its own origin, D-15)
for origin in https://evil.example null; do
  for method in OPTIONS POST; do
    h=$(curl -sS -D - -o /dev/null -X "$method" -H "Origin: $origin" -H 'Access-Control-Request-Method: POST' \
      -H 'Content-Type: application/json' -d '{}' "$base/api/auth/login")
    if grep -qi '^access-control-allow-origin:' <<<"$h"; then fail "CORS allows origin $origin ($method)"; fi
  done
done

# 3. Data endpoints need a token
for path in /api/me /api/settings "/api/years/$year/categories" "/api/years/$year/cards" "/api/years/$year/people"; do
  call GET "$base$path"
  [[ "$status" == 401 ]] || fail "$path answers HTTP $status without a token"
done

# 4. SQL injection probes on list/filter parameters: 400/404 with the error contract, no leak, no delay
payloads=("' OR '1'='1" "1 OR 1=1" "1; DROP TABLE app_user" "1 UNION SELECT password_hash FROM app_user" '"' '\' "' AND SLEEP(5)-- -")
for p in "${payloads[@]}"; do
  enc=$(jq -rn --arg v "$p" '$v|@uri')
  for target in "GET $base/api/years/$year/categories?active=$enc" "GET $base/api/years/$year/cards?active=$enc" \
                "GET $base/api/years/$year/people?active=$enc" "GET $base/api/years/$enc/categories" \
                "POST $base/api/months/$enc/expenses" "PUT $base/api/categories/$enc" "DELETE $base/api/expenses/$enc"; do
    call "${target%% *}" "${target#* }" "$token_a" '{}'
    if [[ "$status" =~ ^(400|401)$ ]] && ! jq -e . "$body" >/dev/null 2>&1; then
      # Rejected before reaching the app: Spring Security's firewall (';' in the path, empty body)
      # or Tomcat (encoded '\', generic HTML page). Still must not leak internals or the version.
      if grep -qiE "$LEAK|tomcat/[0-9]" "$body"; then fail "server rejection leaked details: $target"; fi
      continue
    fi
    [[ "$status" =~ ^(400|404)$ ]] || fail "SQLi probe got HTTP $status: $target"
    jq -e 'has("code") and has("fields")' "$body" >/dev/null 2>&1 || fail "SQLi probe broke the error contract: $target"
    if grep -qiE "$LEAK" "$body"; then fail "SQLi probe leaked internals: $target"; fi
    if awk -v s="$secs" 'BEGIN{exit !(s > 4)}'; then fail "SQLi probe took ${secs}s (time-based injection?): $target"; fi
  done
done

# 5. Errors follow {code, fields} and leak nothing
for target in "POST $base/api/auth/login|{\"email\":" "GET $base/api/does-not-exist|" "DELETE $base/api/me|"; do
  spec=${target%%|*}; json=${target#*|}
  call "${spec%% *}" "${spec#* }" "$token_a" "$json"
  jq -e '(.code | type == "string") and (.fields | type == "object")' "$body" >/dev/null 2>&1 \
    || fail "error without {code, fields} (HTTP $status): $spec"
  if grep -qiE "$LEAK" "$body"; then fail "error leaked internals: $spec"; fi
done

# 6. Money is whole cents: 10.5 is refused, never truncated (NFR-001)
call GET "$base/api/years/$year/categories" "$token_a"
category=$(jq -r '.[0].id' "$body")
call POST "$base/api/months/$month/expenses" "$token_a" \
  "{\"name\":\"Dast\",\"amountCents\":10.5,\"type\":\"FIXED\",\"paymentMethod\":\"PIX\",\"categoryId\":$category,\"date\":\"$month-05\"}"
if [[ "$status" != 400 ]] || ! jq -e '.code == "VALIDATION_ERROR"' "$body" >/dev/null; then
  fail "amountCents 10.5 was not refused (HTTP $status)"
fi

# 7. IDOR: user B never touches user A's rows (404, not 403)
call POST "$base/api/years/$year/categories" "$token_a" '{"name":"Dast privada"}'
own=$(jq -r '.id' "$body")
call PUT "$base/api/categories/$own" "$token_b" '{"name":"hijack","active":true}'
[[ "$status" == 404 ]] || fail "user B updating user A's category got HTTP $status"
call DELETE "$base/api/categories/$own" "$token_b"
[[ "$status" == 404 ]] || fail "user B deleting user A's category got HTTP $status"
call GET "$base/api/years/$year/categories" "$token_b"
jq -e --argjson id "$own" 'map(.id) | index($id) == null' "$body" >/dev/null || fail "user B can list user A's category"

# 8. Login does not reveal which e-mails exist
call POST "$base/api/auth/login" "" '{"email":"nobody-dast@example.test","password":"Wrong-pass-123"}'
unknown="$status $(cat "$body")"
call POST "$base/api/auth/login" "" '{"email":"dast-x@example.test","password":"Wrong-pass-123"}'
other="$status $(cat "$body")"
[[ "$unknown" == "$other" ]] || fail "login answers differ for unknown e-mails"

rm -f "$body"
if (( failed == 0 )); then echo "DAST checks passed against $base"; fi
exit "$failed"
