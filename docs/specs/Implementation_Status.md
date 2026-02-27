# Implementation Status (What is done so far)

_Last updated: 2026-02-27 (PR-6 outbound messaging)_

This is a living snapshot of what is implemented in the repo, based on current test suite structure, recent changes, and project decisions.


## Verified by existing test coverage (evidence)

- **Multi-tenant / tenant isolation**: presence of `TenantControllerIT`, `CrossTenantIsolationIT`.

- **RBAC / authorization rules**: presence of `RbacIT`.

- **Admin user management** (create users, role change, enable/disable): presence of `AdminUserControllerIT`.

- **Contacts / prospects services**: presence of `ContactServiceIT`.

- **Deposits / reservations**: presence of `CrossTenantIsolationIT` test cases (`createDeposit`, `confirmDeposit`, `getDeposit`, ...).


## Domain decisions already applied

- **Property must belong to a Project** (mandatory relationship). Evidence: `Property(Tenant, Project, PropertyType, UUID)` constructor signature.

- **Project KPIs (basic)**: implemented at least at a basic level (per project reporting). Endpoint: `GET /api/projects/{id}/kpis` (ADMIN/MANAGER only). Angular `project-detail` component renders KPI cards.

- **ARCHIVED project guardrail** (Batch 1 — 2026-02-26): Only `ACTIVE` projects may receive new or reassigned properties. Guard is centralised in `ProjectActiveGuard` (Spring @Service, `project/service/`). Both create and update paths delegate to it. `ArchivedProjectAssignmentException` → 400 `ARCHIVED_PROJECT`. Future `SaleContractService` must inject `ProjectActiveGuard` too.

- **Blank project name on update**: `ProjectUpdateRequest.name` annotated `@Size(min=1, max=200)`; compact constructor trims whitespace first. `{"name":"   "}` → 400 `VALIDATION_ERROR`.

- **Property commercial status — single source of truth** (Batch 2 — 2026-02-26): All property commercial status transitions route through `PropertyCommercialWorkflowService` (`property/service/`). Methods: `reserve(Property, LocalDateTime)`, `releaseReservation(Property)`, `sell(Property, LocalDateTime)`, `cancelSaleToAvailable(Property)`, `cancelSaleToReserved(Property)`. `DepositService` and `PropertyService.markAsReserved/markAsSold` delegate to this service. `Property.reservedAt` field added (Liquibase changeset 015). `PropertyResponse` exposes `reservedAt`. Tests: `PropertyCommercialWorkflowServiceTest` (unit) + `DepositControllerIT`.

- **Sales Contract MVP** (PR-1 — 2026-02-26): `SaleContract` entity + `SaleContractStatus` (DRAFT/SIGNED/CANCELED). Liquibase changeset 016 (`sale_contract` table + FK constraints + KPI indexes + partial unique index `uk_sc_property_signed`). API: `POST /api/contracts` (all roles), `POST /api/contracts/{id}/sign` (ADMIN/MANAGER → property SOLD), `POST /api/contracts/{id}/cancel` (ADMIN/MANAGER → property reverts), `GET /api/contracts` (all roles; AGENT sees own only). Double-selling guard: service-layer check + DB partial unique index. AGENT restriction enforced in service layer.

- **Commercial Dashboard v1 + PR-5 enhancements** (PR-3/PR-5 — 2026-02-27): `GET /api/dashboard/commercial` (alias, YYYY-MM-DD dates) + `GET /api/dashboard/commercial/summary` (canonical, ISO datetime) + `GET /api/dashboard/commercial/sales` (paginated drill-down). Single backend call per screen. Aggregate queries only (no entity hydration); up to 10 queries budget. RBAC: AGENT auto-scoped to own data; ADMIN/MANAGER full tenant. Caching: Caffeine 30 s TTL. PR-5 additions to DTO: `asOf` (freshness timestamp), `activeReservationsCount` + `activeReservationsTotalAmount` + `avgReservationAgeDays` (current open reservations snapshot, not date-filtered). Angular dashboard with filter bar, KPI cards (10 cards including 3 new orange reservation KPIs), bar charts, top-10 tables, drill-down. IT: `CommercialDashboardIT` (4 tests).

- **Reservation PDF certificate** (PR-5 — 2026-02-27): `GET /api/deposits/{id}/documents/reservation.pdf` returns an "Attestation de Réservation" PDF. Architecture: `ReservationDocumentService` (RBAC + model builder) → `DocumentGenerationService` (Thymeleaf HTML → OpenHTMLToPDF → PDF bytes). RBAC: ADMIN/MANAGER → any deposit in tenant; AGENT → own deposits only (cross-ownership → 404). Customisable template: `hlm-backend/src/main/resources/templates/documents/reservation.html` (Thymeleaf; edit for branding). Data model: `ReservationDocumentModel` record in `deposit/service/pdf/`. N+1 avoidance: `DepositRepository.findForPdf()` JOIN FETCH. Dependencies: `com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10` + `spring-boot-starter-thymeleaf`; PDFBox 3.x removed. IT: `ReservationPdfIT` (8 tests: 200+`%PDF`+Content-Disposition, 404 non-existent, 404 cross-tenant, 401, AGENT own 200, AGENT other-agent 404).

- **Outbound Messaging / Outbox** (PR-6 — 2026-02-27, REQ-2-3-MODULE-COMMERC-021 DONE): Async EMAIL/SMS dispatch from CRM. `POST /api/messages` (202 Accepted, inserts PENDING row only) + `GET /api/messages` (paged, tenant-scoped; filters: channel, status, contactId, from, to). Architecture: `MessageComposeService` validates + inserts outbox row → `OutboundDispatcherService` polls via SKIP LOCKED batch (`fetchPendingBatch` native query) + dispatches via `EmailSender`/`SmsSender` provider interfaces (Noop defaults log; swap for real SMTP/Twilio). Exponential backoff: 1 min → 5 min → 30 min; permanently FAILED after `maxRetries`. Liquibase: changeset 017 (`outbound_message` table with status, retries, next_retry_at, correlation fields). Config: `app.outbox.{batch-size,max-retries,polling-interval-ms}` (env-var overridable: `OUTBOX_BATCH_SIZE`, `OUTBOX_MAX_RETRIES`, `OUTBOX_POLL_INTERVAL_MS`). RBAC: all roles (ADMIN/MANAGER/AGENT) may send and list. IT: `OutboxIT` (9 tests). Frontend: `features/outbox/` at `/app/messages`.

- **Double-booking protection hardening** (PR-2 — 2026-02-26): `DepositService.confirm()` now acquires a pessimistic write lock on the property row and rejects confirmation if the property is already SOLD (`InvalidDepositStateException` → 409). Closes race window: concurrent contract sign + deposit confirm. Tests: `ContractControllerIT` extended to 10 IT cases (added `cancelSignedContract_revertsPropertyToAvailable`, `confirmDepositOnSoldProperty_returns409`, `cancelSignedContract_withConfirmedDeposit_revertsPropertyToReserved`, `agentCannotSignContract_returns403`). Lock ordering enforced across all 4 flows (Property lock always acquired before Deposit/Contract rows). `DepositRepository.existsActiveConfirmedDepositForProperty()` added as canonical method for cancel-rule check. `SaleContractService.cancel()` restructured: property lock and deposit check occur before contract save.


## In progress / to verify

- **Immediate JWT invalidation on role change / disable**: required by security P1. Verify by checking JWT validation path + tests.

- **Project contains multiple property types**: confirm implementation choice (enum vs `property_type` table) and UI/API alignment.


## Known build/test issues to address

- ~~Spring test ApplicationContext currently fails to load (causing many ITs to be skipped).~~ **FIXED (2026-02-26)**: Root cause was a Liquibase table name collision — legacy CRM `project` table (changeset 003) clashed with new real-estate `project` table (changeset 013). Fixed by inserting changeset `012b-rename-legacy-project-table.yaml` which drops the conflicting FK, renames the PK constraint, and renames the table to `crm_project_legacy` before changeset 013 runs.

- Update test fixtures to create `Tenant → Project → Property` consistently.

