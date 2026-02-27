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

## Commercial KPI Contract (locked definitions — do not change without updating this section)

These definitions are the single source of truth for all dashboards and analytics:

| KPI term | Definition |
|---|---|
| **Reservation** | A deposit created for a property (deposit status `PENDING` or `CONFIRMED`). Property status moves to `RESERVED`. |
| **Sale** | A `SaleContract` with `status = SIGNED`. Deposit / reservation is *pre-sale only*. |
| **Revenue / Sale amount** | `SaleContract.agreedPrice` (negotiated price at signing). `listPrice` is the optional original price for discount analytics. |
| **Cancellation — reservation** | Deposit `CANCELLED` or `EXPIRED` → property reverts to `ACTIVE` (available). |
| **Cancellation — sale** | Signed contract `CANCELED` → property reverts to `RESERVED` (if a CONFIRMED deposit still exists) or `ACTIVE` (no active deposit). |

### Buyer snapshot (P0-2 — Liquibase 018)
At the moment a `SaleContract` transitions to `SIGNED`, an immutable buyer snapshot is captured on the contract row:
- `buyerType` — `PERSON` (default; `COMPANY` reserved for future company-contact support)
- `buyerDisplayName` — `Contact.fullName` at signing time
- `buyerPhone`, `buyerEmail`, `buyerAddress`, `buyerIce` — Contact fields at signing time

The snapshot decouples legal/commercial records from future CRM Contact edits.
The FK `buyer_contact_id` is retained for cross-reference (drill-down, re-contact).

### Event semantics (used for analytics and future outbox alignment)
| Event | Trigger | Property transition |
|---|---|---|
| `PROPERTY_RESERVED` | Deposit created | `ACTIVE → RESERVED` |
| `RESERVATION_RELEASED` | Deposit CANCELLED / EXPIRED | `RESERVED → ACTIVE` |
| `CONTRACT_SIGNED` | `SaleContract.sign()` | `RESERVED or ACTIVE → SOLD` |
| `CONTRACT_CANCELED` (on SIGNED) | `SaleContract.cancel()` | `SOLD → RESERVED` or `SOLD → ACTIVE` |

## Implementation snapshot (as of 2026-02-27, PR-A)
- Implemented baseline modules: Tenant isolation, RBAC, Admin user management, Projects/Properties, Contacts/Prospects, Deposits/Reservations, Sales Contracts, Commercial Dashboard, Outbound Messaging (Email/SMS outbox).
- Key domain rules:
  - Property belongs to Project (mandatory).
  - Only ACTIVE projects accept new/reassigned properties — enforced via `ProjectActiveGuard` (wired into `SaleContractService` for both create and sign).
  - JWT revocation: `tv` (tokenVersion) claim; incremented on role change or disable; `JwtAuthenticationFilter` rejects mismatched tokens immediately.
  - **Sale = Contract SIGNED**: `SaleContract` lifecycle `DRAFT → SIGNED → CANCELED`; signing marks property `SOLD`; canceling a SIGNED contract reverts property to `RESERVED` or `ACTIVE`.
  - Double-selling prevented by service-layer check + DB partial unique index `uk_sc_property_signed`.
  - `PropertyCommercialWorkflowService` is the SSOT for all property commercial status transitions.
  - **Buyer snapshot**: Immutable buyer data captured at `SIGNED` time (`BuyerType`, `buyerDisplayName`, `buyerPhone`, `buyerEmail`, `buyerIce`, `buyerAddress`). Changeset 018.
- Project KPIs: `GET /api/projects/{id}/kpis` (ADMIN/MANAGER); Angular `project-detail` component renders KPI cards.
- Commercial Dashboard: `GET /api/dashboard/commercial/summary` + `/sales` (drill-down). Single-call, 9 aggregate queries, 30 s Caffeine cache. AGENT scope auto-enforced. Angular route `/app/dashboard/commercial`.
- Outbound Messaging: `POST /api/messages` (202, PENDING outbox row) + `GET /api/messages` (paged). `outbox/` package; Noop providers; SKIP LOCKED dispatch; exponential backoff. Angular route `/app/messages`.
- Tests hint: key IT classes include `RbacIT`, `AdminUserControllerIT`, `TenantControllerIT`, `CrossTenantIsolationIT`, `ContactServiceIT`, `PropertyControllerIT`, `ProjectControllerIT`, `TokenRevocationIT`, `DepositControllerIT`, `ContractControllerIT` (11 tests — includes buyer snapshot test), `CommercialDashboardIT`, `OutboxIT`.

## Local verification commands (suggested)
- Run a single failing IT:
  - `cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=ContractControllerIT`
- Run full backend unit tests:
  - `cd hlm-backend && ./mvnw test`
- Run all backend ITs (Docker required):
  - `cd hlm-backend && ./mvnw failsafe:integration-test`
- Frontend build check:
  - `cd hlm-frontend && npm run build`

## Living-spec helpers
- Progress tracking: `docs/spec/Backlog_Status.md`, `docs/spec/Implementation_Status.md`, `docs/spec/Gap_Analysis.md`.
