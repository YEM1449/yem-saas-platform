#!/usr/bin/env bash
# ── smoke-stack.sh ────────────────────────────────────────────────────────────
# End-to-end smoke test for the full Docker Compose stack.
#
# Usage:
#   ./scripts/smoke-stack.sh [--backend-url http://localhost:8080]
#                            [--frontend-url http://localhost]
#                            [--tenant-key acme]
#                            [--email admin@acme.com]
#                            [--password 'Admin123!']
#
# Prerequisites: curl, jq, docker compose
# Exit codes: 0 = all checks passed, 1 = one or more checks failed
#
set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost}"
TENANT_KEY="${TENANT_KEY:-acme}"
EMAIL="${EMAIL:-admin@acme.com}"
PASSWORD="${PASSWORD:-Admin123!}"
TIMEOUT=120   # seconds to wait for backend to become healthy
STEP=5

# ── Colour helpers ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}[PASS]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; FAILURES=$((FAILURES+1)); }
info() { echo -e "${YELLOW}[INFO]${NC} $*"; }

FAILURES=0

# ── Parse CLI args ─────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-url)  BACKEND_URL="$2";  shift 2 ;;
    --frontend-url) FRONTEND_URL="$2"; shift 2 ;;
    --tenant-key)   TENANT_KEY="$2";   shift 2 ;;
    --email)        EMAIL="$2";        shift 2 ;;
    --password)     PASSWORD="$2";     shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# ── 1. Wait for backend health ────────────────────────────────────────────────
info "Waiting for backend at ${BACKEND_URL}/actuator/health (timeout: ${TIMEOUT}s)…"
ELAPSED=0
until curl -sf "${BACKEND_URL}/actuator/health" | jq -e '.status == "UP"' > /dev/null 2>&1; do
  if [[ $ELAPSED -ge $TIMEOUT ]]; then
    fail "Backend did not become healthy within ${TIMEOUT}s"
    docker compose logs hlm-backend 2>/dev/null | tail -40 || true
    exit 1
  fi
  sleep $STEP
  ELAPSED=$((ELAPSED+STEP))
done
pass "Backend is UP (${ELAPSED}s)"

# ── 2. Backend actuator/health returns 200 ────────────────────────────────────
HTTP=$(curl -so /dev/null -w "%{http_code}" "${BACKEND_URL}/actuator/health")
if [[ "$HTTP" == "200" ]]; then
  pass "GET /actuator/health → 200"
else
  fail "GET /actuator/health → ${HTTP} (expected 200)"
fi

# ── 3. Login with seed credentials ────────────────────────────────────────────
info "Authenticating as ${EMAIL} in tenant ${TENANT_KEY}…"
AUTH_BODY=$(curl -sf -X POST "${BACKEND_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"${TENANT_KEY}\",\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" \
  2>/dev/null) || AUTH_BODY=""

if [[ -n "$AUTH_BODY" ]]; then
  TOKEN=$(echo "$AUTH_BODY" | jq -r '.accessToken // empty')
  if [[ -n "$TOKEN" ]]; then
    pass "POST /auth/login → token received"
  else
    fail "POST /auth/login → response OK but no accessToken in body"
  fi
else
  fail "POST /auth/login → request failed"
  TOKEN=""
fi

# ── 4. Authenticated API call — list contacts ─────────────────────────────────
if [[ -n "${TOKEN:-}" ]]; then
  HTTP=$(curl -so /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    "${BACKEND_URL}/api/contacts")
  if [[ "$HTTP" == "200" ]]; then
    pass "GET /api/contacts → 200"
  else
    fail "GET /api/contacts → ${HTTP} (expected 200)"
  fi
fi

# ── 5. GDPR privacy notice (authenticated) ────────────────────────────────────
if [[ -n "${TOKEN:-}" ]]; then
  HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    "${BACKEND_URL}/api/gdpr/privacy-notice")
  if [[ "$HTTP" == "200" ]]; then
    pass "GET /api/gdpr/privacy-notice → 200"
  else
    fail "GET /api/gdpr/privacy-notice → ${HTTP}"
  fi
fi

# ── 6. Frontend serves index.html ─────────────────────────────────────────────
HTTP=$(curl -so /dev/null -w "%{http_code}" "${FRONTEND_URL}/")
if [[ "$HTTP" == "200" ]]; then
  pass "GET ${FRONTEND_URL}/ → 200 (Frontend OK)"
else
  fail "GET ${FRONTEND_URL}/ → ${HTTP} (expected 200)"
fi

# ── 7. Redis health (via backend health detail if enabled) ───────────────────
HEALTH_JSON=$(curl -sf "${BACKEND_URL}/actuator/health" 2>/dev/null || echo "{}")
REDIS_STATUS=$(echo "$HEALTH_JSON" | jq -r '.components.redis.status // "n/a"')
if [[ "$REDIS_STATUS" == "UP" ]]; then
  pass "Redis component health → UP"
elif [[ "$REDIS_STATUS" == "n/a" ]]; then
  info "Redis health component not exposed (set management.endpoints.web.exposure.include=health to include details)"
else
  fail "Redis component health → ${REDIS_STATUS}"
fi

# ── 8. Postgres health ────────────────────────────────────────────────────────
DB_STATUS=$(echo "$HEALTH_JSON" | jq -r '.components.db.status // "n/a"')
if [[ "$DB_STATUS" == "UP" ]]; then
  pass "Database component health → UP"
elif [[ "$DB_STATUS" == "n/a" ]]; then
  info "DB health component not exposed in actuator"
else
  fail "Database component health → ${DB_STATUS}"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
if [[ $FAILURES -eq 0 ]]; then
  echo -e "${GREEN}✓ All smoke checks passed.${NC}"
  exit 0
else
  echo -e "${RED}✗ ${FAILURES} smoke check(s) failed.${NC}"
  exit 1
fi
