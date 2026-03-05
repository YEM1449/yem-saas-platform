# Functional Specification — CRM-HLM Platform

| Field | Value |
|---|---|
| **Version** | 1.1 |
| **Date** | 2026-03-04 |
| **Scope** | Multi-tenant CRM for real-estate promotion & construction |
| **Client** | HLM's Corp |
| **Status** | Updated — Phase 3 (Commercial Intelligence) + Phase 4 (Client Portal) added |
| **Classification** | Confidential |

---

## 1. Executive Summary

CRM-HLM is a multi-tenant SaaS platform designed for real-estate promotion and construction companies in Morocco and the EU. The system covers the commercial lifecycle of property development: project management, property catalog, prospect/contact pipeline, deposit/reservation handling, sales contract execution, PDF document generation (reservation certificates, contracts), outbound messaging (email/SMS), commercial dashboards, and audit trails. The platform enforces strict tenant isolation via JWT claims, role-based access control (Admin/Manager/Agent), and PostgreSQL row-level scoping. As of 2026-02-28, the implemented modules focus on the **commercial MVP** (P1 priorities): user/role management, multi-tenant projects & properties, contacts/prospects, deposits/reservations, sales contracts, commercial KPI dashboards, PDF generation, outbound messaging, and commercial audit. Modules such as land prospecting, construction tracking, stock management, purchases, administrative workflow, finance, and after-sales are planned in the CDC but **not yet implemented**.

---

## 2. Business Scope

### 2.1 In-Scope (Implemented)

| # | Module | Status |
|---|---|---|
| 1 | Authentication & Security (JWT, RBAC, tenant isolation, token revocation) | ✅ Implemented |
| 2 | User & Role Management (create, change role, enable/disable, password reset) | ✅ Implemented |
| 3 | Multi-Tenant / Multi-Project Management | ✅ Implemented |
| 4 | Property Catalog (9 types, 6 statuses, CRUD, dashboards) | ✅ Implemented |
| 5 | Contacts & Prospects (pipeline, interests, client conversion) | ✅ Implemented |
| 6 | Deposits / Reservations (lifecycle, auto-expiry, PDF certificate) | ✅ Implemented |
| 7 | Sales Contracts (DRAFT→SIGNED→CANCELED, buyer snapshot, PDF) | ✅ Implemented |
| 8 | Commercial Dashboard (KPI summary, drill-down, caching) | ✅ Implemented |
| 9 | Outbound Messaging (Email/SMS outbox, async dispatch) | ✅ Implemented |
| 10 | Commercial Audit Trail (immutable event log) | ✅ Implemented |
| 11 | In-App Notifications (deposit lifecycle) | ✅ Implemented |
| 12 | Commission Rules (project-specific + tenant default, formula-based) | ✅ Implemented (Phase 3) |
| 13 | Receivables Dashboard (aging buckets: current/30/60/90+ days) | ✅ Implemented (Phase 3) |
| 14 | Discount Analytics (avg discount %, max discount %, by agent) | ✅ Implemented (Phase 3) |
| 15 | Prospect Source Funnel (by source: organic, referral, ad, etc.) | ✅ Implemented (Phase 3) |
| 16 | Client-Facing Portal (magic link auth, ROLE_PORTAL, contract view) | ✅ Implemented (Phase 4) |

### 2.2 Out-of-Scope (CDC Planned, Not Yet Implemented)

| # | Module | CDC Ref | Priority |
|---|---|---|---|
| 1 | Prospection Foncière (land database, COS/CES, acquisition pipeline) | REQ-2-2-* | P1 |
| 2 | Workflow Administratif (authorizations, document archiving, alerts) | REQ-2-7-* | P1 |
| 3 | Suivi Construction (Gantt, phases, site journal, quality/safety) | REQ-2-4-* | P2 |
| 4 | Gestion des Stocks Chantier (QR/NFC, transfers, inventory) | REQ-2-5-* | P2 |
| 5 | Achats & Logistique (purchase requests, PO, invoice matching) | REQ-2-6-* | P2 |
| 6 | Module Finance (budget vs actual, margins, accounting export, cash-in) | REQ-2-8-* | P3 |
| 7 | Qualité & Sécurité Chantier (checklists, compliance) | REQ-4-3-080..082 | P3 |
| 8 | Module Sous-traitants (legal docs, rating) | REQ-4-3-083..084 | P3 |
| 9 | SAV & Tickets (client declarations, intervention tracking) | REQ-4-3-085..086 | P3 |
| 10 | Intégrations Externes (accounting, website, partner tools) | REQ-4-3-087..089 | P3 |
| 11 | Appels de Fonds (payment schedules / call-for-funds PDF) | REQ-2-3-020 | P1 (partial) |

---

## 3. Personas, Roles & Permissions

### 3.1 Personas

| Persona | Role | Description |
|---|---|---|
| **Administrateur** | `ROLE_ADMIN` | System administrator for a tenant. Full CRUD access, user management, deletion, project archiving, KPI access. |
| **Manager Commercial** | `ROLE_MANAGER` | Sales manager overseeing commercial activity. Can create/update resources, access KPIs, sign/cancel contracts. Cannot delete resources or manage users. |
| **Agent Commercial** | `ROLE_AGENT` | Sales agent handling day-to-day prospect interactions. Read access to properties/contacts, can create deposits/contracts (DRAFT), view own data. Default role for new users. |

### 3.2 Permission Matrix (Business View)

| Action | ADMIN | MANAGER | AGENT |
|---|---|---|---|
| **Users**: Create / Change Role / Enable-Disable / Reset Password | ✅ | ❌ | ❌ |
| **Projects**: Create / Update | ✅ | ✅ | ❌ |
| **Projects**: Archive (soft delete) | ✅ | ❌ | ❌ |
| **Projects**: View / List | ✅ | ✅ | ✅ |
| **Project KPIs**: View | ✅ | ✅ | ❌ |
| **Properties**: Create / Update | ✅ | ✅ | ❌ |
| **Properties**: Delete (soft) | ✅ | ❌ | ❌ |
| **Properties**: View / List | ✅ | ✅ | ✅ |
| **Contacts**: Create / Update / Status Change / Convert to Client | ✅ | ✅ | ❌ |
| **Contacts**: View / List | ✅ | ✅ | ✅ |
| **Contact Interests**: Add | ✅ | ✅ | ❌ |
| **Contact Interests**: Remove | ✅ | ❌ | ❌ |
| **Deposits**: Create / Confirm / Cancel | ✅ | ✅ | ❌ |
| **Deposits**: View (all tenant) | ✅ | ✅ | — |
| **Deposits**: View (own only) | — | — | ✅ |
| **Deposit Report** | ✅ | ✅ | ❌ |
| **Reservation PDF**: Download (all tenant) | ✅ | ✅ | — |
| **Reservation PDF**: Download (own only) | — | — | ✅ |
| **Contracts**: Create (DRAFT) | ✅ | ✅ | ✅ |
| **Contracts**: Sign / Cancel | ✅ | ✅ | ❌ |
| **Contracts**: List (all tenant) | ✅ | ✅ | — |
| **Contracts**: List (own only) | — | — | ✅ |
| **Contract PDF**: Download (all tenant) | ✅ | ✅ | — |
| **Contract PDF**: Download (own only) | — | — | ✅ |
| **Commercial Dashboard**: View (full tenant) | ✅ | ✅ | — |
| **Commercial Dashboard**: View (own data) | — | — | ✅ (auto-scoped) |
| **Dashboard Drill-down (sales)** | ✅ | ✅ | ✅ (auto-scoped) |
| **Messages**: Send (Email/SMS) | ✅ | ✅ | ✅ |
| **Messages**: List | ✅ | ✅ | ✅ |
| **Commercial Audit**: View | ✅ | ✅ | ❌ |
| **Notifications**: View / Mark Read | ✅ | ✅ | ✅ |
| **Property Dashboard (legacy)** | ✅ | ✅ | ❌ |
| **Commission Rules**: Create / Update / Delete | ✅ | ✅ | ❌ |
| **Commission Rules**: View / List | ✅ | ✅ | ✅ |
| **Receivables Dashboard**: View | ✅ | ✅ | ❌ |
| **Discount Analytics**: View | ✅ | ✅ | ❌ |
| **Prospect Source Funnel**: View | ✅ | ✅ | ❌ |
| **Portal**: Authenticate via magic link | ✅ (portal client) | ✅ (portal client) | ✅ (portal client) |
| **Portal**: View own contracts | ✅ (portal client) | ✅ (portal client) | ✅ (portal client) |

---

## 4. Module-by-Module Requirements

### 4.1 Authentication & Security

**Purpose**: Authenticate users within a tenant context and enforce RBAC across all endpoints.

**User Stories**:
- US-AUTH-01: As any user, I can log in with my tenant key, email, and password to receive a JWT token.
- US-AUTH-02: As a logged-in user, I can verify my identity and tenant context via `/auth/me`.
- US-AUTH-03: As an admin, when I change a user's role or disable their account, their existing sessions are immediately invalidated.

**Workflow**:
1. User sends `POST /auth/login` with `{tenantKey, email, password}`.
2. System validates credentials against the tenant's user database.
3. On success, system returns a JWT containing claims: `sub` (userId), `tid` (tenantId), `roles`, `tv` (tokenVersion).
4. Every subsequent request includes `Authorization: Bearer <JWT>`.
5. `JwtAuthenticationFilter` validates the token, checks `tv` against the DB (via cache), sets `TenantContext`, and populates Spring Security context.

**Validation Rules**:
- `tenantKey`: required, non-blank.
- `email`: required, valid format.
- `password`: required, non-blank.
- JWT secret must be ≥ 32 characters; app fails fast if missing.

**Token Revocation (State Machine)**:
- `User.tokenVersion` is an integer incremented on: role change, account disable.
- On each request, filter compares JWT `tv` claim with DB `tokenVersion`; mismatch → 401.
- Cache (`userSecurityCache`) avoids per-request DB hits; evicted on role/disable changes.

**Error Cases**:
- Invalid credentials → 401 `UNAUTHORIZED`
- Missing/expired/malformed JWT → 401 `UNAUTHORIZED`
- Insufficient role → 403 `FORBIDDEN`
- Token version mismatch (revoked) → 401 `UNAUTHORIZED`

---

### 4.2 User & Role Management

**Purpose**: Admin-only module to manage tenant users.

**User Stories**:
- US-USER-01: As an Admin, I can list all users in my tenant.
- US-USER-02: As an Admin, I can create a new user with a specified role.
- US-USER-03: As an Admin, I can change a user's role (which immediately invalidates their JWT).
- US-USER-04: As an Admin, I can enable or disable a user (which immediately invalidates their JWT if disabled).
- US-USER-05: As an Admin, I can reset a user's password.

**RBAC**: All endpoints require `ROLE_ADMIN`.

**Validation Rules**:
- Email must be unique within the tenant.
- Role must be one of: ROLE_ADMIN, ROLE_MANAGER, ROLE_AGENT.

**Error Cases**:
- Duplicate email → 409 `USER_EMAIL_EXISTS`
- User not found → 404 `NOT_FOUND`
- Non-admin access → 403 `FORBIDDEN`

---

### 4.3 Multi-Tenant / Multi-Project Management

**Purpose**: Organize properties and commercial activities within isolated tenants and projects.

#### 4.3.1 Tenants

**User Stories**:
- US-TENANT-01: A new tenant (company) can be bootstrapped via `POST /tenants`, which creates the tenant and an owner user.

**Validation**: `tenantKey` must be unique → 409 `TENANT_KEY_EXISTS`.

#### 4.3.2 Projects

**User Stories**:
- US-PROJ-01: As Admin/Manager, I can create a project within my tenant.
- US-PROJ-02: As Admin/Manager, I can update a project's name and details.
- US-PROJ-03: As Admin, I can archive a project (soft delete → status `ARCHIVED`).
- US-PROJ-04: As Admin/Manager, I can view project-level KPIs.
- US-PROJ-05: As any user, I can list and view projects in my tenant.

**Status Machine**: `ACTIVE` ↔ `ARCHIVED` (via `DELETE /api/projects/{id}`, which sets status to ARCHIVED).

**Business Rules**:
- Only `ACTIVE` projects may receive new or reassigned properties (enforced by `ProjectActiveGuard`).
- Archiving a project does not delete its properties; it prevents new assignments.
- Project names must be unique within tenant → 409 `PROJECT_NAME_EXISTS`.
- Blank project name on update → 400 `VALIDATION_ERROR` (whitespace trimmed).

**KPIs** (`GET /api/projects/{id}/kpis`): Total properties, by status, deposit count, sales count. RBAC: ADMIN/MANAGER.

---

### 4.4 Property Catalog

**Purpose**: Manage the inventory of real-estate properties (lots/units) within projects.

**User Stories**:
- US-PROP-01: As Admin/Manager, I can create a property in a project, specifying type, price, surface, and other characteristics.
- US-PROP-02: As Admin/Manager, I can update property details.
- US-PROP-03: As Admin, I can soft-delete a property.
- US-PROP-04: As any user, I can list properties with filters (type, status).
- US-PROP-05: As any user, I can view a single property's details.

**Property Types** (9): VILLA, APPARTEMENT, DUPLEX, STUDIO, T2, T3, COMMERCE, LOT, TERRAIN_VIERGE.

**Property Categories**: VILLA, APARTMENT, COMMERCE, LAND (derived from type).

**Status Machine**:
```
DRAFT → ACTIVE → RESERVED → SOLD
                ↘ WITHDRAWN
                ↘ ARCHIVED
```
- `ACTIVE → RESERVED`: Triggered by deposit creation.
- `RESERVED → ACTIVE`: Triggered by deposit cancellation/expiry or sale cancellation (no active deposit).
- `RESERVED → SOLD`: Triggered by contract signing.
- `SOLD → RESERVED`: Triggered by sale cancellation (when a confirmed deposit exists).
- `SOLD → ACTIVE`: Triggered by sale cancellation (no active deposit).

All commercial transitions route through `PropertyCommercialWorkflowService` (single source of truth).

**Validation Rules**:
- `referenceCode` unique per tenant → 409 `PROPERTY_REFERENCE_CODE_EXISTS`.
- Type-specific field requirements (e.g., VILLA requires `surface_area`, `land_area`, `bedrooms`, `bathrooms`).
- Project must exist and be `ACTIVE` → 400 `ARCHIVED_PROJECT`.

**Outputs**:
- Property Dashboard Summary: `GET /dashboard/properties/summary` (ADMIN/MANAGER) — aggregated stats by status/period.
- Property Sales KPIs: `GET /dashboard/properties/sales-kpi` (ADMIN/MANAGER).

---

### 4.5 Contacts & Prospects

**Purpose**: Manage the CRM pipeline from prospect to client.

**User Stories**:
- US-CONTACT-01: As Admin/Manager, I can create a contact (prospect).
- US-CONTACT-02: As Admin/Manager, I can update contact details.
- US-CONTACT-03: As Admin/Manager, I can transition a contact's status through the pipeline.
- US-CONTACT-04: As Admin/Manager, I can convert a qualified prospect to a client.
- US-CONTACT-05: As Admin/Manager, I can register a contact's interest in a specific property.
- US-CONTACT-06: As Admin, I can remove a contact interest.
- US-CONTACT-07: As any user, I can search and list contacts (paginated, filterable by status/text).
- US-CONTACT-08: As any user, I can view a contact's details and their interests.

**Contact Status State Machine**:
```
PROSPECT → QUALIFIED_PROSPECT | LOST
QUALIFIED_PROSPECT → PROSPECT | CLIENT | LOST
CLIENT → ACTIVE_CLIENT | COMPLETED_CLIENT | LOST
ACTIVE_CLIENT → COMPLETED_CLIENT | LOST
COMPLETED_CLIENT → REFERRAL
LOST → PROSPECT (re-activation)
```

**Specialized Details**:
- `ProspectDetail`: budget, source, notes.
- `ClientDetail`: client type (kind), company, ICE, SIRET.

**Validation Rules**:
- Email must be unique within tenant → 409 `CONTACT_EMAIL_EXISTS`.
- Invalid status transition → 409 `INVALID_STATUS_TRANSITION`.
- Interest already registered → 409 `CONTACT_INTEREST_EXISTS`.
- Convert to client requires `QUALIFIED_PROSPECT` or `CLIENT` status → 400 `INVALID_CLIENT_CONVERSION`.

---

### 4.6 Deposits / Reservations

**Purpose**: Manage the reservation process via deposits, linking a contact to a property with a financial commitment.

**User Stories**:
- US-DEP-01: As Admin/Manager, I can create a deposit for a contact on an ACTIVE property (which reserves it).
- US-DEP-02: As Admin/Manager, I can confirm a pending deposit.
- US-DEP-03: As Admin/Manager, I can cancel a deposit (which releases the property).
- US-DEP-04: The system automatically expires deposits past their due date (hourly scheduler).
- US-DEP-05: As any user, I can view deposit details (AGENT: own only).
- US-DEP-06: As Admin/Manager, I can view a deposit report filtered by status/agent/contact/property/period.
- US-DEP-07: As any authorized user, I can download a Reservation PDF certificate.

**Deposit Status Machine**:
```
PENDING → CONFIRMED
PENDING → CANCELLED
PENDING → EXPIRED (auto, scheduler)
CONFIRMED → CANCELLED
```

**Workflow (step-by-step)**:
1. Create deposit → status `PENDING`, property moves `ACTIVE → RESERVED`, `reservedAt` stamped.
2. Confirm deposit → status `CONFIRMED` (requires pessimistic lock on property; rejects if property is `SOLD` → 409).
3. Cancel deposit → status `CANCELLED`, property reverts to `ACTIVE` (if no other constraints).
4. Hourly scheduler scans expired deposits → status `EXPIRED`, property reverts to `ACTIVE`.

**Anti-Double-Reservation**: Unique constraint on (tenant, contact, property) + property status check.

**Reservation PDF** (`GET /api/deposits/{id}/documents/reservation.pdf`):
- Generates an "Attestation de Réservation" via Thymeleaf → OpenHTMLToPDF.
- RBAC: ADMIN/MANAGER → any deposit; AGENT → own deposits only (cross-ownership → 404).
- Content-Disposition: `attachment; filename="reservation_<id>.pdf"`.

**Error Cases**:
- Property not ACTIVE → 409 `PROPERTY_ALREADY_RESERVED`
- Deposit already exists for contact+property → 409 `DEPOSIT_ALREADY_EXISTS`
- Invalid state transition → 409 `INVALID_DEPOSIT_STATE`
- Confirm on SOLD property → 409 `INVALID_DEPOSIT_STATE`

---

### 4.7 Sales Contracts

**Purpose**: Formalize the sale of a property through a contract lifecycle.

**User Stories**:
- US-SC-01: As any user, I can create a draft sales contract linking a property, buyer contact, and agent.
- US-SC-02: As Admin/Manager, I can sign a contract (moves property to SOLD, captures buyer snapshot).
- US-SC-03: As Admin/Manager, I can cancel a contract (reverts property status appropriately).
- US-SC-04: As any user, I can list contracts (AGENT: own only).
- US-SC-05: As authorized user, I can download a Contract PDF.

**Contract Status Machine**:
```
DRAFT → SIGNED
DRAFT → CANCELED
SIGNED → CANCELED (business rescission)
CANCELED = terminal
```

**Workflow**:
1. Create contract (`POST /api/contracts`) → status `DRAFT`. Project must be ACTIVE.
2. Sign contract (`POST /api/contracts/{id}/sign`) → status `SIGNED`, property → `SOLD`, buyer snapshot captured.
3. Cancel SIGNED contract → property reverts to `RESERVED` (if confirmed deposit exists) or `ACTIVE`.

**Double-Selling Prevention**:
- Service-layer guard checks for existing SIGNED contract on property.
- DB partial unique index `uk_sc_property_signed` as safety net.
- Pessimistic lock on property row during sign/cancel.

**Buyer Snapshot** (captured at sign time):
- `buyerType` (PERSON/COMPANY), `buyerDisplayName`, `buyerPhone`, `buyerEmail`, `buyerIce`, `buyerAddress`.
- Immutable — decouples legal record from future Contact edits.

**Contract PDF** (`GET /api/contracts/{id}/documents/contract.pdf`):
- Bilingual contract document via Thymeleaf → OpenHTMLToPDF.
- 5 sections: property, prices, buyer, agent, signatures.
- RBAC: same as reservation PDF pattern.
- Buyer data: prefers snapshot fields (SIGNED contracts), falls back to live Contact (DRAFT).

**Error Cases**:
- Property already sold → 409 `PROPERTY_ALREADY_SOLD`
- Invalid state transition → 409 `INVALID_CONTRACT_STATE`
- Source deposit mismatch → 400 `CONTRACT_DEPOSIT_MISMATCH`
- Archived project → 400 `ARCHIVED_PROJECT`

---

### 4.8 Commercial Dashboard

**Purpose**: Provide real-time commercial KPIs for decision-making.

**User Stories**:
- US-DASH-01: As Admin/Manager, I can view a consolidated commercial summary for my tenant.
- US-DASH-02: As Agent, I can view my own commercial summary (auto-scoped).
- US-DASH-03: As any user, I can filter the dashboard by date range, project, and agent.
- US-DASH-04: As any user, I can drill down into individual sales transactions.

**KPI Definitions** (locked):

| KPI | Formula / Definition |
|---|---|
| Sales Count | Count of `SaleContract` with `status = SIGNED` in period |
| Sales Total Amount | Sum of `SaleContract.agreedPrice` where `status = SIGNED` in period |
| Avg Sale Value | `salesTotalAmount / salesCount` |
| Deposits Count | Count of `CONFIRMED` deposits in period |
| Deposits Total Amount | Sum of deposit amounts (CONFIRMED, in period) |
| Active Reservations Count | Count of `PENDING` + `CONFIRMED` deposits (current snapshot, not date-filtered) |
| Active Reservations Total Amount | Sum of amounts for active reservations |
| Avg Reservation Age (days) | Average age of active reservations |
| Active Prospects Count | Count of contacts with status `PROSPECT` or `QUALIFIED_PROSPECT` (tenant-wide) |
| Sales by Project | Top 10 projects by sales amount |
| Sales by Agent | Top 10 agents by sales amount |
| Inventory by Status | Property count grouped by `PropertyStatus` |
| Inventory by Type | Property count grouped by `PropertyType` |
| Sales Amount by Day | Daily sales aggregation |
| Deposits Amount by Day | Daily deposit aggregation |
| Conversion Rate (deposit→sale) | Percentage of deposits that lead to signed contracts |
| Avg Days Deposit to Sale | Average time from deposit to contract signing |

**RBAC**: AGENT callers have `agentId` forced to self. ADMIN/MANAGER see full tenant.

**Caching**: Caffeine, 30s TTL, max 500 entries. Key: `tenantId:agentId:from:to:projectId`.

**Validation**: `from > to` → 400 `InvalidPeriodException`; unknown project/agent → 404.

---

### 4.9 Outbound Messaging (Email/SMS)

**Purpose**: Send email and SMS messages to contacts from within the CRM.

**User Stories**:
- US-MSG-01: As any user, I can compose and queue an email or SMS message.
- US-MSG-02: As any user, I can list sent/queued messages with filters.
- US-MSG-03: From the contracts or deposits views, I can quickly send a message to the buyer/contact.

**Workflow**:
1. User composes message via `POST /api/messages` → returns 202 Accepted + `{messageId}`.
2. Message is inserted as `PENDING` in outbox table (fast path — no provider call).
3. Async scheduler polls every 5s, picks up PENDING messages via `SELECT FOR UPDATE SKIP LOCKED`.
4. Dispatcher calls `EmailSender` or `SmsSender` provider interface.
5. On success → `SENT`. On failure → retry with exponential backoff (1min, 5min, 30min). After max retries → `FAILED`.

**Message Status Machine**: `PENDING → SENT` or `PENDING → FAILED` (after max retries).

**Provider Interfaces**: `EmailSender`, `SmsSender` — default implementations are Noop (log-only). Replace with `@Primary` bean for real SMTP/Twilio.

**Error Cases**:
- Invalid recipient → 400 `INVALID_RECIPIENT`
- Contact exists but no email/phone → 400 `CONTACT_CHANNEL_MISSING`

---

### 4.10 Commercial Audit Trail

**Purpose**: Immutable event log for commercial workflow events.

**User Stories**:
- US-AUDIT-01: As Admin/Manager, I can view the audit trail of commercial events (deposits, contracts).
- US-AUDIT-02: I can filter audit events by type, correlation ID, and date range.

**Events Recorded**:
- `DEPOSIT_CREATED`, `DEPOSIT_CONFIRMED`, `DEPOSIT_CANCELED`, `DEPOSIT_EXPIRED`
- `CONTRACT_CREATED`, `CONTRACT_SIGNED`, `CONTRACT_CANCELED`

**Transactional Guarantee**: Audit event is recorded within the same transaction as the business operation — atomic rollback.

**RBAC**: `GET /api/audit/commercial` — ADMIN/MANAGER only. AGENT → 403.

---

### 4.11 In-App Notifications

**Purpose**: Notify users of deposit lifecycle events.

**User Stories**:
- US-NOTIF-01: As any user, I receive in-app notifications for deposit events.
- US-NOTIF-02: As any user, I can mark notifications as read.

**Notification Types**: `DEPOSIT_CREATED`, `DEPOSIT_PENDING`, `DEPOSIT_DUE_SOON`, `DEPOSIT_CONFIRMED`, `DEPOSIT_CANCELLED`, `DEPOSIT_EXPIRED`.

---

## 5. Cross-Cutting Requirements

### 5.1 Multi-Tenant Behavior

- Each tenant (company/promoter) has a strictly isolated data space.
- Tenant ID is extracted from JWT `tid` claim and stored in `TenantContext` (ThreadLocal).
- All database queries filter by `tenant_id`; cross-tenant access returns 404 (never 403, to avoid information leakage).
- Tenant bootstrapping via `POST /tenants` creates the tenant record and an owner user.

### 5.2 Auditability

- Commercial audit trail records all deposit and contract lifecycle events with actor, timestamp, and JSON payload.
- Notifications log deposit events per user.
- Outbound messages track send attempts and delivery status.

### 5.3 Document Generation

| Document | Endpoint | Status |
|---|---|---|
| Reservation Certificate (PDF) | `GET /api/deposits/{id}/documents/reservation.pdf` | ✅ Implemented |
| Sales Contract (PDF) | `GET /api/contracts/{id}/documents/contract.pdf` | ✅ Implemented |
| Appels de Fonds (payment schedule PDF) | — | ❌ Not implemented (CDC REQ-2-3-020) |

### 5.4 Dashboard / KPI Definitions

- **Commercial Dashboard**: See §4.8 for full KPI table.
- **Project KPIs**: Per-project property counts by status, deposits, sales.
- **Property Dashboard**: Legacy summary by status and period.

---

## 6. Acceptance Criteria (Key Flows)

### AC-01: Login & Tenant Context

```gherkin
Given a user with email "admin@acme.com" and password "Admin123!" in tenant "acme"
When I POST /auth/login with {tenantKey: "acme", email: "admin@acme.com", password: "Admin123!"}
Then the response status is 200
And the response body contains a JWT token
And the JWT contains claims sub, tid, roles, tv

Given I have a valid JWT for tenant "acme"
When I GET /auth/me
Then the response contains my userId and tenantId matching the JWT claims
```

### AC-02: Create Project & Property

```gherkin
Given I am logged in as ADMIN in tenant "acme"
When I POST /api/projects with {name: "Résidence Sunset"}
Then the project is created with status ACTIVE

Given project "Résidence Sunset" exists and is ACTIVE
When I POST /api/properties with {projectId: <id>, type: "APPARTEMENT", referenceCode: "A-101", ...}
Then the property is created with status DRAFT

Given a project is ARCHIVED
When I POST /api/properties with projectId pointing to the archived project
Then the response status is 400 with error code ARCHIVED_PROJECT
```

### AC-03: Deposit / Reservation Lifecycle

```gherkin
Given property "A-101" is ACTIVE and contact "buyer1" is a PROSPECT
When ADMIN creates a deposit for contact "buyer1" on property "A-101"
Then the deposit status is PENDING
And the property status changes to RESERVED

Given a PENDING deposit exists
When ADMIN confirms the deposit
Then the deposit status is CONFIRMED

Given a CONFIRMED deposit exists
When ADMIN cancels the deposit
Then the deposit status is CANCELLED
And the property status reverts to ACTIVE

Given a PENDING deposit exists past its due date
When the hourly scheduler runs
Then the deposit status is EXPIRED
And the property status reverts to ACTIVE
```

### AC-04: Contract Lifecycle

```gherkin
Given a CONFIRMED deposit exists for property "A-101"
When any user creates a contract for property "A-101"
Then the contract status is DRAFT

Given a DRAFT contract exists
When ADMIN signs the contract
Then the contract status is SIGNED
And the property status changes to SOLD
And an immutable buyer snapshot is captured on the contract

Given a SIGNED contract exists
When ADMIN cancels the contract
And a CONFIRMED deposit exists for the property
Then the property status reverts to RESERVED

Given property "A-101" is already SOLD via a SIGNED contract
When another user tries to sign a second contract for "A-101"
Then the response is 409 PROPERTY_ALREADY_SOLD
```

### AC-05: Dashboard Access Rules

```gherkin
Given I am logged in as AGENT "agent1"
When I GET /api/dashboard/commercial/summary
Then the agentId parameter is forced to my own userId
And I only see my own sales and deposits

Given I am logged in as ADMIN
When I GET /api/dashboard/commercial/summary with agentId=<any>
Then I see the full tenant data for the specified agent

Given I request a period where from > to
Then the response is 400 with INVALID_PERIOD
```

### AC-06: Notifications

```gherkin
Given a deposit is created for a property
Then a DEPOSIT_CREATED notification is generated for the relevant user

Given I have unread notifications
When I POST /api/notifications/{id}/read
Then the notification is marked as read
```

---

## 7. Traceability Matrix

| Req ID | Module | Screen / Route | Endpoint(s) | Test Coverage |
|---|---|---|---|---|
| REQ-2-1-011 | Multi-tenant | — | `POST /tenants`, `GET /tenants/{id}` | `TenantControllerIT`, `CrossTenantIsolationIT` |
| REQ-2-1-012 | Dashboard | `/app/dashboard/commercial` | `GET /api/dashboard/commercial/summary` | `CommercialDashboardIT` |
| REQ-2-1-013 | Users/RBAC | `/app/admin/users` | `GET/POST/PATCH /api/admin/users/*` | `AdminUserControllerIT`, `RbacIT` |
| REQ-2-3-017 | Contacts | `/app/contacts`, `/app/prospects` | `GET/POST/PATCH /api/contacts/*` | `ContactServiceIT`, `ContactControllerIT` |
| REQ-2-3-018 | Properties | `/app/properties` | `GET/POST/PUT/DELETE /api/properties/*` | `PropertyControllerIT` |
| REQ-2-3-019 | Pipeline | `/app/prospects/:id` | Contact status transitions | `ContactServiceIT` |
| REQ-2-3-020 | Doc Gen | — (PDF download) | `GET /api/deposits/{id}/documents/reservation.pdf`, `GET /api/contracts/{id}/documents/contract.pdf` | `ReservationPdfIT`, `ContractPdfIT` |
| REQ-2-3-021 | Messaging | `/app/messages` | `POST/GET /api/messages` | `OutboxIT` |
| REQ-4-3-047 | Projects | `/app/projects` | `GET/POST/PUT/DELETE /api/projects/*` | `ProjectControllerIT` |
| REQ-4-3-053 | KPIs | `/app/dashboard/commercial` | `GET /api/dashboard/commercial/summary` | `CommercialDashboardIT` |

---

## 8. Gap Analysis

| # | Feature | CDC Ref | Status | Evidence / Notes |
|---|---|---|---|---|
| 1 | User management + RBAC | REQ-2-1-013, REQ-4-3-043..045 | ✅ Implemented | `AdminUserController`, `RbacIT`, `TokenRevocationIT` |
| 2 | Multi-tenant + projects | REQ-2-1-011..012, REQ-4-3-046..048 | ✅ Implemented | `TenantControllerIT`, `ProjectControllerIT` |
| 3 | Contacts/Prospects pipeline | REQ-2-3-017, REQ-4-3-049 | ✅ Implemented | `ContactServiceIT`, `ContactControllerIT` |
| 4 | Property catalog | REQ-2-3-018, REQ-4-3-050 | ✅ Implemented | `PropertyControllerIT` |
| 5 | Configurable sales pipeline | REQ-2-3-019, REQ-4-3-051 | ⚠️ Partial | Contact status machine is fixed in code, not configurable by user |
| 6 | Deposits/Reservations | REQ-4-3-052 | ✅ Implemented | `DepositControllerIT`, `CrossTenantIsolationIT` |
| 7 | Reservation PDF | REQ-2-3-020 | ✅ Implemented | `ReservationPdfIT` |
| 8 | Contract PDF | REQ-2-3-020 | ✅ Implemented | `ContractPdfIT` |
| 9 | Appels de Fonds PDF | REQ-2-3-020 | ❌ Not implemented | Payment schedule module not built |
| 10 | SMS/Email from CRM | REQ-2-3-021 | ✅ Implemented | `OutboxIT` (Noop providers — production SMTP/Twilio needed) |
| 11 | Commercial KPI dashboard | REQ-4-3-053, REQ-4-3-062..064 | ✅ Implemented | `CommercialDashboardIT` |
| 12 | Prospection Foncière | REQ-2-2-014..016, REQ-4-3-054..057 | ❌ Not implemented | No code or tests |
| 13 | Workflow Administratif | REQ-2-7-036..038, REQ-4-3-058..061 | ❌ Not implemented | No code or tests |
| 14 | Construction Tracking | REQ-2-4-022..026, REQ-4-3-065..067 | ❌ Not implemented | No code or tests |
| 15 | Stock Management | REQ-2-5-027..030, REQ-4-3-068..070 | ❌ Not implemented | No code or tests |
| 16 | Purchases & Suppliers | REQ-2-6-031..035, REQ-4-3-071..074 | ❌ Not implemented | No code or tests |
| 17 | Finance & Controlling | REQ-2-8-039..042, REQ-4-3-077..079 | ❌ Not implemented | No code or tests |
| 18 | SAV / Tickets | REQ-4-3-085..086 | ❌ Not implemented | No code or tests |
| 19 | External Integrations | REQ-4-3-087..089 | ❌ Not implemented | No code or tests |

### Proposed Next Backlog Items

1. **Appels de Fonds PDF** — Complete REQ-2-3-020 (payment schedule document generation).
2. **Real Email/SMS Providers** — Swap Noop providers for SMTP + Twilio/SMS gateway.
3. **Configurable Sales Pipeline** — Allow per-tenant customization of contact status machine.
4. **Prospection Foncière MVP** — Land database, basic COS/CES, acquisition pipeline.
5. **Administrative Workflow MVP** — Authorization tracking, document archiving, regulatory alerts.
6. **CSV Import/Export** — Bulk data import for properties and contacts.
7. **Internationalization (i18n)** — Consistent French UI with English API messages.

---

## Appendix A — Phase 3: Commercial Intelligence (2026-03)

### A.1 Commission Rules

**Purpose**: Define how commission is calculated per contract. Rules can be tenant-wide (default) or project-specific (project rule wins).

**Formula**: `commission = agreedPrice × (rate / 100) + fixedAmount`

**User Stories**:
- US-COMM-01: As an Admin/Manager, I can create a commission rule for a project or as tenant default.
- US-COMM-02: As any CRM user, I can view commission rules.
- US-COMM-03: As an Admin/Manager, I can update or delete a commission rule.

**Business Rules**:
- Project-specific rule wins over tenant default.
- If no rule is found, commission is 0.
- Formula fields: `rate` (percentage, nullable), `fixedAmount` (currency, nullable).

**Package**: `commission/`; Liquibase changeset 024.

### A.2 Receivables Dashboard

**Purpose**: Track outstanding payment receivables with aging buckets.

**Endpoint**: `GET /api/dashboard/receivables`

**Aging Buckets**:
- Current (not yet due)
- 1–30 days overdue
- 31–60 days overdue
- 61–90 days overdue
- 90+ days overdue

**RBAC**: ADMIN + MANAGER only.

**Caching**: Cache name `receivablesDashboard`, 30-second TTL, 200 entries.

### A.3 Discount Analytics

**Purpose**: Understand pricing discounts across agents and contracts.

**Fields added to CommercialDashboardSummaryDTO**:
- `avgDiscountPercent`: average discount across all signed contracts.
- `maxDiscountPercent`: maximum discount observed.
- `discountByAgent[]`: per-agent breakdown.

**Prerequisite**: `SaleContract.listPrice` must be set for discount to be computable.

### A.4 Prospect Source Funnel

**Purpose**: Analyze where prospects originate (organic, referral, advertisement, social, other).

**Field**: `prospectsBySource[]` in `CommercialDashboardSummaryDTO`.

**Data source**: `ProspectDetail.source` (set at prospect creation/update).

---

## Appendix B — Phase 4: Client-Facing Portal (2026-03)

### B.1 Overview

A separate authentication system allows property buyers (contacts) to log into a read-only portal to view their contracts. The portal uses a separate JWT (`ROLE_PORTAL`) and does not overlap with CRM user authentication.

### B.2 Magic Link Authentication Flow

1. Client requests a magic link: `POST /api/portal/auth/magic-link` with their email.
2. System generates a 32-byte SecureRandom token (URL-safe base64), stores its SHA-256 hash in `portal_token` table (48h TTL, one-time use).
3. System sends the magic link via `EmailSender.send()`.
4. Client clicks the link: `GET /api/portal/auth/verify?token=<raw>`.
5. System verifies SHA-256(raw) against stored hash; marks token used; returns Portal JWT.
6. Portal JWT: `sub`=contactId, `roles`=["ROLE_PORTAL"], `tid`=tenantId, TTL=2h.

**Security notes**:
- Raw token never stored; only the SHA-256 hex hash is persisted.
- Token is single-use; clicking twice → 401.
- Token expires after 48h.
- `EmailSender.send()` called directly (not via outbox — no `User` FK for the public endpoint).

**Liquibase**: changeset 025 (`portal_token` table).

### B.3 Portal Contract View

**Endpoint**: `GET /api/portal/contracts`

**RBAC**: `hasRole("PORTAL")` only. Returns only contracts where `contact.id = contactId` from portal JWT. Cross-contact access → 404.

**Package**: `portal/`; `PortalContractService` reads contactId from `SecurityContextHolder.getAuthentication().getPrincipal()`.

### B.4 Frontend Portal Routes

| Route | Access | Description |
|-------|--------|-------------|
| `/portal/login` | Public | Magic link request form |
| `/portal/verify` | Public | Token verification, stores JWT |
| `/portal/contracts` | `ROLE_PORTAL` | Contract list for the buyer |

**Storage**: `hlm_portal_token` in `localStorage`.
**Interceptor**: `portalInterceptor` attaches JWT only to `/api/portal/` requests. Both interceptors registered in `app.config.ts`.
