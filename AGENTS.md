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
  - Feature packages: `auth`, `tenant`, `user`, `contact`, `property`, `project`, `deposit`, `contract`, `notification`, `outbox`, `audit`, `dashboard`, `payments`, `reminder`, `media`, `commission`, `portal`, `common`.
- Controllers expose DTOs under `api/dto`; services contain business rules and tenant checks.
- Frontend structure: `core/` (auth + shared models), `features/` (pages — properties, property-detail, projects, contacts, prospects, notifications, outbox, contracts, payments, admin-users, dashboard, commissions), `portal/` (client-facing portal — separate route tree at `/portal/*`), route config in `app.routes.ts`.

### API conventions
- Auth header: `Authorization: Bearer <JWT>`.
- CRM JWT claims: `sub` (userId), `tid` (tenantId), `roles`, `tv` (tokenVersion — see revocation below).
- Portal JWT claims: `sub` (contactId), `tid` (tenantId), `roles=["ROLE_PORTAL"]`. No `tv` claim — stateless; TTL 2 h.
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
  - **Contract PDF**: `GET /api/contracts/{id}/documents/contract.pdf` — generates a bilingual contract PDF. Tenant-scoped; cross-tenant → 404. **RBAC**: ADMIN/MANAGER → any contract in tenant; AGENT → own contracts only (cross-ownership → 404, avoids info leak). Response: `application/pdf`, `Content-Disposition: attachment; filename="contract_<id>.pdf"`.
    - **Architecture**: `ContractDocumentService` (orchestrator, RBAC check, model builder) → `DocumentGenerationService` (Thymeleaf HTML → OpenHTMLToPDF) → PDF bytes.
    - **Template location**: `hlm-backend/src/main/resources/templates/documents/contract.html` (Thymeleaf). 5 sections: property, prices, buyer, agent, signatures. Edit to change labels/branding.
    - **Buyer data precedence**: snapshot fields (`buyerDisplayName`, `buyerPhone`, etc.) preferred when set (SIGNED contracts); falls back to live Contact fields for DRAFT contracts.
    - **N+1 avoidance**: `SaleContractRepository.findForPdf()` JOIN FETCH on tenant + buyerContact + agent + project + property in a single query.
    - **Frontend**: `GET /api/contracts` returns `List<ContractResponse>` (filter params: `status`, `projectId`, `agentId`, `from`, `to`). Angular route `/app/contracts` (`ContractsComponent` in `features/contracts/`). PDF download via `ContractService.downloadPdf()` (blob response).
- Commercial Dashboard (`dashboard` package):
  - **Endpoints**: `GET /api/dashboard/commercial` (alias, accepts `YYYY-MM-DD` date params) + `GET /api/dashboard/commercial/summary` (canonical, accepts ISO datetime) + `GET /api/dashboard/commercial/sales` (drill-down, paged).
  - **Query params**: `from`, `to` (ISO date or datetime, default last 30 days), `projectId` (optional), `agentId` (optional).
  - **RBAC**: all authenticated roles; AGENT callers have `agentId` forced to self (ignoring supplied value); ADMIN/MANAGER see full tenant data.
  - **Summary DTO** (`CommercialDashboardSummaryDTO`): `asOf` (freshness timestamp), `salesCount`, `salesTotalAmount`, `avgSaleValue`, `depositsCount` (period-filtered CONFIRMED), `depositsTotalAmount`, `activeReservationsCount` (current PENDING+CONFIRMED, not date-filtered), `activeReservationsTotalAmount`, `avgReservationAgeDays`, `activeProspectsCount` (contacts with status PROSPECT or QUALIFIED_PROSPECT, tenant-wide, not date-filtered), `salesByProject[]` (top 10), `salesByAgent[]` (top 10), `inventoryByStatus{}`, `inventoryByType{}`, `salesAmountByDay[]`, `depositsAmountByDay[]`, `conversionDepositToSaleRate`, `avgDaysDepositToSale`, `avgDiscountPercent`, `maxDiscountPercent`, `discountByAgent[]` (top-10 by avg discount; requires `listPrice` set on contracts), `prospectsBySource[]` (source funnel from `ProspectDetail.source`).
  - **Caching**: Caffeine cache `commercialDashboardSummaryCache`, TTL 30 s, max 500 entries. Key = `tenantId:effectiveAgentId:from:to:projectId`.
  - **Query budget**: up to 14 aggregate queries per summary request; no entity hydration.
  - **Validation**: `from > to` → 400 (`InvalidPeriodException`); unknown `projectId` in tenant → 404; unknown `agentId` in tenant → 404.
  - **Observability**: `Timer("commercial_dashboard_summary_duration")` measures cache-miss computation time; `Counter("commercial_dashboard_summary_cache_misses_total")` counts cache misses; `Counter("commercial_dashboard_summary_requests_total")` in controller counts all requests. Slow-query warning logged at WARN level when computation exceeds 300 ms. No new Maven dependencies — Micrometer Core is included transitively via `spring-boot-starter-actuator`.
  - **Angular route**: `/app/dashboard/commercial` (`CommercialDashboardComponent`); drill-down at `/app/dashboard/commercial/sales`. Dashboard nav entry visible to all authenticated roles.

- Payment lifecycle (`payment` package — PR-8):
  - `PaymentSchedule` linked 1:1 to `SaleContract`. Contains ordered `PaymentTranche` rows (one per milestone).
  - `TrancheStatus`: `PLANNED → ISSUED → PARTIALLY_PAID | PAID | OVERDUE`.
  - `PaymentCall` (Appel de Fonds): issued per tranche. Status: `DRAFT → ISSUED → OVERDUE | CLOSED`.
    - `issueCall(trancheId)`: moves tranche `PLANNED → ISSUED`, creates ISSUED call; audit event `PAYMENT_CALL_ISSUED`.
    - Overdue scheduler: cron marks ISSUED calls whose `due_date < today` as `OVERDUE`; disabled in test profile via `@ConditionalOnProperty("spring.task.scheduling.enabled")`.
  - `Payment` (cash-in): recorded against a call. Updates tranche to `PARTIALLY_PAID` or `PAID`; closes call when fully paid. Audit event `PAYMENT_RECEIVED`.
  - **RBAC**: create/update/issue/record → ADMIN/MANAGER; read → all roles (AGENT: own contracts only, enforced in service via `contract.getAgent().getId()` check).
  - **Appel de Fonds PDF**: `GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf`. Architecture: `PaymentCallDocumentService` → `DocumentGenerationService` → OpenHTMLToPDF. Template: `templates/documents/appel-de-fonds.html`. N+1 avoidance: `PaymentCallRepository.findForPdf()` JOIN FETCH.
  - **Error codes**: `PAYMENT_SCHEDULE_EXISTS` (409), `INVALID_TRANCHE_SUM` (400), `TRANCHE_NOT_FOUND` (404), `PAYMENT_CALL_NOT_FOUND` (404), `INVALID_CALL_STATE` (409), `PAYMENT_EXCEEDS_DUE` (400).
  - **Endpoints**:
    - `GET  /api/contracts/{contractId}/payment-schedule` — all roles
    - `POST /api/contracts/{contractId}/payment-schedule` — ADMIN/MANAGER
    - `PATCH /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}` — ADMIN/MANAGER
    - `POST /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call` — ADMIN/MANAGER
    - `GET  /api/payment-calls` — all roles (paged)
    - `GET  /api/payment-calls/{id}` — all roles
    - `GET  /api/payment-calls/{id}/documents/appel-de-fonds.pdf` — all roles (AGENT own only)
    - `GET  /api/payment-calls/{id}/payments` — all roles
    - `POST /api/payment-calls/{id}/payments` — ADMIN/MANAGER

- Commercial Audit Trail (`audit` package — PR-7):
  - **Purpose**: immutable per-tenant event log for commercial workflow events (deposit lifecycle + contract lifecycle).
  - **DB table**: `commercial_audit_event` — Liquibase changeset 019. Columns: `id` (UUID PK), `tenant_id` (FK), `event_type` (VARCHAR 50), `actor_user_id` (UUID), `correlation_type` (VARCHAR 50), `correlation_id` (UUID), `occurred_at` (TIMESTAMP), `payload_json` (TEXT).
  - **Events recorded** (`AuditEventType` enum): `DEPOSIT_CREATED`, `DEPOSIT_CONFIRMED`, `DEPOSIT_CANCELED`, `DEPOSIT_EXPIRED`, `CONTRACT_CREATED`, `CONTRACT_SIGNED`, `CONTRACT_CANCELED`.
  - **Wiring**: `CommercialAuditService.record()` is `@Transactional(REQUIRED)` — audit event participates in the caller's transaction and rolls back atomically if the business operation fails.
    - `DepositService`: records on `create()`, `confirm()`, `cancel()`, `expireDeposit()`.
    - `SaleContractService`: records on `create()`, `sign()`, `cancel()`.
  - **API**: `GET /api/audit/commercial` — RBAC: ADMIN/MANAGER only. Query params: `from` (ISO datetime), `to`, `correlationType` (e.g. `DEPOSIT`, `CONTRACT`), `correlationId` (UUID), `limit` (default 100, max 500). Returns `List<AuditEventResponse>` ordered by `occurredAt DESC`, tenant-scoped.
  - **IT**: `CommercialAuditIT` (4 tests: DEPOSIT_CONFIRMED event present, CONTRACT_SIGNED event present, tenant isolation, AGENT → 403).

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
  - **Frontend**: `features/outbox/` — list with status/channel filter + inline compose form; route `/app/messages`; nav item "Messages". **Quick action buttons**: Email/SMS buttons are also embedded inline in the contracts list (`features/contracts/`) and the deposits table in `prospect-detail` — they call `OutboxService.send()` with a pre-filled body and `correlationType/correlationId` for traceability. Email button shown only if buyer/contact email available; SMS button only if phone available.
  - **Local verification**:
    ```bash
    cd hlm-backend && ./mvnw test
    cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=OutboxIT
    cd hlm-frontend && npm run build
    ```

- Payment Schedule / Appels de fonds (`payments` package — PR-8):
  - **Purpose**: track staged payment milestones per signed contract (e.g. deposit %, foundation %, keys %). Each schedule item represents one "appel de fonds".
  - **Liquibase**: changeset 020 — three tables: `payment_schedule_item`, `schedule_payment`, `schedule_item_reminder`.
  - **Status state machine** (`PaymentScheduleStatus`): `DRAFT → ISSUED → SENT | OVERDUE → PAID` or any `→ CANCELED`.
    - `issue()`: DRAFT → ISSUED (sets `issuedAt`).
    - `send()`: must be `ISSUED|SENT|OVERDUE` — queues outbox row via `MessageComposeService`, transitions to SENT (sets `sentAt`).
    - `cancel()`: any non-terminal state → CANCELED.
    - `addPayment()`: sums payments; when `sumPaid >= amount` → auto-transitions to PAID.
  - **Endpoints** (in `PaymentScheduleController`):
    - `GET  /api/contracts/{id}/schedule` — list items for a contract (all roles).
    - `POST /api/contracts/{id}/schedule` — create DRAFT item (ADMIN/MANAGER).
    - `PUT  /api/schedule-items/{id}` — update DRAFT item (ADMIN/MANAGER).
    - `DELETE /api/schedule-items/{id}` — delete DRAFT item (ADMIN/MANAGER).
    - `POST /api/schedule-items/{id}/issue` — DRAFT → ISSUED (ADMIN/MANAGER).
    - `POST /api/schedule-items/{id}/send` — ISSUED → SENT + outbox queued (ADMIN/MANAGER).
    - `POST /api/schedule-items/{id}/cancel` — any → CANCELED (ADMIN/MANAGER).
    - `GET  /api/schedule-items/{id}/pdf` — download call-for-funds PDF (all roles).
    - `GET  /api/schedule-items/{id}/payments` — list payments on item (all roles).
    - `POST /api/schedule-items/{id}/payments` — record a payment (ADMIN/MANAGER).
    - `POST /api/schedule-items/reminders/run` — trigger reminder batch manually (ADMIN).
  - **Cash KPI dashboard**: `GET /api/dashboard/commercial/cash?from=&to=` — returns `CashDashboardResponse` with `expectedInPeriod`, `issuedInPeriod`, `collectedInPeriod`, `overdueAmount`, `overdueCount`, aging buckets (0-30/31-60/61-90/91+ days), next 10 upcoming due items. Cacheable (`cashDashboard`, 60 s TTL, 200 entries). RBAC: all authenticated roles.
  - **Call-for-funds PDF**: `GET /api/schedule-items/{id}/pdf` — "Appel de Fonds" PDF via Thymeleaf + OpenHTMLToPDF. Template: `hlm-backend/src/main/resources/templates/documents/call_for_funds.html`. Model: `CallForFundsPdfService` builds `CallForFundsDocumentModel` (tenant, project/property, buyer snapshot, schedule item fields, amountPaid/amountRemaining, agent).
  - **Automatic reminders** (`ReminderService` + `ReminderScheduler`):
    - Pre-due: D-7 and D-1 before `dueDate`.
    - Overdue: D+1, D+7, D+30 after `dueDate`.
    - Overdue items: `ISSUED|SENT` items past due date → auto-transitioned to `OVERDUE`.
    - Idempotency: `ScheduleItemReminder` table with unique constraint `(schedule_item_id, reminder_type, channel, reminder_date)`.
    - Scheduler runs via `ReminderScheduler` (cron `${app.payments.reminder-cron:0 0 7 * * *}`; env `PAYMENTS_REMINDER_CRON`). Disabled in test profile.
    - Writes directly to `OutboundMessageRepository` (uses contract agent as `createdByUser`; bypasses `MessageComposeService` since no HTTP session in scheduler context).
  - **Error codes**: `PAYMENT_SCHEDULE_ITEM_NOT_FOUND` (404), `INVALID_PAYMENT_SCHEDULE_STATE` (409), `PAYMENT_INVALID_AMOUNT` (400).
  - **Config**: `app.payments.reminder-cron` (env `PAYMENTS_REMINDER_CRON`, default `0 0 7 * * *`).
  - **IT**: `PaymentScheduleIT` (10 tests: create+list, DRAFT→ISSUED, partial payment, full payment→PAID, cancel→issue=409, agent forbidden on write, agent can read, PDF download, tenant isolation, send ISSUED→SENT).
  - **Frontend**: `features/contracts/contract-detail.component` — tabbed detail view ("Informations" + "Échéancier" tab with `<app-payment-schedule>` child component). Route `/app/contracts/:id`. Payment schedule service at `features/contracts/payment-schedule.service.ts`. Cash dashboard at `features/dashboard/cash-dashboard.component` (route `/app/dashboard/commercial/cash`; nav link "Encaissements").

- Operational Polish (`reminder` + `media` + contact timeline + CSV import — PR-9):
  - **F2.1 Contact Activity Timeline**: `GET /api/contacts/{id}/timeline?limit=50` (all roles). `ContactTimelineService` aggregates `CommercialAuditRepository`, `OutboundMessageRepository`, `NotificationRepository` via shared correlation IDs. Frontend: `contact-detail.component` adds "Fiche / Historique" tabs; timeline lazy-loaded on first click. IT: `ContactTimelineIT`.
  - **F2.2 Automated Reminders** (`reminder` package): `ReminderService` (3 idempotent workflows). Deposit due-date: EMAIL at J-7/J-3/J-1. Payment call overdue: EMAIL to agent + in-app `PAYMENT_CALL_OVERDUE` to ADMINs. Prospect follow-up: in-app `PROSPECT_STALE` after 14 days of inactivity. `ReminderScheduler` fires daily at 08:00; disabled in test profile via `@ConditionalOnProperty("spring.task.scheduling.enabled")`. Config: `app.reminder.{enabled,cron,deposit-warn-days,prospect-stale-days}`. Unit: `ReminderServiceTest` (5 tests).
  - **F2.3 Property Media** (`media` package): Liquibase changeset 023 (`property_media` table). `MediaStorageService` interface + `LocalFileMediaStorage` default (UUID-keyed files at `app.media.storage-dir`; replace with S3 via `@Primary`). `PropertyMediaController` endpoints: `POST /api/properties/{id}/media` (ADMIN/MANAGER), `GET /api/properties/{id}/media`, `GET /api/media/{mediaId}/download`, `DELETE /api/media/{mediaId}` (ADMIN). Error codes: `MEDIA_TOO_LARGE` (400), `MEDIA_TYPE_NOT_ALLOWED` (400), `MEDIA_NOT_FOUND` (404). Config: `app.media.{storage-dir,max-file-size,allowed-types}`. Test override: `${java.io.tmpdir}/hlm-test-media`. Frontend: `property-detail.component` at `/app/properties/:id`. IT: `PropertyMediaIT` (7 tests).
  - **F2.4 CSV Import** (`property` package — `PropertyImportService`): `POST /api/properties/import` (ADMIN/MANAGER, multipart CSV). All-or-nothing validation: validates all rows before inserting any. 200 + `{imported}` on success; 422 + `{errors[{row,message}]}` on validation failure. Apache Commons CSV 1.12.0. Frontend: "Importer CSV" button on properties list; row-level error table on 422. IT: `PropertyImportIT` (6 tests).

- Commission Tracking (`commission` package — Phase 3):
  - **Liquibase changeset 024**: `commission_rule` table — tenant-scoped, optional project scope, rate_percent (DECIMAL 5,2), fixed_amount (nullable), effective_from / effective_to date range.
  - **Rule lookup priority**: project-specific rule (matching tenantId + projectId + date range) first; fallback to tenant-wide default (projectId IS NULL + date range). If no rule found, commission = 0.
  - **Commission formula**: `agreedPrice × ratePercent / 100 + fixedAmount` (fixedAmount defaults to 0).
  - **RBAC**:
    - `GET /api/commissions/my` — own commissions (all roles)
    - `GET /api/commissions?agentId=&from=&to=` — all commissions (ADMIN/MANAGER)
    - `GET|POST|PUT|DELETE /api/commission-rules` — ADMIN only
  - **ErrorCode**: `COMMISSION_RULE_NOT_FOUND` (404).
  - **IT**: `CommissionIT` (4 tests: default rule, project-specific override, AGENT scope, ADMIN sees all).
  - **Frontend**: `CommissionsComponent` at `/app/commissions` (nav link "Commissions", all roles). ADMIN rule CRUD embedded in same view.

- Receivables Dashboard (`dashboard` package, `ReceivablesDashboardService` — Phase 3):
  - **Endpoint**: `GET /api/dashboard/receivables` — all authenticated roles; AGENT auto-scoped to own contracts.
  - **Response** (`ReceivablesDashboardDTO`): `asOf`, `totalOutstanding`, `totalOverdue`, `collectionRate`, `avgDaysToPayment`, aging buckets (`current`/`days30`/`days60`/`days90`/`days90plus`), `overdueByProject[]` (top 10), `recentPayments[]` (last 10).
  - **Caching**: Caffeine `receivablesDashboard`, 30 s TTL, 200 entries.
  - **IT**: `ReceivablesDashboardIT` (3 tests).
  - **Frontend**: `ReceivablesDashboardComponent` at `/app/dashboard/receivables`; nav link "Receivables" (ADMIN/MANAGER only).

- Client-Facing Portal (`portal` package — Phase 4):
  - **Purpose**: read-only buyer portal accessible via magic link (no password). Buyers see their own contracts, payment schedule, and property details. Fully isolated from CRM using `ROLE_PORTAL`.
  - **Auth flow (magic link)**:
    1. Buyer POSTs `{email, tenantKey}` to `POST /api/portal/auth/request-link` (public).
       Service looks up tenant + contact (case-insensitive email), generates a 32-byte random token, stores SHA-256 hex hash in `portal_token` table (48 h TTL, one-time use), sends magic link via `EmailSender.send()` directly (not outbox — no authenticated user). Raw token URL also returned in response body for dev/test.
    2. Frontend calls `GET /api/portal/auth/verify?token=<rawToken>` (public).
       Service hashes raw token, validates (exists + not expired + not used), marks used, returns a 2-h portal JWT.
  - **JWT**: `PortalJwtProvider` uses same `JwtEncoder`/`JwtDecoder` beans as `JwtProvider` (shared HMAC-SHA256 key). Claims: `sub`=contactId, `tid`=tenantId, `roles`=["ROLE_PORTAL"]. No `tv` claim.
  - **`JwtAuthenticationFilter` behaviour for ROLE_PORTAL**: detects `ROLE_PORTAL` in roles claim → skips `UserSecurityCacheService` check (principal is a contactId, not a userId) → sets `TenantContext` directly. Sets contactId as Spring Security principal.
  - **SecurityConfig rule order** (matters!):
    1. `POST /api/portal/auth/request-link` → `permitAll()`
    2. `GET  /api/portal/auth/verify` → `permitAll()`
    3. `/api/portal/**` → `hasRole("PORTAL")`
    4. `/api/**` → `hasAnyRole("ADMIN","MANAGER","AGENT")` — ROLE_PORTAL is blocked here (→ 403).
  - **Liquibase**: changeset 025 — `portal_token` table: `id UUID PK`, `tenant_id FK`, `contact_id FK`, `token_hash VARCHAR(64) UNIQUE NOT NULL`, `expires_at TIMESTAMP`, `used_at TIMESTAMP NULL`, `created_at TIMESTAMP`. Indexes: `(tenant_id, contact_id)`, `expires_at`.
  - **PortalContractService**: all data-access methods extract contactId from `SecurityContextHolder.getAuthentication().getPrincipal()`. All queries scope to `(tenantId, contactId)`.
    - `listContracts()` → `SaleContractRepository.findPortalContracts(tenantId, contactId)`.
    - `getContractPdf(contractId)` → verifies buyer ownership → delegates to `ContractDocumentService.generate()` (`enforceAgentOwnership` in that service only restricts ROLE_AGENT, not ROLE_PORTAL — passes cleanly).
    - `getPaymentSchedule(contractId)` → verifies buyer ownership.
    - `getProperty(propertyId)` → checks `existsByTenant_IdAndProperty_IdAndBuyerContact_Id`.
    - `getTenantInfo()` → tenant name for portal shell header.
  - **Endpoints** (all `/api/portal/` require `ROLE_PORTAL`; auth endpoints are public):
    - `POST /api/portal/auth/request-link` — public; body `{email, tenantKey}`.
    - `GET  /api/portal/auth/verify?token=` — public; returns `{accessToken}`.
    - `GET  /api/portal/contracts` — buyer's own contracts.
    - `GET  /api/portal/contracts/{id}/documents/contract.pdf` — buyer's own contract PDF.
    - `GET  /api/portal/contracts/{id}/payment-schedule` — buyer's own payment schedule.
    - `GET  /api/portal/properties/{id}` — buyer's own property (must have a contract for it).
    - `GET  /api/portal/tenant-info` — tenant name + logo URL.
  - **ErrorCode**: `PORTAL_TOKEN_INVALID` (401).
  - **IT**: `PortalAuthIT` (5 tests: request link, verify valid token, verify used token, verify unknown token, portal JWT forbidden on CRM endpoint). `PortalContractsIT` (4 tests: buyer sees own contracts, buyer cannot see other buyer, CRM ADMIN → 403, tenant info). `PortalPaymentsIT` (4 tests: buyer sees own schedule, cross-buyer → 404, buyer sees own property, cross-buyer property → 404).
  - **Frontend routes** (`/portal/*` lazy-loaded):
    - `/portal/login` → `PortalLoginComponent` (public — accepts `?token=` param, calls verify, stores JWT).
    - `/portal` → `PortalShellComponent` (guarded by `portalGuard`):
      - `/portal/contracts` → `PortalContractsComponent` (default).
      - `/portal/contracts/:contractId/payments` → `PortalPaymentsComponent`.
      - `/portal/properties/:id` → `PortalPropertyComponent`.
  - **Frontend services/guard/interceptor** (all in `portal/core/`):
    - `PortalAuthService`: localStorage key `hlm_portal_token`; `requestLink()`, `verifyToken()`, `getTenantInfo()`, `logout()`.
    - `portalGuard`: redirects to `/portal/login` if no portal JWT.
    - `portalInterceptor`: attaches portal JWT to `/api/portal/` requests (excluding auth endpoints); auto-logout on 401. Registered in `app.config.ts` alongside `authInterceptor`.
  - **Config**: `app.portal.base-url` (default `http://localhost:4200`) — base URL used to build magic link. Override in production.

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
