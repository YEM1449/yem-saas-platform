# Implementation Status (What is done so far)

_Last updated: 2026-02-26 (PR-2 tightenings)_

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

- **Commercial Dashboard v1** (PR-3 — 2026-02-26): `GET /api/dashboard/commercial/summary` + `GET /api/dashboard/commercial/sales` (paginated drill-down). Single backend call per screen. Aggregate queries only (no entity hydration); 9 queries budget. RBAC: AGENT auto-scoped to own data; ADMIN/MANAGER full tenant. Caching: Caffeine 30 s TTL, key includes all filter params. Validation: `from > to` → 400, unknown `projectId`/`agentId` → 404. Summary DTO: sales totals, deposit totals, top-10 breakdowns by project/agent, inventory by status/type, daily trend series, conversion rate, avg cycle-time days. Angular dashboard at `/app/dashboard/commercial` with filter bar, KPI cards, SVG-free bar charts, top-10 tables, drill-down link. IT: `CommercialDashboardIT` (4 tests).

- **Double-booking protection hardening** (PR-2 — 2026-02-26): `DepositService.confirm()` now acquires a pessimistic write lock on the property row and rejects confirmation if the property is already SOLD (`InvalidDepositStateException` → 409). Closes race window: concurrent contract sign + deposit confirm. Tests: `ContractControllerIT` extended to 10 IT cases (added `cancelSignedContract_revertsPropertyToAvailable`, `confirmDepositOnSoldProperty_returns409`, `cancelSignedContract_withConfirmedDeposit_revertsPropertyToReserved`, `agentCannotSignContract_returns403`). Lock ordering enforced across all 4 flows (Property lock always acquired before Deposit/Contract rows). `DepositRepository.existsActiveConfirmedDepositForProperty()` added as canonical method for cancel-rule check. `SaleContractService.cancel()` restructured: property lock and deposit check occur before contract save.


## In progress / to verify

- **Immediate JWT invalidation on role change / disable**: required by security P1. Verify by checking JWT validation path + tests.

- **Project contains multiple property types**: confirm implementation choice (enum vs `property_type` table) and UI/API alignment.


## Known build/test issues to address

- Spring test ApplicationContext currently fails to load (causing many ITs to be skipped). Fix the first root-cause exception in Surefire reports.

- Update test fixtures to create `Tenant → Project → Property` consistently.

