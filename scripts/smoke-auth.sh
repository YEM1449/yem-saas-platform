#!/usr/bin/env bash
# smoke-auth.sh — Smoke test: auth flow + one protected endpoint.
# Requires: curl, jq
#
# Usage:
#   EMAIL=admin@demo.ma PASSWORD='Admin123!Secure' ./scripts/smoke-auth.sh
#
# Environment variables:
#   BASE_URL    — backend URL         (default: http://localhost:8080)
#   EMAIL       — user email          (required)
#   PASSWORD    — user password       (required)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:?ERROR: EMAIL is required}"
PASSWORD="${PASSWORD:?ERROR: PASSWORD is required}"

# --- helpers ---
fail() { printf "\033[31mFAIL:\033[0m %s\n" "$1" >&2; exit 1; }
pass() { printf "\033[32mOK:\033[0m %s\n" "$1"; }
info() { printf "\033[36mINFO:\033[0m %s\n" "$1"; }

# --- pre-checks ---
if ! command -v jq &>/dev/null; then
  fail "jq is required but not installed. Install: sudo apt install jq  /  brew install jq"
fi

info "Base URL: $BASE_URL"

# --- Step 0: Health check ---
info "GET /actuator/health"
HEALTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null) || true
if [ "$HEALTH_HTTP" != "200" ]; then
  fail "Health check failed (HTTP $HEALTH_HTTP). Backend not running? Check logs for 'Tomcat started on port(s): 8080'."
fi
pass "health — backend is UP"

# --- Step 1: Login ---
info "POST /auth/login (email=$EMAIL)"
LOGIN_BODY=$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" \
  '{email:$e,password:$p}')
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "$LOGIN_BODY" \
) || fail "Login request failed (HTTP error or connection refused)"

REQUIRES_SELECTION=$(echo "$LOGIN_RESPONSE" | jq -r '.requiresSocieteSelection // false')
if [ "$REQUIRES_SELECTION" = "true" ]; then
  fail "Login returned requiresSocieteSelection=true. Use a single-membership account for this smoke test or extend the script to call /auth/switch-societe."
fi

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken // empty')
[ -n "$TOKEN" ] || fail "No accessToken in login response: $LOGIN_RESPONSE"

# Validate token looks like a JWT (header.payload.signature)
if [[ "$TOKEN" != *.*.* ]]; then
  fail "Login returned a non-JWT token (no dots). Backend may still be on an auth token stub. Fix auth issuance first."
fi
pass "login — token received (${#TOKEN} chars)"

# --- Step 2: /auth/me ---
info "GET /auth/me"
ME_RESPONSE=$(curl -sf "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN" \
) || fail "/auth/me request failed (401? token invalid?)"

USER_ID=$(echo "$ME_RESPONSE" | jq -r '.userId // empty')
SOCIETE_ID=$(echo "$ME_RESPONSE" | jq -r '.societeId // empty')
[ -n "$USER_ID" ] || fail "No userId in /auth/me response: $ME_RESPONSE"
[ -n "$SOCIETE_ID" ] || fail "No societeId in /auth/me response: $ME_RESPONSE"
pass "/auth/me — userId=$USER_ID societeId=$SOCIETE_ID"

# --- Step 3: Protected endpoint ---
info "GET /api/properties"
PROP_RESPONSE=$(curl -sf "$BASE_URL/api/properties" \
  -H "Authorization: Bearer $TOKEN" \
) || fail "GET /api/properties failed (403? role missing?)"

PROP_COUNT=$(echo "$PROP_RESPONSE" | jq 'length')
pass "protected endpoint — GET /api/properties returned $PROP_COUNT item(s)"

# --- Summary ---
echo ""
info "All smoke tests passed."
