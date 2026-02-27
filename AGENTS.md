# Agent Context: YEM SaaS Platform

## Project Summary
- Multi-tenant CRM platform with a Spring Boot backend (`hlm-backend`) and Angular SPA (`hlm-frontend`).
- Tenant isolation is enforced from JWT claim `tid` into `TenantContext` and tenant-scoped repository queries.
- RBAC uses `ROLE_ADMIN`, `ROLE_MANAGER`, and `ROLE_AGENT` with Spring Security `@PreAuthorize` checks.
- Database schema is PostgreSQL + Liquibase; Hibernate runs with schema validation (`ddl-auto: validate`).

## Repo Map
- `hlm-backend/` — Java 21 Spring Boot API (auth, tenant, users, contacts, properties, projects, deposits, notifications).
- `hlm-frontend/` — Angular 19 SPA (login, shell, properties, projects, auth guard/interceptor).
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
  - Feature packages: `auth`, `tenant`, `user`, `contact`, `property`, `project`, `deposit`, `contract`, `notification`, `outbox`, `common`.
- Controllers expose DTOs under `api/dto`; services contain business rules and tenant checks.
- Frontend structure: `core/` (auth + shared models), `features/` (pages — properties, projects, contacts, prospects, notifications, outbox, admin-users), route config in `app.routes.ts`.

### API conventions
- Auth header: `Authorization: Bearer <JWT>`.
- JWT claims: `sub` (user), `tid` (tenant), `roles`, `tv` (tokenVersion — see revocation below).
- Error contract is standardized via `common/error/ErrorResponse` + `ErrorCode`.
- Validation and malformed JSON errors map to HTTP 400 with stable error code fields.

### Security: JWT Revocation
- Every JWT carries a `tv` (tokenVersion) integer claim stamped at login time.
- `User.tokenVersion` is stored in the DB and incremented when the user's role changes or the account is disabled.
- On each request, `JwtAuthenticationFilter` calls `UserSecurityCacheService.getSecurityInfo(userId)` and rejects the token (→ 401) if:
  - `secInfo` is null (user deleted), OR
  - `secInfo.enabled()` is false, OR
  - `secInfo.tokenVersion() != tv` (token issued before the last role change/disable).
- Cache: `UserSecurityCacheService` uses Spring Cache (`userSecurityCache`) to avoid per-request DB hits; evicted on role change or disable.

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
- Project lifecycle (`ProjectStatus`): `ACTIVE` | `ARCHIVED`.
  - Only `ACTIVE` projects may receive new or reassigned properties; assigning to `ARCHIVED` → 400 `ARCHIVED_PROJECT`.
  - Archive via `DELETE /api/projects/{id}` (sets status; does not physically delete).
  - KPI aggregation: `GET /api/projects/{id}/kpis` — requires `ROLE_ADMIN` or `ROLE_MANAGER`.
- Property lifecycle enum (`PropertyStatus`): `DRAFT`, `ACTIVE`, `RESERVED`, `SOLD`, `WITHDRAWN`, `ARCHIVED`.
- Deposit workflow (`DepositStatus` + service rules):
  - Creation sets `PENDING` and moves property to `RESERVED`.
  - `confirm()` allows only `PENDING -> CONFIRMED`; acquires pessimistic write lock on property and rejects if property is `SOLD` → 409.
  - `cancel()`/expiry move deposit to `CANCELLED`/`EXPIRED` and release property back to `ACTIVE` when applicable.
  - **Reservation PDF**: `GET /api/deposits/{id}/documents/reservation.pdf` — generates an "Attestation de Réservation" PDF. Tenant-scoped; cross-tenant → 404. **RBAC**: ADMIN/MANAGER → any deposit in tenant; AGENT → own deposits only (cross-ownership → 404). Response: `application/pdf`, `Content-Disposition: attachment; filename="reservation_<id>.pdf"`.
    - **Architecture**: `ReservationDocumentService` (orchestrator, RBAC check, model builder) → `DocumentGenerationService` (Thymeleaf HTML → OpenHTMLToPDF) → PDF bytes.
    - **Template location**: `hlm-backend/src/main/resources/templates/documents/reservation.html` (Thymeleaf). Edit this file to change labels/layout/branding. Model fields defined in `ReservationDocumentModel` record.
    - **Dependencies**: `com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10` + `spring-boot-starter-thymeleaf`. No PDFBox 3.x (removed to avoid API version clash with openhtmltopdf transitive PDFBox 2.x).
    - **N+1 avoidance**: deposit loaded via `DepositRepository.findForPdf()` (JOIN FETCH tenant + contact + agent). Property loaded in a second query.
- Sales Contract workflow (`SaleContractStatus`) + Commercial KPI semantics:
  - **Sale = Contract SIGNED** (deposit is pre-sale / reservation step).
  - Lifecycle: `DRAFT → SIGNED → CANCELED` (or `DRAFT → CANCELED`).
  - RBAC: `POST /api/contracts` — all roles; `POST /{id}/sign`, `POST /{id}/cancel` — ADMIN/MANAGER only; `GET /api/contracts` — all roles (AGENT sees own only, enforced in service).
  - `sign()`: property moves to `SOLD`; service-layer guard + DB partial unique index (`uk_sc_property_signed`) prevent double-selling.
  - `cancel()` of SIGNED contract: property reverts to `RESERVED` (if a CONFIRMED deposit exists for the property, via `DepositRepository.existsActiveConfirmedDepositForProperty()`) or `ACTIVE` (= AVAILABLE). Lock ordering: Property lock acquired BEFORE contract save to prevent deadlocks with `sign()` and `DepositService.confirm()`.
  - `ProjectActiveGuard.requireActive()` checked on both `create()` and `sign()`.
  - `PropertyCommercialWorkflowService` is the SSOT for all property commercial status transitions.
  - **Buyer snapshot** (P0-2, Liquibase 018): At `sign()`, `SaleContractService.captureBuyerSnapshot()` stores an immutable copy of buyer data on the contract row (`buyerType`, `buyerDisplayName`, `buyerPhone`, `buyerEmail`, `buyerIce`, `buyerAddress`). Decouples legal records from future Contact edits. `buyerType` defaults to `BuyerType.PERSON`.
  - **KPI contract** (locked — see `docs/ai/PROJECT_CONTEXT.md` for full table):
    - Reservation = deposit PENDING or CONFIRMED, property `RESERVED`.
    - Sale = `SaleContract.status = SIGNED`, revenue = `agreedPrice`.
    - Discount analytics = `listPrice - agreedPrice` (when `listPrice` set).
    - Reservation cancellation = deposit CANCELLED/EXPIRED → property `ACTIVE`.
    - Sale cancellation = signed contract CANCELED → property `RESERVED` or `ACTIVE`.
- Commercial Dashboard (`dashboard` package):
  - **Endpoints**: `GET /api/dashboard/commercial` (alias, accepts `YYYY-MM-DD` date params) + `GET /api/dashboard/commercial/summary` (canonical, accepts ISO datetime) + `GET /api/dashboard/commercial/sales` (drill-down, paged).
  - **Query params**: `from`, `to` (ISO date or datetime, default last 30 days), `projectId` (optional), `agentId` (optional).
  - **RBAC**: all authenticated roles; AGENT callers have `agentId` forced to self (ignoring supplied value); ADMIN/MANAGER see full tenant data.
  - **Summary DTO** (`CommercialDashboardSummaryDTO`): `asOf` (freshness timestamp), `salesCount`, `salesTotalAmount`, `avgSaleValue`, `depositsCount` (period-filtered CONFIRMED), `depositsTotalAmount`, `activeReservationsCount` (current PENDING+CONFIRMED, not date-filtered), `activeReservationsTotalAmount`, `avgReservationAgeDays`, `salesByProject[]` (top 10), `salesByAgent[]` (top 10), `inventoryByStatus{}`, `inventoryByType{}`, `salesAmountByDay[]`, `depositsAmountByDay[]`, `conversionDepositToSaleRate`, `avgDaysDepositToSale`.
  - **Caching**: Caffeine cache `commercialDashboardSummaryCache`, TTL 30 s, max 500 entries. Key = `tenantId:effectiveAgentId:from:to:projectId`.
  - **Query budget**: up to 10 aggregate queries per summary request; no entity hydration.
  - **Validation**: `from > to` → 400 (`InvalidPeriodException`); unknown `projectId` in tenant → 404; unknown `agentId` in tenant → 404.
  - **Angular route**: `/app/dashboard/commercial` (`CommercialDashboardComponent`); drill-down at `/app/dashboard/commercial/sales`. Dashboard nav entry visible to all authenticated roles.

- Outbound Messaging / Outbox (`outbox` package — PR-6, REQ-2-3-MODULE-COMMERC-021):
  - **Purpose**: async dispatch of EMAIL and SMS messages to CRM contacts or arbitrary recipients, with full audit trail.
  - **Endpoints**:
    - `POST /api/messages` — compose and queue a message (returns `202 Accepted` + `{messageId}`). RBAC: all roles (AGENT, MANAGER, ADMIN).
    - `GET /api/messages` — paged list, tenant-scoped. Query params: `channel`, `status`, `contactId`, `from`, `to`, `page`, `size`.
  - **Request path (fast)**: only validates + inserts a `PENDING` outbox row — never calls a provider directly.
  - **Dispatch (async)**: `OutboundDispatcherScheduler` polls every `${app.outbox.polling-interval-ms:5000}` ms and calls `OutboundDispatcherService.runDispatch()`.
    - Uses `SELECT … FOR UPDATE SKIP LOCKED` (native query) so concurrent instances never double-dispatch.
    - Backoff: attempt 1 → +1 min, attempt 2 → +5 min, attempt 3+ → +30 min. After `${app.outbox.max-retries:3}` attempts → `FAILED`.
  - **Provider interfaces**: `EmailSender` / `SmsSender` in `outbox/service/provider/`. Default: `NoopEmailSender` / `NoopSmsSender` (log-only). Swap by providing a `@Primary` Spring bean.
  - **Tenant isolation**: `MessageComposeService` reads `TenantContext` — all messages are scoped to the current tenant.
  - **Config keys** (`application.yml`, overridable via env vars):
    - `app.outbox.batch-size` (env `OUTBOX_BATCH_SIZE`, default 20)
    - `app.outbox.max-retries` (env `OUTBOX_MAX_RETRIES`, default 3)
    - `app.outbox.polling-interval-ms` (env `OUTBOX_POLL_INTERVAL_MS`, default 5000)
  - **Test profile**: scheduler is disabled (`spring.task.scheduling.enabled=false`). Call `OutboundDispatcherService.runDispatch()` directly in ITs.
  - **IT**: `OutboxIT` (9 tests — 202/PENDING for email+SMS, tenant isolation, contact derivation, validation errors, 401, dispatcher PENDING→SENT).
  - **Frontend**: `features/outbox/` — list with status/channel filter + inline compose form; route `/app/messages`; nav item "Messages".
  - **Local verification**:
    ```bash
    cd hlm-backend && ./mvnw test
    cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=OutboxIT
    cd hlm-frontend && npm run build
    ```

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
