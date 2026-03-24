#!/usr/bin/env bash
# smoke-rbac.sh — RBAC permission verification for YEM SaaS Platform
# Usage: ./scripts/smoke-rbac.sh
# Requires: backend running at http://localhost:8080, python3 in PATH
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0; FAIL=0

check() {
  local label=$1 expected=$2 actual=$3
  if [ "$actual" = "$expected" ]; then
    echo "  ✓ $label"
    ((PASS++))
  else
    echo "  ✗ $label — expected HTTP $expected, got HTTP $actual"
    ((FAIL++))
  fi
}

get_token() {
  local email="$1" password="$2"
  curl -sf -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('accessToken',''))" 2>/dev/null || echo ""
}

http() {
  local method="${1:-GET}" path="$2" token="${3:-}" body="${4:-}"
  local args=(-s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body"  ] && args+=(-H "Content-Type: application/json" -d "$body")
  curl "${args[@]}"
}

# ── Credentials ─────────────────────────────────────────────────────────────
SA_EMAIL="${SA_EMAIL:-superadmin@yourcompany.com}"
SA_PASS="${SA_PASS:-YourSecure2026!}"
AD_EMAIL="${AD_EMAIL:-admin@demo.ma}"
AD_PASS="${AD_PASS:-Admin123!Secure}"
MG_EMAIL="${MG_EMAIL:-manager@demo.ma}"
MG_PASS="${MG_PASS:-Manager123!Sec}"
AG_EMAIL="${AG_EMAIL:-agent@demo.ma}"
AG_PASS="${AG_PASS:-Agent123!Secure}"

echo ""
echo "══════════════════════════════════════════════════════"
echo "  YEM SaaS — RBAC Smoke Test"
echo "  Target: $BASE"
echo "══════════════════════════════════════════════════════"

# ── Get tokens ────────────────────────────────────────────────────────────────
echo ""
echo "[ Authenticating ]"
SA_TOKEN=$(get_token "$SA_EMAIL"  "$SA_PASS")
AD_TOKEN=$(get_token "$AD_EMAIL"  "$AD_PASS")
MG_TOKEN=$(get_token "$MG_EMAIL"  "$MG_PASS")
AG_TOKEN=$(get_token "$AG_EMAIL"  "$AG_PASS")

[ -n "$SA_TOKEN" ] && echo "  ✓ SUPER_ADMIN token obtained" || echo "  ✗ SUPER_ADMIN login failed ($SA_EMAIL)"
[ -n "$AD_TOKEN" ] && echo "  ✓ ADMIN token obtained"       || echo "  ✗ ADMIN login failed ($AD_EMAIL)"
[ -n "$MG_TOKEN" ] && echo "  ✓ MANAGER token obtained"     || echo "  ✗ MANAGER login failed ($MG_EMAIL)"
[ -n "$AG_TOKEN" ] && echo "  ✓ AGENT token obtained"       || echo "  ✗ AGENT login failed ($AG_EMAIL)"

if [ -z "$SA_TOKEN" ] || [ -z "$AD_TOKEN" ]; then
  echo ""
  echo "  ✗ Cannot continue without SUPER_ADMIN and ADMIN tokens."
  echo "    Check credentials or start the backend first."
  exit 1
fi

# ── A. Company management (SUPER_ADMIN only) ─────────────────────────────────
echo ""
echo "[ A. Company Management — SUPER_ADMIN only ]"

check "SUPER_ADMIN can list companies"      200 "$(http GET /api/admin/societes "$SA_TOKEN")"
check "ADMIN cannot list companies"         403 "$(http GET /api/admin/societes "$AD_TOKEN")"
check "MANAGER cannot list companies"       403 "$(http GET /api/admin/societes "${MG_TOKEN:-}")"
check "AGENT cannot list companies"         403 "$(http GET /api/admin/societes "${AG_TOKEN:-}")"
check "No token → 401"                      401 "$(http GET /api/admin/societes)"

SOC_PAYLOAD='{"nom":"RBAC Smoke Test Co","pays":"MA"}'
check "SUPER_ADMIN can create company"      201 "$(http POST /api/admin/societes "$SA_TOKEN" "$SOC_PAYLOAD")"
check "ADMIN cannot create company"         403 "$(http POST /api/admin/societes "$AD_TOKEN" "$SOC_PAYLOAD")"
check "MANAGER cannot create company"       403 "$(http POST /api/admin/societes "${MG_TOKEN:-}" "$SOC_PAYLOAD")"
check "AGENT cannot create company"         403 "$(http POST /api/admin/societes "${AG_TOKEN:-}" "$SOC_PAYLOAD")"

# ── B. User invitation — privilege escalation ─────────────────────────────────
echo ""
echo "[ B. User Invitation — Privilege Escalation Prevention ]"

INVITE_ADMIN='{"email":"smoke-admin@test.ma","prenom":"Smoke","nomFamille":"Admin","role":"ADMIN"}'
INVITE_MGR='{"email":"smoke-mgr@test.ma","prenom":"Smoke","nomFamille":"Mgr","role":"MANAGER"}'
INVITE_AGT='{"email":"smoke-agt@test.ma","prenom":"Smoke","nomFamille":"Agt","role":"AGENT"}'

check "ADMIN can invite MANAGER"                    201 "$(http POST /api/mon-espace/utilisateurs "$AD_TOKEN" "$INVITE_MGR")"
check "ADMIN can invite AGENT"                      201 "$(http POST /api/mon-espace/utilisateurs "$AD_TOKEN" "$INVITE_AGT")"
check "ADMIN CANNOT invite ADMIN (escalation!)"     403 "$(http POST /api/mon-espace/utilisateurs "$AD_TOKEN" "$INVITE_ADMIN")"
check "MANAGER cannot invite anyone"                403 "$(http POST /api/mon-espace/utilisateurs "${MG_TOKEN:-}" "$INVITE_AGT")"
check "AGENT cannot invite anyone"                  403 "$(http POST /api/mon-espace/utilisateurs "${AG_TOKEN:-}" "$INVITE_AGT")"

# ── C. Member visibility ─────────────────────────────────────────────────────
echo ""
echo "[ C. Member Visibility ]"

check "ADMIN can list team members"    200 "$(http GET /api/mon-espace/utilisateurs "$AD_TOKEN")"
check "MANAGER can list team members"  200 "$(http GET /api/mon-espace/utilisateurs "${MG_TOKEN:-}")"
check "AGENT cannot list members"      403 "$(http GET /api/mon-espace/utilisateurs "${AG_TOKEN:-}")"

# ── D. Role changes ───────────────────────────────────────────────────────────
echo ""
echo "[ D. Role Change (using first member found) ]"

MEMBER_ID=$(curl -sf "$BASE/api/mon-espace/utilisateurs?size=1" \
  -H "Authorization: Bearer $AD_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['content'][0]['id'] if d.get('content') else '')" 2>/dev/null || echo "")

if [ -n "$MEMBER_ID" ]; then
  VER=$(curl -sf "$BASE/api/mon-espace/utilisateurs/$MEMBER_ID" \
    -H "Authorization: Bearer $AD_TOKEN" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('version',0))" 2>/dev/null || echo "0")

  PROMOTE_ADMIN="{\"nouveauRole\":\"ADMIN\",\"version\":$VER}"
  CHANGE_MGR="{\"nouveauRole\":\"MANAGER\",\"version\":$VER}"

  check "ADMIN cannot promote member to ADMIN" 403 "$(http PATCH "/api/mon-espace/utilisateurs/$MEMBER_ID/role" "$AD_TOKEN" "$PROMOTE_ADMIN")"
  check "MANAGER cannot change roles"           403 "$(http PATCH "/api/mon-espace/utilisateurs/$MEMBER_ID/role" "${MG_TOKEN:-}" "$CHANGE_MGR")"
else
  echo "  ⚠ Skipped role-change tests — no members found"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════"
TOTAL=$((PASS + FAIL))
echo "  Results: $PASS / $TOTAL checks passed"
if [ "$FAIL" -gt 0 ]; then
  echo "  STATUS: ✗ FAILED — fix all failures before merging"
  exit 1
else
  echo "  STATUS: ✓ ALL RBAC CHECKS PASSED"
fi
