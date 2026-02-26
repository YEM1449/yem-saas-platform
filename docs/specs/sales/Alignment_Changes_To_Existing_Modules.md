# Alignment Changes for Sales Module (Real Estate CRM)
Date: 2026-02-26

This document lists what should be **adapted in existing modules** to align with a professional **Sales (Contract) module** and KPI-ready dashboards.

> Principle: **Dashboards are derived artifacts**. First ensure the sales lifecycle is correctly modeled, auditable, and produces reliable events/timestamps; then build KPIs and dashboards.

## 1) Existing Modules to Adapt

### 1.1 Project module
**Current**: Projects have `ProjectStatus` including `ARCHIVED` (closed). Property assignment currently checks tenant/id; needs ACTIVE check.

**Required adaptations**
- Enforce: **ARCHIVED projects cannot accept new properties** *(P1 already identified)*.
- Enforce: **ARCHIVED projects cannot accept new sales contracts** (recommended) — a closed project should not keep generating new contracts.
- Ensure project-scoped aggregates exclude archived projects by default in “active portfolio” views, but allow explicit “include archived” filter for reporting.

**Data additions (optional)**
- `project.launchDate`, `project.targetDeliveryDate` (helps pipeline planning)
- `project.salesStartDate` (for commercial calendars)

### 1.2 Property module
**Current**: Property belongs to a Project (mandatory FK). Property statuses likely include AVAILABLE/RESERVED/SOLD (or similar).

**Required adaptations**
- Define and enforce a **single source of truth** for property commercial status transitions:
  - `AVAILABLE -> RESERVED` (when a valid deposit/reservation is confirmed)
  - `RESERVED -> SOLD` (when contract is signed)
  - `RESERVED -> AVAILABLE` (reservation canceled/expired)
  - `SOLD` is terminal (unless business supports rescission)
- Add **uniqueness guard**: A property must not have >1 active reservation/contract at the same time.
- Add timestamps for KPI readiness (if not present):
  - `reservedAt`, `soldAt` (or derive from reservation/contract dates)
- Concurrency: prevent double booking (DB constraint + transaction locking).

### 1.3 Contacts / CRM pipeline module
**Current**: Contact types include PROSPECT / TEMP_CLIENT etc (based on earlier issues).

**Required adaptations**
- Introduce a stable concept of **Buyer** for contracts:
  - Contract references a `Contact` (buyer) and optionally a **snapshot** of buyer identity fields at signature time.
- Keep prospect lifecycle separate from contract lifecycle:
  - Prospect stages (lead/qualified/visit/negotiation) should not be “overwritten” by the deposit workflow without traceability.
- Add/confirm timestamp fields:
  - `prospectCreatedAt`, `qualifiedAt` (if qualification exists), `lastActivityAt` (if activities exist).

### 1.4 Deposit / Reservation module
**Current**: Deposits exist and are used to reserve; there is a confirm flow, cross-tenant tests etc.

**Required adaptations**
- Ensure deposit has explicit **status** + timestamps:
  - `CREATED`, `CONFIRMED`, `CANCELED`, `EXPIRED` (at minimum)
  - `createdAt`, `confirmedAt`, `canceledAt`
- Clarify “reservation validity”: how long a deposit reserves a property (SLA). Add `expiresAt` if needed.
- Add strict linkage:
  - Deposit belongs to `tenantId`, `projectId`, `propertyId`, `contactId` (buyer), `agentId`.
- Add conversion path:
  - Create `SaleContract` **from a confirmed deposit** (recommended) OR allow direct contract with optional deposit reference.

### 1.5 Sales module (new)
**New capability**: Sales contracts (what the business calls “sale”) must be modeled.

**Key definition (recommended)**
- **Sale = Contract Signed** (not just deposit). Deposits can be canceled; signed contract is the KPI anchor.

### 1.6 Users / RBAC
**Required adaptations**
- RBAC for contracts:
  - ADMIN/MANAGER can view all within tenant
  - AGENT can view/create within tenant; limited to own assignments unless explicitly allowed.
- Audit log entries for:
  - contract signed/canceled
  - role changes affecting visibility

### 1.7 KPI / Analytics layer
**Required adaptations**
- Create a “commercial analytics” query layer that returns DTO projections (no entity hydration).
- Use short TTL caching initially (30–60s), then evolve to pre-aggregated daily tables for scale.

## 2) Minimal Sales MVP Definition (what to implement first)
- Entity: `SaleContract`
- Lifecycle: DRAFT -> SIGNED -> (optional) CANCELED; optionally COMPLETED
- Create contract from confirmed deposit
- Update property status on SIGNED / CANCELED
- KPI-ready fields:
  - `signedAt`, `canceledAt`, `agreedPrice`, `listPrice`, `discount`
  - `agentId`, `projectId`, `propertyId`, `contactId`
- Integrity:
  - One active signed contract per property (tenant-scoped unique constraint)

## 3) Open Points to clarify (keep as [OPEN POINT] until confirmed in code)
- Do we store **listPrice** and **agreedPrice** today?
- Do we track **payments** or only deposit?
- Do we track pipeline stages beyond deposit?
- Do we have “building/immeuble” dimension already?
