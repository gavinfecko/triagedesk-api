#!/usr/bin/env bash
# Demo smoke test (TD-114): walks the sprint demo paths against a running API and exits non-zero on any mismatch.
#   scripts/demo-smoke.sh                 against $BASE_URL (default http://localhost:8080) with the demo data seeded
#   scripts/demo-smoke.sh --compose       starts a fresh stack from compose.yaml + compose.smoke.yaml around the
#                                          image $SMOKE_IMAGE (default triagedesk-api:smoke), tests it, removes it
# Needs curl and jq.
set -uo pipefail

PASSWORD="Demo-Password-2026"
FAILED=0
PROJECT="triagedesk-smoke"

if [[ "${1:-}" == "--compose" ]]; then
  BASE_URL="http://localhost:18080"
  MAILPIT_URL="http://localhost:18025"
  compose() { docker compose -p "$PROJECT" -f compose.yaml -f compose.smoke.yaml "$@"; }
  cleanup() {
    if [[ $FAILED -ne 0 ]]; then compose logs api | tail -80; fi
    compose down -v --remove-orphans >/dev/null 2>&1
  }
  trap cleanup EXIT
  compose up -d --wait postgres mailpit || exit 1
  compose up -d api || exit 1
else
  BASE_URL="${BASE_URL:-http://localhost:8080}"
  MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
fi

pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILED=1; }
check() { # check "description" <command...>: passes when the command succeeds
  local what="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$what"; else fail "$what"; fi
}

# call METHOD PATH TOKEN [JSON] → sets STATUS and BODY
call() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}" out
  out=$(mktemp)
  local args=(-s -o "$out" -w '%{http_code}' -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(--data "$data")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat "$out"); rm -f "$out"
}

login() {
  call POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}"
  [[ "$STATUS" == 200 ]] || { fail "login as $1 (HTTP $STATUS)"; echo ""; return; }
  jq -r .access_token <<<"$BODY"
}

echo "Demo smoke test against $BASE_URL"
for _ in $(seq 1 90); do
  curl -sf "$BASE_URL/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null 2>&1 && break
  sleep 2
done
check "the API reports UP" bash -c "curl -sf '$BASE_URL/actuator/health' | jq -e '.status == \"UP\"'"
[[ $FAILED -eq 0 ]] || exit 1

ROSA=$(login rosa.diaz@clinic.test)
ANA=$(login ana.ruiz@clinic.test)
ADMIN=$(login admin@clinic.test)
[[ -n "$ROSA" && -n "$ANA" && -n "$ADMIN" ]] && pass "every demo role can log in"

echo "Sprint 1: scoping, roles, creating a ticket"
call GET /api/v1/users/me "$ROSA"; ROSA_ID=$(jq -r .id <<<"$BODY")
call GET "/api/v1/tickets?size=100" "$ROSA"
check "Rosa's list is not empty" jq -e '.items | length > 0' <<<"$BODY"
check "Rosa's list holds only her own tickets" jq -e --arg me "$ROSA_ID" 'all(.items[]; .requester.id == $me)' <<<"$BODY"

call GET "/api/v1/tickets?size=100" "$ADMIN"
OTHER=$(jq -r --arg me "$ROSA_ID" '[.items[] | select(.requester.id != $me)][0].key' <<<"$BODY")
call GET "/api/v1/tickets/$OTHER" "$ROSA"
check "another requester's ticket is 404 for Rosa ($OTHER)" test "$STATUS" = 404

call GET /api/v1/users "$ANA"
check "an agent gets 403 on user admin" test "$STATUS" = 403

call POST /api/v1/tickets "$ROSA" \
  '{"title":"Smoke test: label printer offline","description":"Created by the demo smoke test.","category_id":"00000000-0000-4000-8000-000000000202","priority":"P3_MEDIUM","requester_id":"00000000-0000-4000-8000-000000000999"}'
check "Rosa creates a ticket (201)" test "$STATUS" = 201
KEY=$(jq -r .key <<<"$BODY")
check "a requester_id she may not set is ignored with a warning" jq -e '.warnings | length > 0' <<<"$BODY"

echo "Sprint 2: working the ticket"
call POST "/api/v1/tickets/$KEY/assign" "$ANA" '{"assignee_id":"me"}'
check "Ana takes the ticket and it opens" jq -e '.status == "OPEN"' <<<"$BODY"
call GET "/api/v1/tickets/$KEY/audit" "$ROSA"
check "Rosa sees the ticket's history" jq -e 'length >= 2' <<<"$BODY"

echo "Sprint 3: SLA clocks, search, email"
call GET "/api/v1/tickets/$KEY/sla" "$ROSA"
check "the new ticket has an on-track resolution clock" jq -e '.resolution.status == "on_track"' <<<"$BODY"
call GET "/api/v1/tickets?sla_status=breached&size=1" "$ADMIN"
check "the demo data has breached tickets" jq -e '.page.total_elements > 0' <<<"$BODY"
call GET "/api/v1/tickets?q=printer&size=5" "$ANA"
check "full-text search finds printer tickets" jq -e '.items | length > 0' <<<"$BODY"
MAIL=0
for _ in $(seq 1 15); do
  if curl -s "$MAILPIT_URL/api/v1/search?query=subject:%22$KEY%22" | jq -e '.messages_count > 0' >/dev/null 2>&1; then
    MAIL=1; break
  fi
  sleep 1
done
check "Rosa was emailed about $KEY" test "$MAIL" = 1

if [[ $FAILED -ne 0 ]]; then echo "Demo smoke test FAILED"; exit 1; fi
echo "Demo smoke test passed"
