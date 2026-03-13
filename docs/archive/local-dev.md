# Local Development Guide

Golden-path walkthrough: prerequisites → backend → frontend → first login.

## Prerequisites

| Tool       | Version | Check command        |
|------------|---------|----------------------|
| Java       | 21      | `java -version`      |
| Docker     | any     | `docker info`        |
| PostgreSQL | 14+     | `psql --version`     |
| Node       | 18+     | `node -v`            |
| npm        | 9+      | `npm -v`             |

Docker is needed for Testcontainers (integration tests). PostgreSQL can be local or a Docker container.

## 1. Database setup

**Option A — Docker (recommended):**
```bash
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm \
  -e POSTGRES_USER=hlm_user \
  -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 \
  postgres:16-alpine
```

**Option B — Existing PostgreSQL:**
```bash
createdb hlm
createuser hlm_user
psql -c "ALTER USER hlm_user WITH PASSWORD 'hlm_pwd';"
psql -c "GRANT ALL PRIVILEGES ON DATABASE hlm TO hlm_user;"
```

## 2. Environment variables

```bash
cp .env.example .env
# Edit .env if your credentials differ from the defaults
export $(grep -v '^#' .env | xargs)
```

Required variables (see `.env.example`):

| Variable          | Default in `.env.example`               | Notes                           |
|-------------------|------------------------------------------|---------------------------------|
| `DB_URL`          | `jdbc:postgresql://localhost:5432/hlm`   | JDBC connection string          |
| `DB_USER`         | `hlm_user`                               | Database username               |
| `DB_PASSWORD`     | `hlm_pwd`                                | Database password               |
| `JWT_SECRET`      | *(40-char example)*                      | Must be >= 32 characters (HS256)|
| `JWT_TTL_SECONDS` | `3600`                                   | Token lifetime in seconds       |

The app refuses to start if `JWT_SECRET` is missing, blank, or shorter than 32 characters.

## 3. Start the backend

```bash
cd hlm-backend
chmod +x mvnw
./mvnw spring-boot:run
```

Verify:
```bash
curl -i http://localhost:8080/actuator/health
# Expected: 200 {"status":"UP"}
```

If it fails, check the console for `Tomcat started on port(s): 8080` and review error logs.

Liquibase runs automatically on startup and seeds a default tenant (`acme`) with an admin user.

## 4. Start the frontend

```bash
cd hlm-frontend
npm ci
npm start
```

Opens at **http://localhost:4200**. The dev proxy (`proxy.conf.json`) forwards `/auth`, `/api`, `/dashboard`, and `/actuator` to `http://localhost:8080` — no CORS issues in dev.

**Important:** Use relative paths in frontend code (e.g. `/auth/login`, not `http://localhost:8080/auth/login`).

## 5. First login (smoke test)

### Via curl

```bash
# Login with the seeded admin user
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}'
```

Expected: `200` with `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600 }`.

```bash
# Verify identity
TOKEN="<paste accessToken>"
curl -s http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"
```

### Via the Angular app

1. Open http://localhost:4200 — redirects to `/login`.
2. Enter: tenant `acme`, email `admin@acme.com`, password `Admin123!`.
3. On success, you land on `/app/properties`.

## 6. Demo: Contacts flow

### Via curl

```bash
TOKEN="<paste accessToken from login>"

# Create a contact
curl -s -X POST http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","phone":"0612345678"}'

# List contacts
curl -s http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN"

# Get contact by ID
curl -s http://localhost:8080/api/contacts/<id> \
  -H "Authorization: Bearer $TOKEN"
```

### Via the Angular app

1. Login at http://localhost:4200
2. Click **Contacts** in the nav bar → `/app/contacts`
3. Click a contact row → `/app/contacts/:id` (details page)

## 7. Demo: Prospects flow

### Via curl

```bash
TOKEN="<paste accessToken from login>"

# List prospects only (contacts filtered by type)
curl -s "http://localhost:8080/api/contacts?contactType=PROSPECT" \
  -H "Authorization: Bearer $TOKEN"

# Update a prospect's status
curl -s -X PATCH http://localhost:8080/api/contacts/<id>/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"QUALIFIED_PROSPECT"}'
```

### Via the Angular app

1. Login at http://localhost:4200
2. Click **Prospects** in the nav bar → `/app/prospects`
3. Click a prospect row → `/app/prospects/:id` (details page)
4. Change the **Status** dropdown → status updates immediately

## 8. Demo: Deposit / Reservation flow

Creating a deposit automatically marks the property as **RESERVED**. Only **ACTIVE** properties can receive a deposit. Cancelling or expiring a deposit releases the property back to **ACTIVE**.

### Via curl

```bash
TOKEN="<paste accessToken from login>"

# Create a deposit — property moves from ACTIVE → RESERVED
CONTACT_ID="<prospect UUID>"
PROPERTY_ID="<property UUID (must be ACTIVE)>"
curl -s -X POST http://localhost:8080/api/deposits \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"contactId\":\"$CONTACT_ID\",\"propertyId\":\"$PROPERTY_ID\",\"amount\":5000}"

# Verify property is now RESERVED
curl -s http://localhost:8080/api/properties/$PROPERTY_ID \
  -H "Authorization: Bearer $TOKEN" | jq .status
# Expected: "RESERVED"

# Attempt a second deposit on the same property → 409
curl -s -w "\n%{http_code}" -X POST http://localhost:8080/api/deposits \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"contactId\":\"<another-contact-id>\",\"propertyId\":\"$PROPERTY_ID\",\"amount\":3000}"
# Expected: 409 — property is already reserved

# List deposits for a contact
curl -s "http://localhost:8080/api/deposits/report?contactId=$CONTACT_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### Via the Angular app

1. Login at http://localhost:4200
2. Click **Properties** → verify status badges (DRAFT, ACTIVE, RESERVED, etc.)
3. Click **Prospects** → click a prospect → scroll to **Réservation / Acompte**
4. The property dropdown shows only **ACTIVE** properties
5. Select a property, enter an amount, click **Create Deposit**
6. Deposit appears in the table; prospect status updates; property moves to RESERVED
7. Go back to **Properties** — the property now shows a RESERVED badge
8. Try creating another deposit on the same property → error: "Ce bien est déjà réservé"

## 9. Running tests

```bash
cd hlm-backend

# Unit tests (Surefire)
./mvnw test

# Integration tests (Failsafe — requires Docker for Testcontainers)
./mvnw failsafe:integration-test failsafe:verify
```

```bash
cd hlm-frontend

# Build check
npm run build
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `Connection refused :8080` | Backend not running | Check logs for startup errors |
| `JWT_SECRET` startup failure | Missing or < 32 chars | Set `JWT_SECRET` >= 32 characters in `.env` |
| 401 on login | Wrong credentials or expired token | Use seeded creds: `acme` / `admin@acme.com` / `Admin123!` |
| 403 on API call | Insufficient role | Check JWT `roles` claim matches required role |
| CORS error in browser | Origin not in `CorsConfig` | Use the Angular proxy (port 4200) or add your origin |
| Testcontainers fails | Docker not running | Run `docker info` to verify Docker is accessible |

See [runbook.md](runbook.md) for detailed troubleshooting.
