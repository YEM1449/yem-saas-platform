# Project Context (for ClaudeCode / GPT)

## Source of truth
- Primary: `docs/spec/CDC_Source.md`
- Structured: `docs/spec/Requirements_Index.md`, `docs/spec/Backlog_Priorities.md`

## Product scope (high level)
- CRM SaaS for real-estate promotion/construction lifecycle: land prospecting → studies/authorizations → sales → construction → procurement/logistics → finance → after-sales.
- Multi-company + multi-project with consolidated reporting.

## Core modules (CDC)
- Multi-societies & multi-projects
- Prospection foncière
- Commercial (prospects/contacts, lots catalog, sales pipeline, document generation, SMS/email)
- Construction tracking (Gantt, phases, site journal, quality/safety)
- Site stock management (QR/NFC, transfers, alerts, inventories)
- Purchases & suppliers (DA/BC/BL/Invoice matching)
- Administrative workflow (Morocco + EU)
- Finance & controlling (budget vs actual, margins, accounting export, cash-in)
- After-sales (SAV tickets)

## Non-functional
- Cloud SaaS, security/confidentiality, integrations (ERP/accounting/website), RGPD compliance (Morocco + EU).

## Working rules for coding assistants
- Do not invent features beyond CDC.
- If CDC is unclear: add `[OPEN POINT]` with 1–3 options + recommended choice.
- Update docs when behavior/contracts change.

## Implementation snapshot (as of 2026-02-26, PR-3)
- Implemented baseline modules: Tenant isolation, RBAC, Admin user management, Projects/Properties, Contacts/Prospects, Deposits/Reservations, Sales Contracts, Commercial Dashboard.
- Key domain rules:
  - Property belongs to Project (mandatory).
  - Only ACTIVE projects accept new/reassigned properties — enforced via `ProjectActiveGuard` (wired into `SaleContractService` for both create and sign).
  - JWT revocation: `tv` (tokenVersion) claim; incremented on role change or disable; `JwtAuthenticationFilter` rejects mismatched tokens immediately.
  - **Sale = Contract SIGNED**: `SaleContract` lifecycle `DRAFT → SIGNED → CANCELED`; signing marks property `SOLD`; canceling a SIGNED contract reverts property to `RESERVED` or `ACTIVE`.
  - Double-selling prevented by service-layer check + DB partial unique index `uk_sc_property_signed`.
  - `PropertyCommercialWorkflowService` is the SSOT for all property commercial status transitions.
- Project KPIs: `GET /api/projects/{id}/kpis` (ADMIN/MANAGER); Angular `project-detail` component renders KPI cards.
- Commercial Dashboard: `GET /api/dashboard/commercial/summary` + `/sales` (drill-down). Single-call, 9 aggregate queries, 30 s Caffeine cache. AGENT scope auto-enforced. Angular route `/app/dashboard/commercial`.
- Tests hint: key IT classes include `RbacIT`, `AdminUserControllerIT`, `TenantControllerIT`, `CrossTenantIsolationIT`, `ContactServiceIT`, `PropertyControllerIT`, `ProjectControllerIT`, `TokenRevocationIT`, `DepositControllerIT`, `ContractControllerIT`, `CommercialDashboardIT`.

## Local verification commands (suggested)
- Run a single failing IT with full stack:
  - `mvn -pl hlm-backend -Dtest=RbacIT -DtrimStackTrace=false -e test`
- Run full backend tests:
  - `mvn -pl hlm-backend -am test`
- Run dashboard ITs only:
  - `cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=CommercialDashboardIT`
- Run all backend ITs (Docker required):
  - `cd hlm-backend && ./mvnw failsafe:integration-test`
- Frontend build check after dashboard changes:
  - `cd hlm-frontend && npm run build`

## Living-spec helpers
- Progress tracking: `docs/spec/Backlog_Status.md`, `docs/spec/Implementation_Status.md`, `docs/spec/Gap_Analysis.md`.
