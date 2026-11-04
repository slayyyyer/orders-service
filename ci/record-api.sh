#!/usr/bin/env bash
# Record a scripted set of REST and GraphQL calls against the running service.
#
# The output is the only "live" thing on the Pages site: the page replays it. Nothing
# here claims to be a running server, and every recorded row carries the commit and time.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUT="${OUT:-dist/data/recordings.json}"
COMMIT="${GITHUB_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo local)}"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
KEY_SUFFIX="$(date -u +%Y%m%dT%H%M%SZ)"

TMP="$(mktemp)"
echo '[]' >"$TMP"

record() { # record <label> <method> <path> [body] [extra-header]
  local label="$1" method="$2" path="$3" body="${4:-}" extra="${5:-}"
  local response http_code start end duration

  local args=(-sS -X "$method" "$BASE_URL$path" -H 'Accept: application/json')
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data-binary "$body")
  [ -n "$extra" ] && args+=(-H "$extra")

  start=$(date +%s%3N)
  response="$(curl "${args[@]}" -w $'\n%{http_code}')"
  end=$(date +%s%3N)
  duration=$((end - start))

  http_code="$(printf '%s' "$response" | tail -n1)"
  response="$(printf '%s' "$response" | sed '$d')"

  jq --argjson existing "$(cat "$TMP")" \
     --arg label "$label" \
     --arg method "$method" \
     --arg path "$path" \
     --arg body "$body" \
     --argjson status "$http_code" \
     --argjson durationMs "$duration" \
     --arg response "$response" \
     '$existing + [{
        label: $label,
        method: $method,
        path: $path,
        requestBody: (if $body == "" then null else ($body | fromjson?) end),
        status: $status,
        durationMs: $durationMs,
        responseBody: ($response | fromjson?)
      }]' >"$TMP.new"
  mv "$TMP.new" "$TMP"
}

echo "waiting for $BASE_URL/actuator/health ..."
for _ in $(seq 1 60); do
  if curl -fsS "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1 \
    || curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

record "List customers" GET "/api/customers?limit=10"
record "Get one customer" GET "/api/customers/11111111-1111-4111-8111-111111111111"
record "Search products" GET "/api/products?query=keyboard"
record "Orders for a customer, newest first" GET "/api/orders?customerId=11111111-1111-4111-8111-111111111111&size=5"
record "Orders filtered by status" GET "/api/orders?customerId=11111111-1111-4111-8111-111111111111&status=PAID"
record "Create order (idempotent, first call)" POST "/api/orders" \
  '{"customerId":"11111111-1111-4111-8111-111111111111","items":[{"productId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","quantity":1},{"productId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","quantity":2}]}' \
  "Idempotency-Key: recorded-${KEY_SUFFIX}"
record "Create order (same key: replayed)" POST "/api/orders" \
  '{"customerId":"11111111-1111-4111-8111-111111111111","items":[{"productId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","quantity":1},{"productId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","quantity":2}]}' \
  "Idempotency-Key: recorded-${KEY_SUFFIX}"
record "Validation failure (empty items)" POST "/api/orders" \
  '{"customerId":"11111111-1111-4111-8111-111111111111","items":[]}' \
  "Idempotency-Key: recorded-invalid-${KEY_SUFFIX}"
record "Unknown customer (404 problem detail)" GET "/api/customers/99999999-9999-4999-8999-999999999999"
record "GraphQL: orders with line items" POST "/graphql" \
  '{"query":"query($id: ID!) { orders(customerId: $id, size: 3) { totalCount hasNextPage items { id status totalCents items { sku quantity lineTotalCents } } } }","variables":{"id":"11111111-1111-4111-8111-111111111111"}}'
record "GraphQL: products" POST "/graphql" \
  '{"query":"{ products(limit: 3) { sku name unitPriceCents } }"}'

mkdir -p "$(dirname "$OUT")"
jq -n \
  --arg commit "$COMMIT" \
  --arg recordedAt "$STAMP" \
  --arg baseUrl "$BASE_URL" \
  --argjson requests "$(cat "$TMP")" \
  '{commit: $commit, recordedAt: $recordedAt, recordedAgainst: $baseUrl, note: "Recorded from a CI run, not a live server.", requests: $requests}' \
  >"$OUT"

rm -f "$TMP"
echo "recorded $(jq '.requests | length' "$OUT") request/response pairs to $OUT"
