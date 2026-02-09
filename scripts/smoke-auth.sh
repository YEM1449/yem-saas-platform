#!/usr/bin/env bash
# smoke-auth.sh — Quick smoke test for auth + protected endpoints.
# Requires: curl, jq
#
# Usage:
#   TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
#
# Environment variables (with defaults):
#   BASE_URL    — backend URL         (default: http://localhost:8080)
#   TENANT_KEY  — tenant identifier   (required)
#   EMAIL       — user email          (required)
#   PASSWORD    — user password       (required)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_KEY="${TENANT_KEY:?ERROR: TENANT_KEY is required}"
EMAIL="${EMAIL:?ERROR: EMAIL is required}"
PASSWORD="${PASSWORD:?ERROR: PASSWORD is required}"

# --- helpers ---
fail() { printf "\033[31mFAIL:\033[0m %s\n" "$1" >&2; exit 1; }
pass() { printf "\033[32mPASS:\033[0m %s\n" "$1"; }
info() { printf "\033[36mINFO:\033[0m %s\n" "$1"; }

check_jq() {
  if ! command -v jq &>/dev/null; then
    fail "jq is required but not installed. Install it: sudo apt install jq / brew install jq"
  fi
}

# --- pre-checks ---
check_jq
info "Base URL: $BASE_URL"

# --- Step 1: Login ---
info "POST /auth/login (tenant=$TENANT_KEY, email=$EMAIL)"
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"$TENANT_KEY\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
) || fail "Login request failed (HTTP error or connection refused)"

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken // empty')
[ -n "$TOKEN" ] || fail "No accessToken in login response: $LOGIN_RESPONSE"
pass "Login succeeded — token received (${#TOKEN} chars)"

# --- Step 2: /auth/me ---
info "GET /auth/me"
ME_RESPONSE=$(curl -sf "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN" \
) || fail "/auth/me request failed"

USER_ID=$(echo "$ME_RESPONSE" | jq -r '.userId // empty')
TENANT_ID=$(echo "$ME_RESPONSE" | jq -r '.tenantId // empty')
[ -n "$USER_ID" ] || fail "No userId in /auth/me response: $ME_RESPONSE"
[ -n "$TENANT_ID" ] || fail "No tenantId in /auth/me response: $ME_RESPONSE"
pass "/auth/me — userId=$USER_ID tenantId=$TENANT_ID"

# --- Step 3: Protected endpoint ---
info "GET /api/properties"
PROP_RESPONSE=$(curl -sf "$BASE_URL/api/properties" \
  -H "Authorization: Bearer $TOKEN" \
) || fail "GET /api/properties failed"

# Response is a JSON array; just verify it parses
PROP_COUNT=$(echo "$PROP_RESPONSE" | jq 'length')
pass "GET /api/properties — returned $PROP_COUNT item(s)"

# --- Summary ---
echo ""
info "All smoke tests passed."
