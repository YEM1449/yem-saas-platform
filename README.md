# YEM SaaS Platform

Multi-tenant CRM backend for real estate promotion and construction teams.
Tenant isolation via JWT claim `tid` → request-scoped `TenantContext`.
RBAC with three roles: ADMIN, MANAGER, AGENT (see [docs/runbook.md](docs/runbook.md#rbac-conventions)).

## Prerequisites

- **Java 21** (see `hlm-backend/pom.xml`)
- **Docker** (required for Testcontainers integration tests and local PostgreSQL)
- **PostgreSQL** (local instance or Docker container)

## Quickstart (backend)

```bash
# 1. Copy env template and fill in your values
cp .env.example .env
# Edit .env — set your DB credentials and a JWT secret (≥32 chars)

# 2. Export env vars (or use direnv / your IDE)
export $(grep -v '^#' .env | xargs)

# 3. Run the backend
cd hlm-backend
chmod +x mvnw
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.
Verify it's running:

```bash
curl -i http://localhost:8080/actuator/health
# Expected: 200 {"status":"UP"}
# If it fails: backend isn't running or is on another port.
# Check logs for "Tomcat started on port(s): 8080"
```

## Required Environment Variables

| Variable          | Example                                      | Notes                              |
|-------------------|----------------------------------------------|------------------------------------|
| `DB_URL`          | `jdbc:postgresql://localhost:5432/hlm`       | JDBC connection string             |
| `DB_USER`         | `hlm_user`                                   | Database username                  |
| `DB_PASSWORD`     | `hlm_pwd`                                    | Database password                  |
| `JWT_SECRET`      | *(generate a random ≥32-char string)*        | **Required**, no default. HS256    |
| `JWT_TTL_SECONDS` | `3600`                                       | Token lifetime (default 3600)      |

> These map to `application.yml` placeholders: `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, `${JWT_TTL_SECONDS}`.
>
> **Fail-fast:** The app refuses to start if `JWT_SECRET` is missing or blank (`JwtProperties` uses `@Validated` + `@NotBlank`).

## Running Tests

```bash
cd hlm-backend

# Unit tests (Surefire)
./mvnw test

# Integration tests (Failsafe, requires Docker for Testcontainers)
./mvnw failsafe:integration-test
```

## Frontend Integration Quickstart

### Step 0 — Health check

```bash
curl -i http://localhost:8080/actuator/health
```

If this fails, the backend is not running. Check the server logs for `Tomcat started on port(s): 8080`.

### Step 1 — Login

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}'
```

Response (`LoginResponse`):
```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### Step 2 — Verify identity

```bash
curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

Response:
```json
{ "userId": "...", "tenantId": "..." }
```

### Step 3 — Call a protected endpoint

```bash
curl -s http://localhost:8080/api/properties \
  -H "Authorization: Bearer <accessToken>"
```

### Error shapes

**401 Unauthorized** (missing/invalid token):
```json
{ "timestamp": "...", "status": 401, "error": "Unauthorized", "code": "UNAUTHORIZED", "message": "...", "path": "/api/properties" }
```

**403 Forbidden** (insufficient role):
```json
{ "timestamp": "...", "status": 403, "error": "Forbidden", "code": "FORBIDDEN", "message": "...", "path": "/api/properties" }
```

> Full curl walkthrough: [docs/api-quickstart.md](docs/api-quickstart.md)
> Troubleshooting (401/403/CORS): [docs/runbook.md](docs/runbook.md)
> Smoke test script: [scripts/smoke-auth.sh](scripts/smoke-auth.sh)

## Documentation

- [docs/index.md](docs/index.md) — full doc index
- [docs/api-quickstart.md](docs/api-quickstart.md) — curl flows for frontend integration
- [docs/runbook.md](docs/runbook.md) — local run, troubleshooting, CORS, RBAC
- AI Context Pack: [docs/ai/quick-context.md](docs/ai/quick-context.md) | [docs/ai/deep-context.md](docs/ai/deep-context.md) | [docs/ai/prompt-playbook.md](docs/ai/prompt-playbook.md)
- Agent working agreement: [GPT.md](GPT.md)
