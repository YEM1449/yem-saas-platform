# Gap Analysis (CDC vs Current Implementation)

_Last updated: 2026-03-03 (Phase 4 Client-Facing Portal)_

This file lists the main gaps to close between the CDC expectations and the current implementation state.


## Top gaps (prioritized)

1) **Security P1** — Role change / disable must invalidate active JWTs immediately. ✅ CLOSED: `tv` claim + `UserSecurityCacheService` + `JwtAuthenticationFilter` + `TokenRevocationIT` tests.

2) **Context load / IT stability** — Fix the failing Spring test ApplicationContext (root-cause), then make all ITs green. Status: ONGOING — verify with `./mvnw failsafe:integration-test failsafe:verify`.

3) **Project KPIs** — Confirm exact KPI list required by CDC + ensure backend endpoint(s) + UI coverage + tests. ✅ CLOSED: `GET /api/projects/{id}/kpis` (ADMIN/MANAGER), `ProjectControllerIT` coverage, Angular `project-detail` KPI view.

4) **ARCHIVED project guardrail** — Property create/update must reject ARCHIVED projects. ✅ CLOSED (Batch 1): `ProjectActiveGuard` + `ArchivedProjectAssignmentException` + 400 `ARCHIVED_PROJECT` + tests.

5) **Commercial MVP completeness** — SaleContract entity, lifecycle (DRAFT→SIGNED→CANCELED), property status on sign/cancel, double-booking guard. ✅ CLOSED (PR-1 + PR-2 + tightenings): `SaleContract` + `SaleContractService` + `ContractController`; Liquibase changeset 016; double-booking guard (service + partial unique index `uk_sc_property_signed`); `DepositService.confirm()` pessimistic lock + SOLD guard; consistent lock ordering across all 4 concurrent flows; `DepositRepository.existsActiveConfirmedDepositForProperty()` for cancel-rule; `ContractControllerIT` (10 tests).

6) **Commercial Dashboard KPIs** — Single-call summary endpoint, RBAC scoping, caching, Angular UI. ✅ CLOSED (PR-3 + Phase 3): `CommercialDashboardController` + `CommercialDashboardService`; `CommercialDashboardIT` (7 tests including discount + prospect funnel); Angular `CommercialDashboardComponent` with full KPI suite. Phase-3 additions: `avgDiscountPercent`, `maxDiscountPercent`, `discountByAgent[]`, `prospectsBySource[]` (14-query budget). Cash/receivables gap closed: `GET /api/dashboard/receivables` (`ReceivablesDashboardService`: outstanding, overdue, collection rate, aging buckets — IT: `ReceivablesDashboardIT` 3 tests). Commission tracking gap closed: `GET /api/commissions` + `/my` + commission rule CRUD (`CommissionIT` 4 tests; Liquibase 024).

7) **Commission / Revenue tracking** — Agents need to know their commission per contract. ✅ CLOSED (Phase 3): `commission/` package with `CommissionRule` entity (project-specific + tenant-default, date-effective), `CommissionService` (rule lookup with priority + formula), and `CommissionController`. ADMIN manages rules; AGENT/MANAGER view commissions.

8) **Receivables / Cash-in analytics** — Outstanding and overdue receivables dashboard needed. ✅ CLOSED (Phase 3): `ReceivablesDashboardController` + `ReceivablesDashboardService`. Aging buckets computed in Java from raw call data. Collection rate = payments received / total issued. Caffeine cache 30 s.

9) **Client-facing buyer portal** — Buyers need a self-service view of their contract, payment schedule, and property. ✅ CLOSED (Phase 4): `portal/` package. Magic-link authentication (`portal_token` entity + Liquibase 025, SHA-256 hash, 48 h TTL, one-time use). `ROLE_PORTAL` JWT (`PortalJwtProvider`, 2 h TTL, contactId as subject, no `tv` claim). SecurityConfig rule order isolates ROLE_PORTAL from all CRM endpoints. Endpoints: `POST /api/portal/auth/request-link`, `GET /api/portal/auth/verify`, `GET /api/portal/contracts`, `GET /api/portal/contracts/{id}/documents/contract.pdf`, `GET /api/portal/contracts/{id}/payment-schedule`, `GET /api/portal/properties/{id}`, `GET /api/portal/tenant-info`. Angular: lazy-loaded `/portal/*` route tree with `portalGuard` + `portalInterceptor` + separate `hlm_portal_token` localStorage key. IT: `PortalAuthIT` (5), `PortalContractsIT` (4), `PortalPaymentsIT` (4) — 13 tests.


## By CDC P1 backlog item

See `docs/specs/Backlog_Status.md` for a compact status table.


## Traceability rules

- When implementing a gap, reference CDC section + requirement IDs from `Requirements_Index.md` in PR descriptions.

- If you intentionally deviate, add an entry in `Spec_Deltas.md`.
