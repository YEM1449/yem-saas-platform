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

## Payment / Finance Module (PR-8 — Revenue Foundation, 2026-03-01)

### Domain model
- `PaymentSchedule` — linked 1:1 to `SaleContract`; container for ordered `PaymentTranche` rows.
- `PaymentTranche` — a milestone slice of the total agreed price (percentage + absolute amount). Status lifecycle: `PLANNED → ISSUED → PARTIALLY_PAID | PAID | OVERDUE`.
- `PaymentCall` (Appel de Fonds) — payment request issued for a specific tranche. Status lifecycle: `DRAFT → ISSUED → OVERDUE (scheduler) | CLOSED (on full payment)`.
- `Payment` — cash-in record against a `PaymentCall`. Recording a payment triggers tranche + call status updates.

### Finance KPI definitions (locked)
| KPI | Definition |
|-----|-----------|
| **Receivables** | `SUM(tranche.amount)` WHERE `tranche.status IN (PLANNED, ISSUED, PARTIALLY_PAID, OVERDUE)` |
| **Overdue receivables** | `SUM(call.amountDue − totalPaymentsReceived)` WHERE `call.status = OVERDUE` |
| **Collection rate** | `SUM(payments.amountReceived) / SUM(tranche.amount) × 100` |
| **Avg days to collect** | `AVG(lastPayment.receivedAt − call.issuedAt)` for CLOSED calls |

### Payment methods
`BANK_TRANSFER`, `CHECK`, `CASH`, `OTHER`

## Operational Polish (PR-9 — 2026-03-01)

### F2.1 — Contact Activity Timeline
- Backend: `GET /api/contacts/{id}/timeline?limit=50` (all authenticated roles). `ContactTimelineService` aggregates three sources: `CommercialAuditRepository`, `OutboundMessageRepository`, `NotificationRepository`, correlated via contact/deposit/contract UUIDs.
- Response DTO: `TimelineEventResponse` with `timestamp`, `eventType`, `category` (AUDIT/MESSAGE/NOTIFICATION), `summary`, `correlationId`.
- Frontend: `contact-detail.component` gains "Fiche / Historique" tabs; timeline lazy-loaded on first tab click.
- IT: `ContactTimelineIT` (4 tests: empty, after audit event, cross-tenant 404, 401).

### F2.2 — Automated Reminders
- Backend: `ReminderService` (3 idempotent workflows; idempotency via `OutboundMessageRepository.existsPendingOrSent(correlationId, correlationType)`).
  - Deposit due-date: EMAIL to agent at J-7, J-3, J-1.
  - Payment call overdue: EMAIL to contract agent + in-app `PAYMENT_CALL_OVERDUE` notification to all ADMINs.
  - Prospect follow-up: in-app `PROSPECT_STALE` notification to ADMIN+MANAGER when PROSPECT/QUALIFIED_PROSPECT has no audit/message activity for ≥14 days.
- `ReminderScheduler` fires daily at 08:00; `@ConditionalOnProperty("spring.task.scheduling.enabled")` — disabled in test profile.
- Config: `app.reminder.{enabled, cron, deposit-warn-days, prospect-stale-days}`.
- Unit tests: `ReminderServiceTest` (5 tests).

### F2.3 — Property Media Uploads
- Liquibase changeset 023 (`property_media` table). Package `media/`: `PropertyMedia` entity, `PropertyMediaRepository`, `MediaStorageService` interface + `LocalFileMediaStorage` default (path-traversal guarded; swap to S3 via `@Primary` bean).
- `PropertyMediaService`: upload (size+type validation), list, download, delete.
- Endpoints: `POST /api/properties/{id}/media` (ADMIN/MANAGER, 201), `GET /api/properties/{id}/media` (all), `GET /api/media/{mediaId}/download` (all), `DELETE /api/media/{mediaId}` (ADMIN).
- New `ErrorCode`s: `MEDIA_TOO_LARGE` (400), `MEDIA_TYPE_NOT_ALLOWED` (400), `MEDIA_NOT_FOUND` (404).
- Config: `app.media.{storage-dir, max-file-size, allowed-types}`. Test override: `${java.io.tmpdir}/hlm-test-media`.
- Frontend: `property-detail.component` at `/app/properties/:id` — property fields + media gallery (image thumbnails + PDF cards), upload button (ADMIN/MANAGER), delete button (ADMIN).
- IT: `PropertyMediaIT` (7 tests).

### F2.4 — CSV Import for Properties
- Backend: `POST /api/properties/import` (ADMIN/MANAGER, multipart). `PropertyImportService` parses CSV (Apache Commons CSV 1.12.0), validates all rows first (all-or-nothing). Returns `ImportResultResponse {imported, errors[]}`.
  - On any row error: 422 with full error list, zero rows imported.
  - On success: 200 with `imported` count.
- Frontend: "Importer CSV" label button on properties list; on success reloads list; on error displays row-level error table.
- IT: `PropertyImportIT` (6 tests).

## Commercial Intelligence (Phase 3 — 2026-03-02)

### F3.1 — Receivables Dashboard
- Backend: `GET /api/dashboard/receivables` (all authenticated roles; AGENT auto-scoped to own contracts).
- `ReceivablesDashboardService` computes outstanding/overdue totals, collection rate, avg days to payment, and aging buckets (current / ≤30d / ≤60d / ≤90d / >90d) from `PaymentCall` and `Payment` data.
- Cache: `receivablesDashboard`, 30 s TTL, 200 entries.
- Frontend: `ReceivablesDashboardComponent` at `/app/dashboard/receivables`; nav link "Receivables" (ADMIN/MANAGER).
- IT: `ReceivablesDashboardIT` (3 tests).

### Receivables KPI definitions (locked)
| KPI | Definition |
|-----|-----------|
| **totalOutstanding** | `SUM(call.amountDue)` WHERE `call.status IN (ISSUED, OVERDUE)` |
| **totalOverdue** | `SUM(call.amountDue)` WHERE `call.status = OVERDUE` |
| **collectionRate** | `SUM(payments.amountReceived) / SUM(call.amountDue WHERE status IN (ISSUED,OVERDUE,CLOSED)) × 100` |
| **avgDaysToPayment** | `AVG(payment.receivedAt − call.issuedAt)` for CLOSED calls |
| **agingBucket.current** | calls due in the future (dueDate > today) |
| **agingBucket.days30** | calls 1–30 days overdue |
| **agingBucket.days60** | calls 31–60 days overdue |
| **agingBucket.days90** | calls 61–90 days overdue |
| **agingBucket.days90plus** | calls >90 days overdue |

### F3.2 — Discount Analytics
- `CommercialDashboardSummaryDTO` extended with: `avgDiscountPercent`, `maxDiscountPercent`, `discountByAgent[]` (top-10 agents by avg discount).
- Discount % formula: `(listPrice - agreedPrice) / listPrice × 100`. Requires `SaleContract.listPrice` to be populated.
- New queries in `SaleContractRepository`: `discountTotals(tenantId, agentId, projectId)` and `discountByAgent(tenantId, Pageable)`.
- New DTO: `DiscountByAgentRow(agentId, agentEmail, avgDiscountPercent, salesCount)`.

### F3.3 — Commission Tracking
- New package: `commission/` (domain, repo, service, api/dto).
- **Liquibase changeset 024**: `commission_rule` table — `id` (UUID), `tenant_id` (FK), `project_id` (nullable FK), `rate_percent` (DECIMAL 5,2), `fixed_amount` (nullable DECIMAL 15,2), `effective_from` (DATE), `effective_to` (nullable DATE).
- **Rule priority**: project-specific rule takes precedence over tenant-wide default; date-effective (checked via `effective_from ≤ today ≤ effective_to`).
- **Commission formula**: `agreedPrice × ratePercent / 100 + fixedAmount` (fixedAmount defaults to 0 if null).
- **Endpoints**:
  - `GET /api/commissions/my` — own commissions for the calling agent (all roles)
  - `GET /api/commissions?agentId=&from=&to=` — all commissions (ADMIN/MANAGER)
  - `GET /api/commission-rules` — list rules (ADMIN)
  - `POST /api/commission-rules` — create rule (ADMIN, 201)
  - `PUT /api/commission-rules/{id}` — update rule (ADMIN)
  - `DELETE /api/commission-rules/{id}` — delete rule (ADMIN, 204)
- **ErrorCode**: `COMMISSION_RULE_NOT_FOUND` (404).
- **Frontend**: `CommissionsComponent` at `/app/commissions`; nav link "Commissions" (all roles); ADMIN sees rule CRUD form.
- **IT**: `CommissionIT` (4 tests: tenant default rule calculation, project-specific priority over default, AGENT scope via `/my`, ADMIN sees all).

### F3.4 — Prospect Source Funnel
- `CommercialDashboardSummaryDTO` extended with: `prospectsBySource[]` (ProspectSourceRow: source, count, convertedCount, conversionRate).
- Source is stored in `ProspectDetail.source` (String, max 80 chars; set via repository or future contact update endpoint).
- New JPQL query `ContactRepository.prospectSourceFunnel(tenantId, convertedStatuses)`: groups by `pd.source`, counts total and converted (status CLIENT/ACTIVE_CLIENT/COMPLETED_CLIENT/REFERRAL).
- New DTO: `ProspectSourceRow(source, count, convertedCount, conversionRate)` (conversionRate = convertedCount/count × 100, computed in service).

## Phase 4 — Client-Facing Portal (2026-03-02)

### F4.1 — Portal Auth (Magic Link)
- **No password, no registration**: buyers authenticate via a time-limited magic link sent to their registered Contact email.
- **Flow**: `POST /api/portal/auth/request-link {email, tenantKey}` → service generates 32-byte SecureRandom token → SHA-256 hex stored in `portal_token` table → email sent → raw token URL returned in response body for dev convenience.  `GET /api/portal/auth/verify?token=<raw>` → hash → DB lookup + validity check → mark used → return 2 h portal JWT.
- **Security**: only the SHA-256 hash is stored. Raw token is single-use (48 h expiry). Even if DB is compromised, raw token cannot be reconstructed.
- **Email sending**: calls `EmailSender.send()` directly (not outbox) — outbox requires a `createdByUser` FK which doesn't exist for public auth. Email failure is caught and swallowed; token generation succeeds regardless.
- **Liquibase changeset 025**: `portal_token` table — `id UUID PK`, `tenant_id FK`, `contact_id FK`, `token_hash VARCHAR(64) UNIQUE`, `expires_at TIMESTAMP`, `used_at TIMESTAMP NULL`, `created_at TIMESTAMP`.
- **ErrorCode**: `PORTAL_TOKEN_INVALID` (401) for invalid/expired/used tokens, or unknown tenant/email.
- **Anti-enumeration**: `requestLink` throws the same `PortalTokenInvalidException` for unknown tenant or unknown email (same error message, same HTTP status).

### F4.2–F4.4 — Portal Data Access
- All portal data endpoints require `ROLE_PORTAL` (via `@PreAuthorize("hasRole('PORTAL')")`).
- `PortalContractService` extracts contactId from `SecurityContextHolder.getAuthentication().getPrincipal()` (portal JWT stores contactId as JWT subject → set as principal by filter).
- Cross-contact access always returns 404 (no info leak).
- Property access requires at least one SIGNED contract linking the buyer to that property.

### F4.5 — ROLE_PORTAL Isolation
- Portal JWTs carry `roles=["ROLE_PORTAL"]` — not present in any CRM user JWT.
- `JwtAuthenticationFilter` detects ROLE_PORTAL and skips `UserSecurityCacheService` (portal JWT subject is a contactId, not a userId; no CRM User row exists for buyers).
- `SecurityConfig` rule order ensures ROLE_PORTAL cannot access `/api/**` CRM endpoints (→ 403).
- `portalInterceptor` in Angular attaches portal JWT only to `/api/portal/` calls; `authInterceptor` attaches CRM JWT only to non-portal calls. The two session types are fully independent (different localStorage keys: `hlm_token` vs `hlm_portal_token`).

## Living-spec helpers
- Progress tracking: `docs/spec/Backlog_Status.md`, `docs/spec/Implementation_Status.md`, `docs/spec/Gap_Analysis.md`.
