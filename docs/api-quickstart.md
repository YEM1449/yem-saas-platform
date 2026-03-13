# API Quickstart

This guide provides a precise, copy/paste-friendly API flow for local development.

Base URL used below: `http://localhost:8080`

## 1. Prerequisites
- Backend is running.
- Seed tenant/user exists (`acme`, `admin@acme.com`, `Admin123!`).
- `jq` is installed for response parsing.

## 2. Prepare environment variables
```bash
BASE_URL="http://localhost:8080"
TENANT_KEY="acme"
EMAIL="admin@acme.com"
PASSWORD="Admin123!"
RUN_ID=$(date +%s)
CONTACT_EMAIL="sara.bennani+$RUN_ID@example.com"
PROPERTY_REF="LOT-DEMO-$RUN_ID"
```

## 3. Health check
```bash
curl -i "$BASE_URL/actuator/health"
```
Expected: HTTP `200`, body includes `{"status":"UP"}`.

## 4. CRM login and token capture
```bash
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"$TENANT_KEY\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | jq -r '.accessToken')

echo "$TOKEN" | cut -c1-40
```

If token is empty, inspect response body:
```bash
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"$TENANT_KEY\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq .
```

## 5. Verify caller identity
```bash
ME=$(curl -s "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN")

echo "$ME" | jq .
USER_ID=$(echo "$ME" | jq -r '.userId')
TENANT_ID=$(echo "$ME" | jq -r '.tenantId')
```

Expected fields: `userId`, `tenantId`, and usually `role`.

## 6. Create a project
```bash
PROJECT_ID=$(curl -s -X POST "$BASE_URL/api/projects" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Projet Demo API",
    "description": "Projet de démonstration quickstart"
  }' | jq -r '.id')

echo "$PROJECT_ID"
```

## 7. Create a property (valid minimal payload)
Property type-specific validation is strict. To keep payload minimal, use `TERRAIN_VIERGE` (requires `landAreaSqm`).

```bash
PROPERTY_ID=$(curl -s -X POST "$BASE_URL/api/properties" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"TERRAIN_VIERGE\",
    \"title\": \"Lot Démo\",
    \"referenceCode\": \"$PROPERTY_REF\",
    \"price\": 950000,
    \"currency\": \"MAD\",
    \"landAreaSqm\": 220,
    \"projectId\": \"$PROJECT_ID\"
  }" | jq -r '.id')

echo "$PROPERTY_ID"
```

Note: newly created properties start with status `DRAFT`.

## 8. Promote property to ACTIVE
Deposits require an `ACTIVE` property.

```bash
curl -s -X PUT "$BASE_URL/api/properties/$PROPERTY_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE"}' | jq '{id, status, referenceCode}'
```

## 9. Create a contact (prospect)
```bash
CONTACT_ID=$(curl -s -X POST "$BASE_URL/api/contacts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Sara\",
    \"lastName\": \"Bennani\",
    \"email\": \"$CONTACT_EMAIL\",
    \"phone\": \"0600112233\"
  }" | jq -r '.id')

echo "$CONTACT_ID"
```

## 10. Create a deposit (reservation)
```bash
DEPOSIT_ID=$(curl -s -X POST "$BASE_URL/api/deposits" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"contactId\": \"$CONTACT_ID\",
    \"propertyId\": \"$PROPERTY_ID\",
    \"amount\": 50000,
    \"currency\": \"MAD\"
  }" | jq -r '.id')

echo "$DEPOSIT_ID"
```

Check property became reserved:
```bash
curl -s "$BASE_URL/api/properties/$PROPERTY_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, status, reservedAt}'
```

## 11. Confirm deposit
```bash
curl -s -X POST "$BASE_URL/api/deposits/$DEPOSIT_ID/confirm" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, status, confirmedAt}'
```

## 12. Create contract (DRAFT)
For ADMIN/MANAGER callers, `agentId` is required.

```bash
CONTRACT_ID=$(curl -s -X POST "$BASE_URL/api/contracts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"propertyId\": \"$PROPERTY_ID\",
    \"buyerContactId\": \"$CONTACT_ID\",
    \"agentId\": \"$USER_ID\",
    \"agreedPrice\": 930000,
    \"listPrice\": 950000
  }" | jq -r '.id')

echo "$CONTRACT_ID"
```

## 13. Sign contract
```bash
curl -s -X POST "$BASE_URL/api/contracts/$CONTRACT_ID/sign" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, status, signedAt, agreedPrice}'
```

Check property moved to `SOLD`:
```bash
curl -s "$BASE_URL/api/properties/$PROPERTY_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, status, soldAt}'
```

## 14. Download generated PDFs
### Reservation PDF
```bash
curl -s -o reservation.pdf \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/deposits/$DEPOSIT_ID/documents/reservation.pdf"
file reservation.pdf
```

### Contract PDF
```bash
curl -s -o contract.pdf \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/contracts/$CONTRACT_ID/documents/contract.pdf"
file contract.pdf
```

## 15. Outbox messaging quick check
```bash
curl -s -X POST "$BASE_URL/api/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"channel\": \"EMAIL\",
    \"contactId\": \"$CONTACT_ID\",
    \"subject\": \"Bienvenue\",
    \"body\": \"Message de test quickstart\",
    \"correlationType\": \"CONTRACT\",
    \"correlationId\": \"$CONTRACT_ID\"
  }" | jq .
```
Expected: HTTP `202` and a `messageId`.

## 16. Commercial dashboard quick check
```bash
curl -s "$BASE_URL/api/dashboard/commercial/summary" \
  -H "Authorization: Bearer $TOKEN" | jq '{salesCount, salesTotalAmount, depositsCount, activeReservationsCount}'
```

## 17. Portal flow quick check
### Step A — request magic link
```bash
MAGIC_LINK=$(curl -s -X POST "$BASE_URL/api/portal/auth/request-link" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$CONTACT_EMAIL\",
    \"tenantKey\": \"acme\"
  }" | jq -r '.magicLinkUrl')

echo "$MAGIC_LINK"
```

### Step B — extract token and verify
```bash
PORTAL_RAW_TOKEN=$(echo "$MAGIC_LINK" | sed -E 's/.*token=([^&]+).*/\1/')

PORTAL_TOKEN=$(curl -s "$BASE_URL/api/portal/auth/verify?token=$PORTAL_RAW_TOKEN" \
  | jq -r '.accessToken')

echo "$PORTAL_TOKEN" | cut -c1-40
```

### Step C — list portal contracts
```bash
curl -s "$BASE_URL/api/portal/contracts" \
  -H "Authorization: Bearer $PORTAL_TOKEN" | jq .
```

## 18. Frequent Failure Cases and Fast Fixes
| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `401 UNAUTHORIZED` immediately | invalid/expired token | re-login and refresh token |
| `403 FORBIDDEN` on write endpoint | role mismatch | use ADMIN/MANAGER token |
| deposit creation fails with property error | property not `ACTIVE` | update property status first |
| contract creation fails for ADMIN/MANAGER | missing `agentId` | pass `agentId` in payload |
| portal verify fails | token reused/expired | request new magic link |

## 19. Useful next references
- Full API catalog: [api.md](api.md)
- Developer workflows: [05_DEV_GUIDE.md](05_DEV_GUIDE.md)
- Local dev walkthrough: [local-dev.md](local-dev.md)
