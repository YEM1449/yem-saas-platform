# Agent Context: YEM SaaS Platform

## Project Summary
- Multi-tenant CRM platform with a Spring Boot backend (`hlm-backend`) and Angular SPA (`hlm-frontend`).
- Tenant isolation is enforced from JWT claim `tid` into `TenantContext` and tenant-scoped repository queries.
- RBAC uses `ROLE_ADMIN`, `ROLE_MANAGER`, and `ROLE_AGENT` with Spring Security `@PreAuthorize` checks.
- Database schema is PostgreSQL + Liquibase; Hibernate runs with schema validation (`ddl-auto: validate`).

## Repo Map
- `hlm-backend/` — Java 21 Spring Boot API (auth, tenant, users, contacts, properties, deposits, notifications).
- `hlm-frontend/` — Angular 19 SPA (login, shell, properties, auth guard/interceptor).
- `docs/` — architecture, backend/frontend guides, API docs, runbook, contributing rules.
- `scripts/` — shell utilities (notably `smoke-auth.sh` for auth + protected endpoint verification).
- `README.md` — top-level quickstart and env expectations.
- `GPT.md` — short working agreement for Codex/GPT agents.

## Quick Start (Dev Setup)
### Prerequisites
- Java 21
- Node 18+ and npm 9+
- Docker (required for Testcontainers integration tests)
- PostgreSQL (local or container)

### Install
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```
```bash
cd hlm-frontend
npm ci
```

### Run backend
```bash
cd hlm-backend
chmod +x mvnw
./mvnw spring-boot:run
```

### Run frontend
```bash
cd hlm-frontend
npm start
```

### Run locally with env files
- Required env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`; optional `JWT_TTL_SECONDS`.
- The app fails fast if `JWT_SECRET` is blank.
- Frontend proxy (`hlm-frontend/proxy.conf.json`) forwards `/auth`, `/api`, `/dashboard`, `/actuator` to backend.

## Commands (use exact repo commands)
### Build
- Backend package/build:
  ```bash
  cd hlm-backend
  ./mvnw -DskipTests=false verify
  ```
- Frontend production build:
  ```bash
  cd hlm-frontend
  npm run build
  ```

### Test
- Backend unit tests:
  ```bash
  cd hlm-backend
  ./mvnw test
  ```
- Backend integration tests (Docker/Testcontainers required):
  ```bash
  cd hlm-backend
  ./mvnw failsafe:integration-test
  ```
- Frontend tests:
  ```bash
  cd hlm-frontend
  npm test
  ```
- API smoke test:
  ```bash
  TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
  ```

### Lint / format
- TODO verify lint/format command for backend (`hlm-backend/pom.xml` has no dedicated lint plugin configured).
- TODO verify lint/format command for frontend (`hlm-frontend/package.json` has no `lint` script).

### DB migrations
- Migrations run automatically on backend startup via Liquibase.
- Additive migration location: `hlm-backend/src/main/resources/db/changelog/changes/`.
- Master changelog: `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`.

## Architecture & Patterns
### Module boundaries
- Backend follows feature packages: `*/api`, `*/service`, `*/repo`, `*/domain`.
- Controllers expose DTOs under `api/dto`; services contain business rules and tenant checks.
- Frontend structure: `core/` (auth + shared models), `features/` (pages), route config in `app.routes.ts`.

### API conventions
- Auth header: `Authorization: Bearer <JWT>`.
- JWT claims: `sub` (user), `tid` (tenant), `roles`.
- Error contract is standardized via `common/error/ErrorResponse` + `ErrorCode`.
- Validation and malformed JSON errors map to HTTP 400 with stable error code fields.

### Persistence conventions
- Never edit already-applied Liquibase changesets; create a new changeset instead.
- Tenant-safe data access uses tenant-scoped repository methods (`findByTenant...`).
- Hibernate schema mode is validation only; schema change must be through Liquibase.

## Business Workflows (from code)
- Contact status state machine (`ContactStatus`):
  - `PROSPECT -> QUALIFIED_PROSPECT|LOST`
  - `QUALIFIED_PROSPECT -> PROSPECT|CLIENT|LOST`
  - `CLIENT -> ACTIVE_CLIENT|COMPLETED_CLIENT|LOST`
  - `ACTIVE_CLIENT -> COMPLETED_CLIENT|LOST`
  - `COMPLETED_CLIENT -> REFERRAL`
  - `LOST -> PROSPECT`
- Property lifecycle enum (`PropertyStatus`): `DRAFT`, `ACTIVE`, `RESERVED`, `SOLD`, `WITHDRAWN`, `ARCHIVED`.
- Deposit workflow (`DepositStatus` + service rules):
  - Creation sets `PENDING` and moves property to `RESERVED`.
  - `confirm()` allows only `PENDING -> CONFIRMED`.
  - `cancel()`/expiry move deposit to `CANCELLED`/`EXPIRED` and release property back to `ACTIVE` when applicable.

## Coding Standards
- Follow existing layered design; keep tenant checks in service/repository boundaries.
- Use DTO records for API payloads; avoid exposing entities directly from controllers.
- Reuse `GlobalExceptionHandler` patterns and existing domain exceptions for API errors.
- Log meaningful context; avoid logging secrets or raw tokens.
- Keep diffs minimal and scoped to one concern.

### Do / Don’t (PR-quality changes)
- Do add tests for changed behavior (unit first, integration when backend workflow/security changes).
- Do update docs when setup, commands, API behavior, or workflows change.
- Do use placeholders only for env/secrets.
- Don’t trust tenant IDs from request payloads when tenant context is available.
- Don’t bypass RBAC checks in controllers/services.
- Don’t modify historical Liquibase changesets.

## Testing Guidance
- Small backend logic change: run `./mvnw test` in `hlm-backend`.
- Backend endpoint/security/tenant change: run `./mvnw test` + `./mvnw failsafe:integration-test`.
- Frontend UI/service change: run `npm run build` and `npm test` in `hlm-frontend`.
- End-to-end auth sanity check: run `scripts/smoke-auth.sh` against a running backend.

## Safe Agent Workflow
- Read `README.md`, `docs/ai/quick-context.md`, and relevant feature package before editing.
- Prefer small, reviewable patches over broad refactors.
- Validate with the smallest relevant command set, then expand if touching cross-cutting flows.
- Update docs (`README.md`, `docs/*`, `AGENTS.md`, `CLAUDE.md`) when behavior or commands change.
- Never commit secrets, tokens, or populated `.env` files.
