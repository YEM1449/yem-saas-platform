# Implementation Status (What is done so far)

_Last updated: 2026-02-26 (Batch 2)_

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

- **Property commercial status — single source of truth** (Batch 2 — 2026-02-26): All property commercial status transitions route through `PropertyCommercialWorkflowService` (`property/service/`). Methods: `reserve(Property, LocalDateTime)`, `releaseReservation(Property)`, `sell(Property, LocalDateTime)`. `DepositService` and `PropertyService.markAsReserved/markAsSold` delegate to this service. `Property.reservedAt` field added (Liquibase changeset 015). `PropertyResponse` exposes `reservedAt`. Tests: `PropertyCommercialWorkflowServiceTest` (unit, 6 cases) + `DepositControllerIT` (`firstDeposit_stampsReservedAtOnProperty`, `cancelDeposit_clearsReservedAtOnProperty`).


## In progress / to verify

- **Immediate JWT invalidation on role change / disable**: required by security P1. Verify by checking JWT validation path + tests.

- **Project contains multiple property types**: confirm implementation choice (enum vs `property_type` table) and UI/API alignment.


## Known build/test issues to address

- Spring test ApplicationContext currently fails to load (causing many ITs to be skipped). Fix the first root-cause exception in Surefire reports.

- Update test fixtures to create `Tenant → Project → Property` consistently.

