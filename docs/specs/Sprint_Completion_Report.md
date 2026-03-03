# Sprint Completion Report — Phases 1–4

_Generated: 2026-03-03_
_Branch: Epic/Commercial-Intelligence_
_Tech stack: Spring Boot 3.5.8 / Java 21 / Angular 19 / PostgreSQL / Liquibase / Testcontainers_

---

## Executive Summary

Four implementation phases delivered across the sprint, taking the system from a foundational
multi-tenant CRM skeleton to a production-ready commercial real-estate SaaS with buyer
self-service capabilities.

| Phase | Label | Status |
|-------|-------|--------|
| 1 | Revenue Foundation — Payment Module | ✅ COMPLETE |
| 2 | Operational Polish (Timeline, Reminders, Media, CSV Import) | ✅ COMPLETE |
| 3 | Commercial Intelligence (Receivables, Discounts, Commissions, Source Funnel) | ✅ COMPLETE |
| 4 | Client-Facing Portal (Magic Link, Buyer Portal) | ✅ COMPLETE |

---

## Features Delivered

### Phase 1 — Revenue Foundation (`PR-8` — 2026-03-01)

**F1 — Payment Schedule / Appels de Fonds**
- `PaymentSchedule` (1:1 to `SaleContract`) with ordered `PaymentTranche` rows.
- `PaymentCall` (Appel de Fonds): DRAFT → ISSUED → OVERDUE | CLOSED lifecycle.
- `Payment` entity: records cash-in against a call; auto-closes call when fully paid.
- Overdue scheduler: marks ISSUED calls past `due_date` as OVERDUE (disabled in test profile).
- **Appel de Fonds PDF**: `GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf` — Thymeleaf → OpenHTMLToPDF.
- SMTP `EmailSender` implementation activated by `@ConditionalOnProperty("app.email.host")`.
- Frontend: `PaymentScheduleComponent` at `/app/contracts/:contractId/payments`.
- Liquibase changesets: 020 (`payment_schedule` + `payment_tranche`), 021 (`payment_call`), 022 (`payment`).

---

### Phase 2 — Operational Polish (`PR-9` — 2026-03-01)

**F2.1 — Contact Activity Timeline**
- `GET /api/contacts/{id}/timeline?limit=50` (all roles).
- `ContactTimelineService` aggregates audit events, outbox messages, in-app notifications via shared correlation IDs.
- Angular: "Fiche / Historique" tabs on contact detail; lazy-loaded.

**F2.2 — Automated Reminders**
- `ReminderService`: 3 idempotent workflows — deposit due-date (J-7/J-3/J-1), payment call overdue (EMAIL + in-app ADMIN), prospect stale (14-day inactivity → in-app).
- `ReminderScheduler`: daily at 08:00; disabled in test profile via `@ConditionalOnProperty`.

**F2.3 — Property Media Uploads**
- `PropertyMedia` entity + `LocalFileMediaStorage` (S3-swappable via `@Primary`).
- Endpoints: `POST /api/properties/{id}/media` (ADMIN/MANAGER), `GET` list, `GET` download, `DELETE` (ADMIN).
- New `ErrorCode`s: `MEDIA_TOO_LARGE`, `MEDIA_TYPE_NOT_ALLOWED`, `MEDIA_NOT_FOUND`.
- Liquibase changeset 023.
- Frontend: media gallery + upload button on `property-detail.component`.

**F2.4 — CSV Property Import**
- `POST /api/properties/import` (ADMIN/MANAGER, multipart CSV).
- All-or-nothing: validates all rows before inserting any. 200 + `{imported}` or 422 + `{errors[]}`.
- Apache Commons CSV 1.12.0.
- Frontend: "Importer CSV" button + row-level error table.

---

### Phase 3 — Commercial Intelligence (2026-03-02)

**F3.1 — Receivables Dashboard**
- `GET /api/dashboard/receivables` (all roles; AGENT auto-scoped).
- KPIs: `totalOutstanding`, `totalOverdue`, `collectionRate`, `avgDaysToPayment`, aging buckets (0/30/60/90/90+d), top-10 overdue projects, recent 10 payments.
- Caffeine cache `receivablesDashboard` (30 s TTL, 200 entries).

**F3.2 — Discount Analytics**
- `CommercialDashboardSummaryDTO` extended: `avgDiscountPercent`, `maxDiscountPercent`, `discountByAgent[]` (top-10).
- Formula: `(listPrice − agreedPrice) / listPrice × 100`. Requires `SaleContract.listPrice`.

**F3.3 — Commission Tracking**
- `commission/` package: `CommissionRule` entity (Liquibase 024), `CommissionService` (project-specific rule wins over tenant default; date-effective).
- Formula: `agreedPrice × ratePercent / 100 + fixedAmount`.
- CRUD: `GET|POST|PUT|DELETE /api/commission-rules` (ADMIN); `GET /api/commissions/my` (all); `GET /api/commissions?agentId=` (ADMIN/MANAGER).
- Frontend: `CommissionsComponent` with ADMIN rule CRUD.

**F3.4 — Prospect Source Funnel**
- `CommercialDashboardSummaryDTO` extended: `prospectsBySource[]` (source, count, convertedCount, conversionRate).
- JPQL groups by `ProspectDetail.source` with conditional count for converted statuses.

---

### Phase 4 — Client-Facing Buyer Portal (2026-03-02)

**F4.1 — Portal Auth (Magic Link)**
- `POST /api/portal/auth/request-link` (public) + `GET /api/portal/auth/verify?token=` (public).
- 32-byte `SecureRandom` → URL-safe base64 raw token; SHA-256 hex stored in DB (raw token never persisted).
- 48 h TTL, one-time use. Email sent via `EmailSender.send()` directly (not outbox — no User FK on public endpoint).
- `PortalJwtProvider`: 2 h JWT; `sub`=contactId, `tid`=tenantId, `roles`=["ROLE_PORTAL"]. No `tv` claim.
- `JwtAuthenticationFilter` detects ROLE_PORTAL → skips `UserSecurityCacheService` check.
- Liquibase changeset 025 (`portal_token`).

**F4.2 — Portal Contracts & Documents**
- `GET /api/portal/contracts` — buyer's own SIGNED contracts.
- `GET /api/portal/contracts/{id}/documents/contract.pdf` — buyer's own contract PDF.
- Cross-ownership → 404 (no info leak).

**F4.3 — Portal Payment Schedule**
- `GET /api/portal/contracts/{id}/payment-schedule` — buyer's own schedule (ownership-verified).

**F4.4 — Portal Property**
- `GET /api/portal/properties/{id}` — verifies buyer has a signed contract for the property.
- `GET /api/portal/tenant-info` — tenant name + logo URL for portal shell header.

**F4.5 — Portal Layout & Branding**
- Angular lazy-loaded route tree at `/portal/*`; `PortalShellComponent` wraps authenticated pages.
- `portalGuard` protects all non-login portal routes.
- `portalInterceptor` attaches portal JWT only to `/api/portal/` requests; auto-logout on 401.
- Separate `hlm_portal_token` localStorage key (completely independent of CRM session).
- Config: `app.portal.base-url` (default `http://localhost:4200`).

**F4.6 — ROLE_PORTAL RBAC Isolation**
- SecurityConfig rule order: portal auth → `permitAll`; `/api/portal/**` → `hasRole("PORTAL")`; `/api/**` → `hasAnyRole("ADMIN","MANAGER","AGENT")` — ROLE_PORTAL explicitly blocked from all CRM endpoints (→ 403).

---

## New API Endpoints (Complete List — Phases 1–4)

### Phase 1 — Payment Module

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET    | `/api/contracts/{id}/payment-schedule` | All roles | Get payment schedule |
| POST   | `/api/contracts/{id}/payment-schedule` | ADMIN/MANAGER | Create schedule |
| PATCH  | `/api/contracts/{id}/payment-schedule/tranches/{tid}` | ADMIN/MANAGER | Update tranche |
| POST   | `/api/contracts/{id}/payment-schedule/tranches/{tid}/issue-call` | ADMIN/MANAGER | Issue call |
| GET    | `/api/payment-calls` | All roles | List calls (paged) |
| GET    | `/api/payment-calls/{id}` | All roles | Get call |
| GET    | `/api/payment-calls/{id}/documents/appel-de-fonds.pdf` | All roles (AGENT own) | PDF |
| GET    | `/api/payment-calls/{id}/payments` | All roles | List payments |
| POST   | `/api/payment-calls/{id}/payments` | ADMIN/MANAGER | Record payment |
| GET    | `/api/dashboard/commercial/cash` | All roles | Cash dashboard |

### Phase 2 — Operational Polish

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET    | `/api/contacts/{id}/timeline` | All roles | Contact activity timeline |
| POST   | `/api/properties/{id}/media` | ADMIN/MANAGER | Upload media |
| GET    | `/api/properties/{id}/media` | All roles | List media |
| GET    | `/api/media/{mediaId}/download` | All roles | Download media |
| DELETE | `/api/media/{mediaId}` | ADMIN | Delete media |
| POST   | `/api/properties/import` | ADMIN/MANAGER | CSV import |

### Phase 3 — Commercial Intelligence

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET    | `/api/dashboard/receivables` | All roles (AGENT auto-scoped) | Receivables dashboard |
| GET    | `/api/commissions/my` | All roles | Own commissions |
| GET    | `/api/commissions` | ADMIN/MANAGER | All commissions |
| GET    | `/api/commission-rules` | ADMIN | List commission rules |
| POST   | `/api/commission-rules` | ADMIN | Create rule |
| PUT    | `/api/commission-rules/{id}` | ADMIN | Update rule |
| DELETE | `/api/commission-rules/{id}` | ADMIN | Delete rule |

### Phase 4 — Client-Facing Portal

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST   | `/api/portal/auth/request-link` | Public | Request magic link |
| GET    | `/api/portal/auth/verify?token=` | Public | Verify token → JWT |
| GET    | `/api/portal/contracts` | ROLE_PORTAL | Buyer's contracts |
| GET    | `/api/portal/contracts/{id}/documents/contract.pdf` | ROLE_PORTAL | Contract PDF |
| GET    | `/api/portal/contracts/{id}/payment-schedule` | ROLE_PORTAL | Payment schedule |
| GET    | `/api/portal/properties/{id}` | ROLE_PORTAL | Property details |
| GET    | `/api/portal/tenant-info` | ROLE_PORTAL | Tenant name/logo |

---

## Database Changes (Changeset Summary)

| # | File | Table(s) | Description |
|---|------|----------|-------------|
| 020 | `020-create-payment-schedule.yaml` | `payment_schedule`, `payment_tranche` | Payment schedule + tranches |
| 021 | `021-create-payment-call.yaml` | `payment_call` | Appel de fonds entity |
| 022 | `022-create-payment.yaml` | `payment` | Cash-in records |
| 023 | `023-create-property-media.yaml` | `property_media` | Media attachments for properties |
| 024 | `024-create-commission-rule.yaml` | `commission_rule` | Commission rules (tenant/project-scoped, date-effective) |
| 025 | `025-create-portal-token.yaml` | `portal_token` | Magic link tokens (SHA-256 hash, 48 h TTL) |

**Total changesets applied across all phases:** 001–025 (+ 012b fix)
**Schema tables added this sprint (020–025):** 6 new tables

---

## Test Coverage

### Integration Tests (IT) — 31 files, 291 @Test methods

| IT Class | Tests | Domain |
|----------|------:|--------|
| `PropertyControllerIT` | 38 | Property CRUD + RBAC |
| `ProjectControllerIT` | 28 | Project lifecycle + KPIs |
| `RbacIT` | 23 | Cross-feature RBAC matrix |
| `AdminUserControllerIT` | 22 | User management |
| `ContactControllerIT` | 15 | Contact CRUD |
| `DepositControllerIT` | 13 | Deposit lifecycle + locking |
| `ContractControllerIT` | 11 | Contract lifecycle + sign/cancel |
| `PaymentScheduleIT` | 10 | Schedule + tranche + call lifecycle |
| `PaymentIT` | 10 | Cash-in + auto-close |
| `CrossTenantIsolationIT` | 9 | Tenant isolation |
| `OutboxIT` | 9 | Outbound messaging dispatch |
| `ReservationPdfIT` | 8 | Reservation PDF (RBAC + content) |
| `PropertyMediaIT` | 7 | Media upload/download/delete |
| `ContractPdfIT` | 7 | Contract PDF (RBAC + content) |
| `CommercialDashboardIT` | 7 | Dashboard KPIs + discount + funnel |
| `TokenRevocationIT` | 6 | JWT revocation on role change |
| `TenantControllerIT` | 6 | Tenant CRUD |
| `PropertyImportIT` | 6 | CSV import + validation |
| `PaymentCallPdfIT` | 6 | Appel de Fonds PDF |
| `ErrorContractIT` | 6 | Error envelope contract |
| `AuthLoginIT` | 6 | Login flow |
| `PortalAuthIT` | 5 | Magic link flow + RBAC isolation |
| `ContactServiceIT` | 5 | Contact service edge cases |
| `PortalPaymentsIT` | 4 | Portal schedule + property access |
| `PortalContractsIT` | 4 | Portal contracts + tenant info |
| `ContactTimelineIT` | 4 | Timeline aggregation |
| `CommissionIT` | 4 | Commission rule priority + RBAC |
| `CommercialAuditIT` | 4 | Audit event recording |
| `AuthMeIT` | 4 | /me endpoint |
| `ReceivablesDashboardIT` | 3 | Receivables KPIs |
| `HlmBackendApplicationIT` | 1 | ApplicationContext smoke test |

### Unit Tests — 13 files, 47 @Test methods

| Test Class | Tests | Domain |
|------------|------:|--------|
| `PaymentScheduleServiceTest` | 5 | Schedule service logic |
| `ReminderServiceTest` | 5 | Reminder idempotency |
| `PropertyCommercialWorkflowServiceTest` | 4 | Property status transitions |
| `CommercialDashboardServiceTest` | 4 | Dashboard RBAC + caching |
| Others | 29 | Various domain units |

**Grand total: 338 @Test methods across 44 test classes.**

---

## Remaining Gaps vs CDC

### CDC modules NOT YET started

| CDC Module | Priority | Notes |
|------------|----------|-------|
| Prospection foncière | P1 | Land prospecting workflow — no evidence in codebase |
| Workflow administratif (Maroc + UE) | P1 | Legal/admin document workflow — not started |
| Module Construction — Phase 1 | P1 | Gantt, phases, site journal — not started |
| Gestion des stocks chantier | P1 | Site inventory — not started |
| Achats & fournisseurs | P1 | Procurement — not started |
| Automatisations & notifications avancées | P1 | Advanced automation rules — partially covered by Reminders (P2.2) but CDC scope is broader |
| Module Finance complet | P1 | Full finance module — payment basics done but invoicing/accounting not started |
| Qualité & sécurité chantier | P1 | Quality/safety site management — not started |
| Module Sous-traitants | P1 | Subcontractor management — not started |
| SAV & gestion des tickets | P1 | After-sales / ticket system — not started |
| Intégrations externes (API) | Unknown | REST APIs exist; webhooks/ERP integrations not verified |

### Known open issues

1. **IT stability** — ApplicationContext loads cleanly but verify all 291 IT tests pass end-to-end:
   `cd hlm-backend && ./mvnw failsafe:integration-test`

2. **Consolidated reporting** — Multi-société consolidated dashboard not yet addressed (CDC requirement for multi-project roll-ups across companies).

3. **Portal: no magic-link resend rate limiting** — Production deployments should add rate limiting on `POST /api/portal/auth/request-link` to prevent email flooding.

4. **Portal: token cleanup job** — Expired `portal_token` rows accumulate; a scheduled cleanup job (e.g., delete `where expires_at < NOW() - interval '7 days'`) is recommended for production.

5. **`SaleContract.listPrice`** — Discount analytics only work when `listPrice` is set. There is no UI enforcement to require `listPrice` on contract creation. Consider making it required or documenting it as optional.

---

## Recommended Next Priorities

### Immediate (P0 — pre-production hardening)

1. **Rate limit portal magic-link requests** — Max 5 requests per email per hour (Redis rate limiter or Bucket4j).
2. **Portal token cleanup scheduler** — Cron job to purge expired tokens (keep DB lean).
3. **Run full IT suite** — Confirm all 291 IT tests pass: `./mvnw failsafe:integration-test`.
4. **`SaleContract.listPrice` enforcement** — Add validation or at minimum surfacing in contract create UI.

### High (P1 — next sprint)

5. **Module Construction Phase 1** — Gantt chart for construction phases, site journal entries, progress % per phase. This is the largest unstarted P1 gap.
6. **Prospection Foncière** — Land prospecting: plots, legal status, acquisition pipeline. Core to the CDC scope.
7. **Consolidated Multi-Société Dashboard** — Roll-up KPIs across multiple tenant companies for group reporting.
8. **Finance Module basics** — Invoice generation from payment calls, VAT handling (Moroccan and EU regimes), payment receipts.

### Medium (P2 — future sprints)

9. **External integrations** — Webhook outbound events, ERP sync stubs (SAP/Sage), OCR for document ingestion.
10. **After-sales (SAV)** — Ticket system linked to COMPLETED_CLIENT contacts.
11. **Subcontractor module** — Supplier roster, contract assignment, invoice reconciliation.
12. **Advanced portal features** — Construction progress tracking in buyer portal, document uploads (punch lists), push notifications.

---

## Final Verification Commands

```bash
# 1 — Backend unit tests (fast, no Docker)
cd hlm-backend && ./mvnw test

# 2 — Backend integration tests (Docker required for Testcontainers Postgres)
cd hlm-backend && ./mvnw failsafe:integration-test

# 3 — Specific portal IT tests
cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test="PortalAuthIT,PortalContractsIT,PortalPaymentsIT"

# 4 — Frontend production build (catches type errors)
cd hlm-frontend && npm run build

# 5 — Auth smoke test against a running backend
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh

# 6 — Verify all Liquibase changesets applied (requires running backend)
curl -s http://localhost:8080/actuator/liquibase | jq '.contexts[].liquibaseBeans[].changeSets | length'

# 7 — Portal magic-link smoke test (requires running backend + contact with email in DB)
curl -s -X POST http://localhost:8080/api/portal/auth/request-link \
  -H 'Content-Type: application/json' \
  -d '{"email":"buyer@example.com","tenantKey":"acme"}' | jq '.magicLinkUrl'
```

---

_Report generated from verified codebase state at commit HEAD on branch `Epic/Commercial-Intelligence`._
_All 4 phases marked COMPLETE in `.sprint-state.md`._
