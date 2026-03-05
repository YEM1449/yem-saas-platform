# API Quickstart v2

Goal: run one complete CRM + portal scenario locally with reproducible commands.

## 1. Prerequisites
- Backend running on `http://localhost:8080`
- Seed user exists (`acme`, `admin@acme.com`, `Admin123!`)
- `jq` installed

## 2. Initialize variables
```bash
BASE_URL="http://localhost:8080"
TENANT_KEY="acme"
EMAIL="admin@acme.com"
PASSWORD="Admin123!"
RUN_ID=$(date +%s)
CONTACT_EMAIL="buyer+$RUN_ID@example.com"
PROPERTY_REF="REF-$RUN_ID"
```

## 3. Health + login
```bash
curl -i "$BASE_URL/actuator/health"

TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"$TENANT_KEY\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | jq -r '.accessToken')
```

## 4. Read caller identity
```bash
ME=$(curl -s "$BASE_URL/auth/me" -H "Authorization: Bearer $TOKEN")
USER_ID=$(echo "$ME" | jq -r '.userId')
```

## 5. Create project and property
```bash
PROJECT_ID=$(curl -s -X POST "$BASE_URL/api/projects" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Project v2","description":"Quickstart project"}' | jq -r '.id')

PROPERTY_ID=$(curl -s -X POST "$BASE_URL/api/properties" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"TERRAIN_VIERGE\",
    \"title\": \"Demo Property\",
    \"referenceCode\": \"$PROPERTY_REF\",
    \"price\": 900000,
    \"currency\": \"MAD\",
    \"landAreaSqm\": 200,
    \"projectId\": \"$PROJECT_ID\"
  }" | jq -r '.id')

curl -s -X PUT "$BASE_URL/api/properties/$PROPERTY_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE"}' | jq '{id,status}'
```

## 6. Create contact and deposit
```bash
CONTACT_ID=$(curl -s -X POST "$BASE_URL/api/contacts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Client\",
    \"lastName\": \"Demo\",
    \"email\": \"$CONTACT_EMAIL\",
    \"phone\": \"0600000000\"
  }" | jq -r '.id')

DEPOSIT_ID=$(curl -s -X POST "$BASE_URL/api/deposits" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"contactId\": \"$CONTACT_ID\",
    \"propertyId\": \"$PROPERTY_ID\",
    \"amount\": 50000,
    \"currency\": \"MAD\"
  }" | jq -r '.id')

curl -s -X POST "$BASE_URL/api/deposits/$DEPOSIT_ID/confirm" \
  -H "Authorization: Bearer $TOKEN" | jq '{id,status,confirmedAt}'
```

## 7. Create and sign contract
```bash
CONTRACT_ID=$(curl -s -X POST "$BASE_URL/api/contracts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"propertyId\": \"$PROPERTY_ID\",
    \"buyerContactId\": \"$CONTACT_ID\",
    \"agentId\": \"$USER_ID\",
    \"agreedPrice\": 870000,
    \"listPrice\": 900000,
    \"sourceDepositId\": \"$DEPOSIT_ID\"
  }" | jq -r '.id')

curl -s -X POST "$BASE_URL/api/contracts/$CONTRACT_ID/sign" \
  -H "Authorization: Bearer $TOKEN" | jq '{id,status,signedAt}'
```

## 8. Download contract and reservation PDFs
```bash
curl -s -o reservation_$RUN_ID.pdf \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/deposits/$DEPOSIT_ID/documents/reservation.pdf"

curl -s -o contract_$RUN_ID.pdf \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/contracts/$CONTRACT_ID/documents/contract.pdf"
```

## 9. Verify dashboard data
```bash
curl -s "$BASE_URL/api/dashboard/commercial/summary" \
  -H "Authorization: Bearer $TOKEN" | jq '{salesCount,salesTotalAmount,depositsCount}'
```

## 10. Portal scenario
### Request magic link
```bash
MAGIC_LINK=$(curl -s -X POST "$BASE_URL/api/portal/auth/request-link" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$CONTACT_EMAIL\",\"tenantKey\":\"$TENANT_KEY\"}" | jq -r '.magicLinkUrl')
```

### Verify and call portal contracts
```bash
PORTAL_RAW_TOKEN=$(echo "$MAGIC_LINK" | sed -E 's/.*token=([^&]+).*/\1/')
PORTAL_TOKEN=$(curl -s "$BASE_URL/api/portal/auth/verify?token=$PORTAL_RAW_TOKEN" | jq -r '.accessToken')

curl -s "$BASE_URL/api/portal/contracts" \
  -H "Authorization: Bearer $PORTAL_TOKEN" | jq .
```

## 11. Typical failure diagnostics
| Symptom | Root cause | Fix |
|---------|------------|-----|
| `401` after login | token missing/expired/revoked | re-login and refresh token |
| deposit creation fails | property not `ACTIVE` | update property status first |
| contract create fails | missing `agentId` for admin/manager | pass `agentId` |
| portal verify fails | token already used or expired | request new link |
| `403` on admin route | role mismatch | use ADMIN credentials |
