# 05 — Developer Guide

> For a full walkthrough with demo flows, see [local-dev.md](local-dev.md).
> For all commands, see [../context/COMMANDS.md](../context/COMMANDS.md).

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 21 (Temurin recommended) | `java -version` |
| Docker | any | `docker info` |
| PostgreSQL | 14+ | `psql --version` |
| Node | 18+ | `node -v` |
| npm | 9+ | `npm -v` |

Docker is required for integration tests (Testcontainers). A local or Dockerized PostgreSQL is required for the backend.

## Environment Setup

```bash
cp .env.example .env
# Edit .env with your values
export $(grep -v '^#' .env | xargs)
```

Required variables:

| Variable | Purpose |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` |
| `DB_USER` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | 32+ char HMAC key (app refuses to start if blank/short) |
| `JWT_TTL_SECONDS` | Token TTL (default 3600) |

## Database Setup (Docker — recommended)

```bash
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm \
  -e POSTGRES_USER=hlm_user \
  -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 \
  postgres:16-alpine
```

Liquibase runs automatically on backend startup and applies all migrations.

## Running the Backend

```bash
cd hlm-backend
chmod +x mvnw        # first time only
./mvnw spring-boot:run
```

Verify: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

Seed credentials: tenant `acme`, email `admin@acme.com`, password `Admin123!`

## Running the Frontend

```bash
cd hlm-frontend
npm ci
npm start
```

Opens at http://localhost:4200. The dev proxy routes `/auth`, `/api`, `/dashboard`, `/actuator` to `:8080` — **use relative paths in frontend code, never hardcode `:8080`.**

## Testing

### Backend Unit Tests (fast, no Docker)

```bash
cd hlm-backend && ./mvnw test
```

Uses Surefire, runs `*Test` classes (~36 tests as of 2026-03).

### Backend Integration Tests (requires Docker)

```bash
cd hlm-backend && ./mvnw failsafe:integration-test
```

Uses Failsafe, runs `*IT` classes. Testcontainers spins up a PostgreSQL container automatically.

Base class: `IntegrationTestBase` — extends `@SpringBootTest` with `@ActiveProfiles("test")`.
Annotation shortcut: `@IntegrationTest` = `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")`.

### Frontend Tests

```bash
cd hlm-frontend && npm test
# CI mode (no watch, headless, coverage):
cd hlm-frontend && npm test -- --watch=false --browsers=ChromeHeadless --code-coverage --progress=false
```

## Adding a New Backend Feature

1. **New entity** → add Liquibase changeset (`NNN_description.yaml`) in `db/changelog/`. Never edit applied changesets.
2. **Domain class** → add to the appropriate package (e.g., `contract/domain/`).
3. **Repository** → extend `JpaRepository` or `JpaSpecificationExecutor`.
4. **Service** → read `TenantContext.getTenantId()` for all queries. Never trust tenant from client payload.
5. **Controller** → use DTO contract. Use `@PreAuthorize("hasRole('ADMIN')")` (not `ROLE_ADMIN` — Spring adds prefix).
6. **Test** → unit test for service logic; `*IT` integration test for controller RBAC + CRUD.

## RBAC Quick Reference

| Annotation | Who can call |
|-----------|-------------|
| `@PreAuthorize("hasRole('ADMIN')")` | ROLE_ADMIN only |
| `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")` | ROLE_ADMIN + ROLE_MANAGER |
| `@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")` | All CRM users |
| No annotation + `/api/**` in SecurityConfig | All authenticated CRM users |
| `@PreAuthorize("hasRole('PORTAL')")` | Portal clients only |

## Adding a Liquibase Changeset

1. Find the next number: check `db.changelog-master.yaml` for the last included file.
2. Create `hlm-backend/src/main/resources/db/changelog/NNN_description.yaml`.
3. Add the include to `db.changelog-master.yaml`.
4. Write the changeset with a unique `id` and your `author`.
5. **Do not edit previously applied changesets.**

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused :8080` | Backend not running | Check startup logs |
| JWT startup failure | `JWT_SECRET` missing/blank | Set `JWT_SECRET` ≥ 32 chars |
| 401 on valid request | Token expired or wrong credentials | Re-login with seeded creds |
| 403 on API call | Insufficient role | Check JWT `roles` claim |
| CORS error in browser | Not using proxy | Use port 4200, not 8080 |
| Testcontainers fails | Docker not running | `docker info` |
| Liquibase checksum error | Edited applied changeset | Revert changeset to original |

See [runbook.md](runbook.md) for extended troubleshooting.

## Code Style Conventions

→ See [../context/CONVENTIONS.md](../context/CONVENTIONS.md) for full conventions.

**Key rules:**
- Controller → DTO only (no entity exposure)
- Error: use `ErrorCode` enum + `ErrorResponse` envelope (never raw strings)
- Multi-tenancy: `TenantContext.getTenantId()` in services, never from request body
- `@PreAuthorize`: `hasRole('ADMIN')` not `hasRole('ROLE_ADMIN')`
- Package structure: `api/`, `service/`, `repo/`, `domain/` per feature
- Frontend: relative API paths only (no hardcoded `localhost:8080`)
