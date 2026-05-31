# HLM CRM — Enterprise Architecture, Security, Functional & Construction Readiness Audit

**Date:** 2026-05-31
**Scope:** Full platform audit prior to building the Construction Management Module
**Method:** Static analysis of the live codebase (541 backend `.java` files, 169 frontend `.ts` files, 74 Liquibase changesets, 69 test files), grounded against `CLAUDE.md`, prior `docs/AUDIT_REPORT.md` (2026-03-22), and direct file inspection.

> This is a point-in-time engineering assessment. Scores are relative to a "production SaaS ready for a major new domain module" bar, not an absolute. Every numeric score below is justified by cited evidence.

---

## 1. Executive Summary

HLM is a **well-built real-estate CRM modular monolith** (Spring Boot 3.5.11 / Java 21 / Angular 19) with genuinely strong multi-tenant isolation (société-scoped queries + PostgreSQL RLS phase 2 + nil-UUID system bypass), a mature security baseline (httpOnly-cookie JWT, CSP, RLS, rate limiting, ShedLock), and a rich, coherent sales domain (prospect → reservation → deposit → vente pipeline → portal). 27 backend modules follow a disciplined `api/domain/repo/service` layering. The codebase is **above average for a single-team SaaS** and clearly the product of sustained, careful iteration (Waves 4–15).

**The platform is READY to host a Construction Management Module — but NOT to absorb it into the current monolith without deliberate structural work first.** The blockers are not security or infrastructure (both strong); they are **domain-modeling depth, the lack of an explicit application/CQRS layer, an immature shared frontend design-system + state strategy, and the total absence of construction primitives** (WBS, cost/budget, contractors, scheduling, inspections, HSE). Construction is a fundamentally different domain than CRM — it is graph-shaped (dependencies, critical path), document-heavy, financially rigorous (commitments vs. actuals), and field-operated (mobile/offline). Bolting it onto the CRM's flat CRUD-service pattern would create the platform's first true "big ball of mud."

**Headline scores (detail in §16):**

| Dimension | Score |
|---|---|
| Architecture | 72/100 |
| Security | 82/100 |
| Performance | 70/100 |
| Maintainability | 74/100 |
| Scalability | 68/100 |
| UI/UX | 66/100 |
| Construction Readiness | 22/100 |
| **Overall Platform Maturity** | **68/100** |

**Critical pre-build actions:** establish a construction bounded context with its own schema + module boundary; introduce an application/command layer (light CQRS) before the domain explodes; build a real shared component library + a single state strategy on the frontend; add a scheduling/graph engine decision (this is the long-pole). Full ordered list in §15 (Top 50).

---

## 2. Architecture Assessment

### 2.1 What exists (evidence)
- **Modular monolith**, 27 modules under `com.yem.hlm.backend.*`, each with `api/` (controllers+DTOs), `domain/` (JPA entities), `repo/`, `service/`. Consistent and navigable.
- **44 controllers**, **36 files with `@PreAuthorize`**, **33 services calling `requireSocieteId()`** — the multi-tenant guard is pervasive, not spotty.
- Cross-cutting concerns are real, not aspirational: `RlsContextAspect`, `SocieteContextTaskDecorator` (ThreadLocal propagation across `@Async`), ShedLock distributed locks, Caffeine + Redis caching, OTel/Prometheus, Springdoc on all controllers.
- Event-driven seams already exist: `common/event/`, `societe/event/`, `SaleFinalizedEvent`, `EcheanceChangedEvent`, `KpiComputationService` — Spring `ApplicationEvent` based.

### 2.2 Findings

| # | Severity | Finding | Root cause | Impact | Fix | Effort |
|---|---|---|---|---|---|---|
| A1 | High | **No application/use-case layer.** Controllers call fat services directly; services mix orchestration, business rules, persistence, and mapping. `VenteService` (635 LOC), `DepositService` (480), `ReservationService` (438) are orchestration + rules + I/O in one. | Layered (not hexagonal) architecture; pragmatic for CRUD CRM. | Construction workflows (multi-step, stateful, long-running) will balloon these into god-services. | Introduce a thin application layer (commands/handlers) for the construction context; keep CRM as-is. | M (per module) |
| A2 | High | **No domain isolation / anti-corruption between contexts.** Modules import each other's JPA entities directly (e.g. dashboards reach into vente/reservation repos). | Shared persistence model, no module API contracts. | A construction module that reaches into `property`/`tranche` entities couples two release cadences and two teams. | Define module-facing interfaces (ports) for cross-context reads; construction talks to CRM via those, not entities. | M |
| A3 | Medium | **Dependency-inversion is partial.** Good examples exist (`MediaStorageService` interface with local+S3 impls, `EmailSender`/`SmsSender` noop defaults). But most services depend on concrete repos/entities. | Convenience. | Hard to unit-test rich logic without DB; Testcontainers-heavy. | Adopt ports for I/O-heavy construction services from day one. | S |
| A4 | Medium | **God services / large files.** `GlobalExceptionHandler` 997 LOC, `DashboardCockpitService` 743, `HomeDashboardService` 696, `VenteRepository` 820 (likely many `@Query` strings). | Accretion over 15 waves. | Maintenance friction, merge conflicts, cognitive load. | Split `GlobalExceptionHandler` per-domain (`@RestControllerAdvice(basePackages=…)`); split dashboard services by concern; extract repo queries to named views/projections. | M |
| A5 | Medium | **CQRS-readiness: low but seam present.** Dashboards already act as a read model (KPI snapshots, projections). No formal command/query split. | Not needed for CRM scale. | Construction reporting (progress %, earned value, cost rollups) is read-heavy and aggregate-heavy — will fight the write model. | Use KPI-snapshot pattern as the template: construction read models materialized via events. | M |
| A6 | Low | **Microservice readiness: deliberately and correctly low.** This is a *modular monolith* and should stay one. | By design. | None — extraction later is feasible because modules are clean. | Keep monolith; enforce module boundaries with ArchUnit tests. | S |

### 2.3 Frontend architecture
- **State management is immature.** `auth.store.ts` is a signal store **explicitly marked "Currently unused"** — auth state lives in `AuthService` + httpOnly cookie. Only `core/store/` has one file. No global store, no entity caching; each feature re-fetches.
- **Shared component strategy is thin:** `shared/` contains only `pickers/`. Design tokens exist (`design-tokens.css`, `styles.css` per Wave 11/12) and a "skeleton" primitive system, but there is **no component library** — buttons, tables, dialogs, form fields are re-implemented per feature (732-LOC `properties.component.html`, 621-LOC `contact-detail.component.html`).
- **Routing** is clean (standalone components, lazy routes, guards). Portal/CRM/superadmin shells are well-separated.

| # | Severity | Finding | Fix | Effort |
|---|---|---|---|---|
| F1 | High | No shared UI component library; markup duplicated across 700-LOC templates. | Build `@hlm/ui` lib (table, data-list, form-field, dialog, status-pill, KPI-card) before construction adds 15+ new screens. | L |
| F2 | High | No coherent state strategy; `AuthStore` dead code. Construction (Gantt, live site dashboards) needs reactive shared state. | Standardize on Angular signals + a lightweight signal-store pattern (NgRx SignalStore); delete or adopt `auth.store.ts`. | M |
| F3 | Medium | God templates (2037-LOC `home-dashboard.component.html`). | Decompose into presentational components. | M |

---

## 3. Security Assessment

**Overall: strong baseline — the best-developed non-functional area.** Benchmarked against OWASP Top 10 (2021) and ASVS L2.

### 3.1 Authentication
- JWT via Spring OAuth2 resource server (`JwtProvider`, shared encoder/decoder beans). TTL **3600s (1h)**, configurable.
- **CRM token in httpOnly `hlm_auth` cookie** (`CookieTokenHelper`): `HttpOnly` + `Secure` (default true) + `SameSite=Lax`. **No JS-readable token** — strong XSS-theft resistance. The frontend confirms: "On success the backend sets the httpOnly auth cookie — no token is stored in JS."
- **Portal**: separate `hlm_portal_auth` httpOnly cookie, magic-link (32-byte SecureRandom → SHA-256 stored, 48h TTL, one-time use). Sound.
- **Gap (A-AUTH-1, Medium):** **No refresh-token mechanism.** 1h TTL with no silent refresh = hard logout every hour, or pressure to lengthen TTL (worse). Add rotating refresh tokens (httpOnly cookie, server-side revocation list — `TokenRevocationIT` exists, so revocation infra is partly present).
- **OAuth readiness (Low):** resource-server stack is OAuth2-native; adding an external IdP (Keycloak/Entra) is low-friction. No SSO today.

### 3.2 Authorization
- **RBAC**: `ROLE_ADMIN/MANAGER/AGENT` (société), `SUPER_ADMIN` (platform), `ROLE_PORTAL` (buyer). `@EnableMethodSecurity` + `@PreAuthorize` on 36 files; class-level + method-level overrides documented.
- **Object-level / multi-tenant authz is the standout strength:** `requireSocieteId()` in 33 services **plus** PostgreSQL RLS phase 2 on all domain tables (defense-in-depth — even a missing service guard is caught at the DB). `CrossSocieteIsolationIT` validates it.
- **ABAC readiness (Medium gap for construction):** construction needs row-level, attribute-based rules (a subcontractor sees only their work packages; a site engineer sees only their site). Current RBAC is role-coarse. Plan an ABAC/permission layer (Spring Security `PermissionEvaluator` or a policy table) for the construction context.

### 3.3 API security
- **IDOR:** strongly mitigated by `WHERE societe_id=?` + RLS; cross-contact portal access returns 404 by design.
- **Mass assignment (Medium):** controllers are on explicit DTO contracts (good), but verify no entity is bound directly from request bodies in newer modules.
- **Rate limiting:** `bucket4j` + `RateLimiterService` (login + invitation 10/h). Good, but **coverage is narrow** — extend to magic-link request, password reset, and all write-heavy construction endpoints.
- **Validation:** `spring-boot-starter-validation`, `@StrongPassword`, completeness validators. Decent.

### 3.4 Frontend / Backend / Infra
- **CSP** is real and restrictive (`default-src 'self'`, `frame-ancestors 'none'`, `script-src 'self'`); `style-src 'unsafe-inline'` is the one relaxation (Angular requirement) — acceptable, note for ASVS.
- **CSRF disabled** (`csrf.disable()`). With **cookie-based auth** this is the one item to scrutinize. **SameSite=Lax mitigates the common cross-site POST/PUT/DELETE vector** (Lax does not send the cookie on cross-site sub-requests), so residual risk is **Low–Medium**, but defense-in-depth warrants either `SameSite=Strict` for mutations or a double-submit CSRF token. **Finding S-CSRF-1 (Medium).**
- **File upload (Medium):** `MediaTypeNotAllowedException` + `MediaTooLargeException` exist → type/size validation is present. **Verify**: content-type is validated against actual magic bytes (not just the client-sent header), filenames are sanitized (path traversal), and the new 3D GLB upload-URL flow validates server-side. Construction will add heavy document/photo uploads — harden now.
- **SSRF (Medium for construction):** S3 presigned URLs + future BIM/webhook integrations are SSRF-prone. No outbound-URL allowlist today.
- **Secrets:** env-var driven (`JWT_SECRET` `@NotBlank` fail-fast), `.env`/`.env.example`, `secret-scan.yml` regex audit, Snyk OSS+SAST. `.env` (8.9 KB) is present in the working tree — **confirm it is gitignored** and never committed. **Finding S-SECRET-1 (verify, potentially High).**
- **Logging:** `logstash-logback-encoder` structured logs + `SecurityAuditLogger`. Verify no token/PII in logs.

**Security score: 82/100.** Deductions: no refresh tokens, narrow rate-limit coverage, CSRF-disabled-with-cookies residual, file-upload deep-validation unverified, ABAC gap for construction.

**Risk matrix:** Critical 0 · High 1 (verify `.env` not committed) · Medium 6 · Low 4.

---

## 4. Database Assessment

### 4.1 Schema quality
- **74 additive Liquibase changesets**, strictly additive discipline (CLAUDE.md rule, next = 075). Excellent change governance.
- Consistent: `societe_id UUID NOT NULL` + `fk_<table>_societe` on every domain table; RLS policies; optimistic-lock `version` columns; reference counters (reservation, vente) with dedicated tables.
- Naming is consistent (snake_case, French domain terms). FKs and indexes are present and deliberately added (changeset 061 composite + partial task indexes; 062 KPI unique constraint).

### 4.2 Scalability / performance risks
- **N+1 risk (Medium):** JPA entity graph with cross-module reads (dashboards iterate ventes/echeances). `VenteRepository` 820 LOC suggests many hand-written queries (good — projections avoid N+1) but verify list endpoints use fetch joins/projections, not lazy walks.
- **Aggregate-on-read (Medium → High for construction):** dashboards compute aggregates live with Caffeine/Redis TTLs. Construction cost rollups and progress aggregation across a WBS tree are far heavier — **must** be materialized (the KPI-snapshot pattern is the right precedent).
- **No partitioning / archival strategy:** fine now; site daily-reports + inspection photos will grow fast. Plan table partitioning (by société or date) for high-volume construction tables.
- **RLS overhead:** RLS on every table adds a per-query predicate; acceptable, but load-test construction's hot paths.

### 4.3 Construction-module readiness (schema)
**Current schema supports NONE of the construction primitives.** Grep for `contractor|subcontractor|budget|procurement|milestone|wbs|inspection|hse|defect|snag` in backend returns **only false positives** (e.g. "budget" in dashboard CSS, "milestone" in commission). The existing `Project → Tranche → Immeuble → Property` hierarchy is a **sales/asset** hierarchy, not a **construction/work** hierarchy.

| Construction entity | Current state | Gap |
|---|---|---|
| Projects | ✅ `Project` exists (commercial framing) | Needs construction attributes (contract value, planned/actual dates, phases) |
| Buildings / Floors / Units | ✅ `Immeuble`/`Property` (+ floor fields) | Reusable as physical breakdown; needs link to work breakdown |
| Construction phases | ❌ | New `phase` entity + state machine |
| WBS / Tasks / Milestones | ❌ | New graph-shaped schema (self-referencing, predecessor links) |
| Budget / Cost / Commitments | ❌ | New financial schema (budget lines, commitments, actuals, change orders) — must be auditable |
| Contractors / Subcontractors | ❌ | New party schema (could extend `contact`, but procurement-grade) |
| Procurement / Materials | ❌ | New (PO, GRN, inventory) |
| Inspections / Defects / Snagging | ❌ | New + photo evidence |
| HSE / Incidents | ❌ | New |
| Resources (labor/equipment) | ❌ | New scheduling-linked schema |

**Recommendation:** the construction module gets its **own schema namespace** and changeset range (e.g. 100+), its own bounded context, and references CRM entities by ID through a port — not by FK across contexts where avoidable (or FK with a clear ownership rule). A future ERD is sketched in §11.

---

## 5. Code Quality Assessment

- **SOLID:** S/O partly violated by god-services (§A4); D partly (concrete deps). I/L generally fine.
- **DRY:** backend good (shared `ErrorResponse`/`ErrorCode`, base test classes). **Frontend poor** — template duplication.
- **KISS:** mostly good; the multi-tenant + RLS + async-decorator stack is necessarily complex but well-documented.
- **Tests:** 69 backend test files (mix unit + Testcontainers IT), Playwright E2E (`workers:1`), frontend Karma. CLAUDE.md documents ~290 passing. **Coverage is solid for CRM**; construction will need a much larger suite (state machines, scheduling, financial math).
- **Dead code:** `auth.store.ts` (admitted), legacy `CrossTenantAccessException` kept for compat. Minor.
- **Technical debt estimate:** ~**30–45 dev-days** to bring the *existing* platform to "ready to host a major new context" (module boundaries + ArchUnit + frontend component lib + state strategy + god-service splits). Construction module itself is a separate, much larger build.

**Code quality score: 74/100.** Maintainability index: **B (good, trending fragile at the dashboard/template hotspots).**

---

## 6. Performance Assessment

- **Backend:** caching layered (Caffeine local + Redis distributed, per-cache TTLs via `registerCustomCache`), KPI snapshots, ShedLock prevents duplicate scheduler work. Good. Risks: live-aggregate dashboards, potential N+1 on list endpoints, RLS predicate overhead.
- **Frontend:** lazy routes ✅. **Bundle risk:** `three@0.165` + Draco is heavy — confirm the 3D viewer is lazy-loaded only on its routes (CLAUDE.md says it is). `anyComponentStyle` budget was bumped to 20 KB — a smell of CSS bloat from duplicated styles. No virtual scrolling on long lists.
- **No evidence of load testing.** Construction's field-reporting + photo upload + Gantt will change the performance profile entirely.

**Performance score: 70/100.** Roadmap: (1) projection-based list endpoints, (2) materialized construction read models, (3) frontend component lib to shrink CSS/bundles, (4) virtual scroll + pagination everywhere, (5) load-test before construction GA.

---

## 7. UI/UX Assessment

- **Strengths:** cohesive design tokens (green brand unified, status palette), skeleton loaders, i18n (FR primary), three well-separated shells (CRM/portal/superadmin), recent design-system pass (Waves 11–12).
- **Weaknesses:** no component library → inconsistency risk as screens multiply; god templates; navigation/IA is CRM-shaped and will not absorb construction's depth (a Procore-class app needs project-scoped sub-navigation, not a flat sidebar).
- **Persona gaps for construction:** site managers need **mobile-first, offline-tolerant** field UIs (daily reports, photo capture, snag lists) — the current desktop CRM UX does not serve them. Executives need cross-project portfolio dashboards.

**Benchmark (UX maturity):** ahead of a typical in-house CRM; **well behind** Procore/Autodesk Construction Cloud on field UX, document management, and scheduling visualization; behind Salesforce/HubSpot on configurability and reporting builders; comparable to early Odoo modules.

**UX score: 66/100.** Quick wins: component library, decompose god templates, add project-scoped navigation shell, define a mobile/field UX track.

---

## 8. Real-Estate Domain Assessment

**Strong and largely complete for primary-market developer sales:**
- ✅ Lead/prospect management, source funnel, prospect→client lifecycle, auto-promotion.
- ✅ Sales pipeline with enforced state machine (`COMPROMIS→FINANCEMENT→ACTE_NOTARIE→LIVRE`, `ANNULE` terminal).
- ✅ Reservations (unique refs, expiry scheduler, cancellation reasons), deposits, contracts + PDF, payment schedules, commissions.
- ✅ Buyer portal (magic-link, read-only ventes/echeances/docs), 2D plan de commercialisation, 3D viewer.
- ✅ Moroccan-specific: SRU/reflexion delays, Law 09-08 GDPR, French vocabulary.

**Gaps / opportunities:**
- Secondary-market / rental / property-management lifecycle (leases, tenants, maintenance) — absent; the platform is sales-only.
- Investor & developer reporting (absorption already exists; extend to cash-flow forecasting, sales-velocity, price-evolution analytics).
- MENA/Africa: multi-currency, Arabic RTL i18n, local payment rails, escrow/notary integrations.
- CRM marketing automation (campaigns, lead scoring, web-to-lead) — minimal.

---

## 9. Construction Module Readiness (per-area, 0–100)

| Area | Capability now | Readiness | Gap & recommended architecture |
|---|---|---|---|
| Project management (WBS/milestones/tasks) | Commercial `Project`+`Tranche` only | **15** | New graph schema (self-ref tasks, predecessors), milestone entity, state machines. App-layer command handlers. |
| Site management (phases, progress, daily reports) | None | **10** | New `phase`, `daily_report` (high-volume, partition), progress-% rollup as read model. Mobile/offline UI. |
| Financials (budget/cost/commitments/procurement) | Commercial payments only | **15** | New auditable financial ledger: budget lines, commitments, actuals, change orders, POs. Double-entry rigor; event-sourced or append-only. |
| Resources (labor/equipment/materials) | None | **5** | New resource schema linked to scheduling + inventory (GRN, stock moves). |
| Quality (inspections/defects/snagging) | None | **10** | New inspection/checklist + defect lifecycle + photo evidence (media module reusable). |
| HSE (incidents/audits/compliance) | None | **5** | New incident + audit + compliance-register schema; regulatory reporting. |
| Reporting (progress KPIs, exec dashboards) | Commercial dashboards (reusable pattern) | **40** | Strongest transferable asset: KPI-snapshot + dashboard pattern extends to earned-value, S-curves. |
| Document management | Cross-entity `document` module exists | **45** | Reusable base; needs versioning, transmittals, approval workflows, drawing sets. |
| 3D / BIM readiness | Three.js viewer (GLB/Draco), mesh↔lot mapping | **35** | 3D viewer is real and valuable but is a **mesh viewer, not BIM**. No IFC parsing, no property/quantity extraction, no 4D (schedule-linked) or 5D (cost-linked). |
| Scheduling (Gantt/critical path) | None | **5** | The long-pole. Needs a CPM/PERT engine (build vs. integrate) + Gantt UI. |

**Aggregate Construction Readiness: 22/100.** Infrastructure (multi-tenant, security, storage, events, 3D) is ready to *host* construction; the *domain* is greenfield.

---

## 10. Competitive Benchmark

| Capability | HLM | Salesforce | HubSpot | Dynamics 365 | Procore | Autodesk ACC | Primavera P6 | Odoo | Yardi/Buildium |
|---|---|---|---|---|---|---|---|---|---|
| RE sales CRM | ●●●○ | ●●●● | ●●● | ●●●● | ○ | ○ | ○ | ●●● | ●● |
| Buyer portal | ●●● | ●● | ●● | ●● | ●●● | ●● | ○ | ●● | ●●● |
| Construction PM | ○ | ○ | ○ | ●● | ●●●● | ●●●● | ●●●● | ●● | ● |
| Scheduling/CPM | ○ | ○ | ○ | ● | ●●● | ●●● | ●●●● | ● | ○ |
| Cost/procurement | ○ | ● | ○ | ●●● | ●●●● | ●●● | ●● | ●●● | ●●● |
| BIM/3D | ●● (viewer) | ○ | ○ | ● | ●●● | ●●●● | ○ | ○ | ○ |
| Multi-tenant SaaS | ●●●● | ●●●● | ●●●● | ●●●● | ●●●● | ●●●● | ●● | ●●● | ●●● |
| Field/mobile | ○ | ●●● | ●● | ●●● | ●●●● | ●●●● | ●● | ●● | ●●● |
| MENA/RTL/local fit | ●●○ | ●● | ● | ●● | ● | ● | ● | ●● | ● |

**Differentiation opportunity:** *Unified developer platform* — sell → reserve → build → deliver → portal, in one société-isolated SaaS tuned for MENA/Africa. No incumbent owns "developer sells units **and** manages the construction that produces them" with a shared 3D model and a buyer portal. That is the wedge. Don't try to out-feature Procore on construction depth; win on **continuity from sales to handover** on one model.

---

## 11. Future Architecture Blueprint (target)

**Bounded contexts (DDD):**
1. **Sales & CRM** (existing) — contacts, ventes, reservations, deposits, commissions, portal.
2. **Asset / Inventory** — projects, tranches, immeubles, properties (the physical breakdown; shared reference).
3. **Construction / Delivery** (new) — WBS, schedule, phases, daily reports, quality, HSE, resources.
4. **Procurement & Cost** (new) — budgets, commitments, actuals, POs, change orders, contractors.
5. **Documents & Media** (existing, extend) — versioning, transmittals, drawing sets, BIM models.
6. **Platform** — société, users, auth, audit, notifications, outbox.

**Integration strategy:** keep the **modular monolith**; enforce boundaries with **ArchUnit** + module-facing port interfaces (no cross-context entity imports). Cross-context communication via **domain events** (extend the existing `ApplicationEvent` seam; consider an outbox-backed event bus — the `outbox` module is a ready foundation) and **read-only query ports**.

**Per-context patterns:**
- Construction & Cost: **light CQRS + event-carried read models** (extend KPI-snapshot pattern to earned-value/S-curves/cost rollups). Financial ledger **append-only/auditable**.
- Scheduling: isolate the CPM engine behind a port (build-vs-buy decision; see §13).

**Cross-cutting:**
- **API:** REST now; add a versioned `/api/v1` prefix before construction (you'll break contracts). Consider GraphQL/BFF for the heavy construction dashboards.
- **Events:** outbox-backed, at-least-once, idempotent consumers.
- **Scalability:** materialized read models, table partitioning for daily-reports/inspections/photos, keep RLS, load-test.
- **Multi-tenant:** unchanged (société + RLS) — already the platform's crown jewel; ensure every new construction table carries `societe_id NOT NULL` + RLS + FK (the §M checklist).
- **DR:** define RPO/RTO, automate Postgres PITR backups + object-storage versioning/replication (R2), restore drills. **Not evidenced today — add before construction GA.**
- **Mobile:** a field app (PWA or native) is required for construction; design the API as mobile-first/offline-tolerant for the new context.

---

## 12. Documentation Updates (required before build)
Existing docs are good (`docs/01-…10-`, ADRs, runbook, prior AUDIT_REPORT). Add/update:
- **Architecture:** add the bounded-context map + module-boundary rules + ArchUnit policy (new ADR).
- **Construction module:** Business Solution Doc, Functional Spec, Technical Spec, ERD, process flows (WBS, daily report, inspection, change order), use cases. *None exist.*
- **Security:** ABAC policy model for construction; CSRF decision record; file-upload hardening standard; secrets-handling (confirm `.env` excluded).
- **API:** versioning policy (`/api/v1`), event catalog.
- **DR runbook:** backup/restore/PITR procedures + drill log.

---

## 13. Missing Skills & Technologies

| Capability | Need | Build vs. Buy | Recommended tech |
|---|---|---|---|
| DDD / bounded contexts | High | Build (upskill) | ArchUnit, context mapping |
| CQRS / event-carried read models | High | Build (pattern exists) | Spring events + outbox |
| Event sourcing (financial ledger) | Medium | Build selectively | Append-only tables; avoid full ES unless needed |
| Scheduling / CPM (critical path) | **Critical / long-pole** | **Buy/integrate first** | Frappe Gantt / DHTMLX / Bryntum (UI) + a CPM lib or service; Primavera/MS-Project import (XER/MPP) |
| Cost management / project controls | High | Build domain, hire SME | Earned-value (EVM) model |
| BIM / IFC | Medium (roadmap) | Buy/integrate | IFC.js / web-ifc, Autodesk APS (Forge) for true BIM; xeokit for large models |
| 3D viz | Have (Three.js) | Have, extend | xeokit/Cesium if scale/geo grows |
| GIS | Low–Medium | Integrate later | PostGIS, MapLibre/Cesium |
| Mobile/offline field app | High | Build | Angular PWA or Flutter; offline sync (PouchDB/SQLite) |
| AI features | Medium | Buy | Claude API (already in-house Anthropic stack) for doc extraction, RFI drafting, progress narration |

**Skill-gap priorities:** (1) DDD + module boundaries, (2) project controls / construction domain SME (hire or contract), (3) scheduling engine integration, (4) mobile/offline. **Training:** DDD (Vaughn Vernon), CQRS/event-driven, construction project controls (AACE/PMI-SP), BIM/IFC fundamentals.

---

## 14. Technical Debt & Risk Registers (top items)

**Technical Debt Register (top 10):** A1 app-layer absence · A2 cross-context entity coupling · A4 god-services (GlobalExceptionHandler 997, dashboards 700+) · F1 no UI component library · F2 dead `AuthStore` / no state strategy · F3 god templates (2037-LOC) · live-aggregate dashboards (no read models beyond KPI) · narrow rate-limit coverage · no API versioning · no load-test baseline.

**Risk Register (top 10):**
| Risk | Sev | Likelihood | Mitigation |
|---|---|---|---|
| `.env` committed / secret leak | High | Verify | Confirm gitignore; rotate if ever committed; secret-scan enforce |
| Construction bolted onto CRM CRUD pattern → big ball of mud | High | High | Bounded context + app layer + ArchUnit *first* |
| Scheduling engine underestimated (long-pole) | High | High | Build-vs-buy decision in discovery; integrate, don't build CPM |
| No DR/backup drills evidenced | High | Medium | PITR + object versioning + restore drill |
| CSRF-disabled-with-cookies | Medium | Low | SameSite=Strict for mutations or double-submit token |
| File-upload deep validation gap | Medium | Medium | Magic-byte + filename sanitization + AV scan |
| Aggregate dashboards won't scale to construction | Medium | High | Materialized read models |
| No refresh tokens → poor session UX/pressure to widen TTL | Medium | High | Rotating refresh cookie |
| ABAC absent for subcontractor/site-scoped access | Medium | High | Policy layer before construction GA |
| Frontend bundle/CSS bloat | Low | Medium | Component lib, budgets |

---

## 15. Prioritized Roadmap — TOP 50 ACTIONS BEFORE BUILDING THE CONSTRUCTION MODULE
*(ordered by business value × enabling-impact; P0 = do first / blocking)*

**P0 — Foundations & risk retirement (do before any construction code)**
1. Confirm `.env` is gitignored and never committed; rotate `JWT_SECRET`/DB creds if it ever was. Enable `SECRET_SCAN_ENFORCE=true`.
2. Define the **construction bounded context** + dedicated Liquibase changeset range (100+) + schema namespace.
3. Add **ArchUnit** tests enforcing module boundaries (no cross-context entity imports).
4. Introduce a **light application/command layer** template (command + handler) for the construction context.
5. Define **module-facing port interfaces** for CRM/Asset reads the construction module needs (property/tranche/contact by ID).
6. **Build-vs-buy decision for the scheduling/CPM engine** (the long-pole) — spike Bryntum/DHTMLX/Frappe + a CPM lib.
7. Stand up **DR**: automated Postgres PITR backups + object-storage versioning + a documented restore drill.
8. Add **API versioning** (`/api/v1`) before contracts proliferate.
9. Hire/contract a **construction project-controls SME** to validate the domain model.
10. Write the **Construction Business Solution + Functional Spec + ERD** (none exist).

**P1 — Platform hardening that construction will lean on**
11. Add **rotating refresh tokens** (httpOnly cookie + revocation list; revocation infra partly exists).
12. Decide **CSRF posture** (SameSite=Strict for mutations or double-submit token) and record an ADR.
13. **Harden file uploads** (magic-byte validation, filename sanitization, size caps per type, AV scan hook) — construction is document/photo-heavy.
14. Add an **ABAC/policy layer** (Spring `PermissionEvaluator` or policy table) for site/subcontractor-scoped access.
15. Extend **rate limiting** to magic-link, password reset, and write-heavy endpoints.
16. Establish the **event-carried read-model pattern** as the official approach (extend KPI-snapshot) for construction reporting.
17. Make the **outbox** the backbone of an at-least-once **domain event bus**; idempotent consumers.
18. Split **`GlobalExceptionHandler`** (997 LOC) into per-domain advices.
19. Split **dashboard god-services** (`DashboardCockpitService` 743, `HomeDashboardService` 696) by concern.
20. Convert hot **list endpoints to projections/fetch-joins**; audit for N+1.
21. Add **`societe_id NOT NULL` + RLS + FK** checklist enforcement (lint/ArchUnit) for all new tables.
22. Establish a **load-test baseline** (k6/Gatling) on current hot paths before adding construction load.
23. Add **table-partitioning strategy** for future high-volume tables (daily reports, inspections, photos).
24. Verify **no PII/token in logs**; document logging-security standard.
25. Add **OpenAPI contract tests** so the new module's API is verified against spec.

**P2 — Frontend readiness (construction adds 15+ screens)**
26. Build a **shared component library** (`@hlm/ui`): table, data-list, form-field, dialog, status-pill, KPI-card, file-upload.
27. Choose & implement a **single state strategy** (signals + NgRx SignalStore); delete or adopt dead `auth.store.ts`.
28. Add a **project-scoped navigation shell** (construction needs deep per-project sub-nav, not a flat sidebar).
29. Decompose **god templates** (2037-LOC home-dashboard, 700+ property/contact) into presentational components.
30. Define a **mobile/offline field-UX track** (PWA): daily reports, photo capture, snag lists.
31. Add **virtual scrolling + server pagination** to all long lists.
32. Establish **bundle budgets** + confirm Three.js/3D is lazy per-route only.
33. Add **Arabic RTL + multi-currency** groundwork (MENA differentiation).
34. Component-level **Storybook/visual regression** for the new library.
35. Accessibility pass (WCAG AA) on shared components.

**P3 — Construction domain MVP scaffolding (after P0–P2 enable it)**
36. Model **Construction Project / Phase** entities + state machines (reuse Tranche state-machine pattern).
37. Model **WBS / Tasks / Milestones** (self-referencing graph, predecessor links).
38. Integrate the chosen **Gantt/scheduling** component + CPM engine behind a port.
39. Model the **financial ledger** (budget lines, commitments, actuals, change orders) — append-only/auditable.
40. Model **Contractors/Subcontractors** (party schema; extend `contact` or new procurement-grade entity).
41. Model **Procurement** (PO, GRN, materials/inventory, stock moves).
42. Model **Daily Site Reports** (high-volume, partitioned, photo-linked) + progress-% rollup read model.
43. Model **Quality** (inspections/checklists, defect/snag lifecycle) reusing the media module.
44. Model **HSE** (incidents, audits, compliance register, regulatory reports).
45. Model **Resources** (labor/equipment) linked to scheduling.
46. Build **construction read models**: earned value (EVM), S-curves, cost-vs-budget, progress KPIs (extend KPI-snapshot).
47. Wire **construction events** to the buyer **portal** (delivery progress visible to buyers — the differentiation wedge).
48. Extend the **3D model** to schedule-link (4D) and cost-link (5D) using the existing mesh↔lot mapping as the join.
49. **BIM/IFC roadmap spike** (web-ifc / Autodesk APS) — readiness only, not MVP.
50. Build the **construction test suite** (state machines, scheduling math, financial rollups, cross-context isolation IT) to CRM's standard.

---

## 16. Overall Platform Score (justification)

| Dimension | Score | Rationale |
|---|---|---|
| **Architecture** | **72** | Clean modular monolith, pervasive multi-tenant guard, real cross-cutting infra; −for no app layer, cross-context entity coupling, god-services. |
| **Security** | **82** | httpOnly-cookie JWT + RLS defense-in-depth + CSP + rate limiting + secret scanning; −for no refresh tokens, CSRF-disabled-with-cookies residual, file-upload deep-validation/ABAC gaps. |
| **Performance** | **70** | Good caching + KPI snapshots + ShedLock; −for live-aggregate dashboards, unproven N+1 hygiene, no load-test baseline. |
| **Maintainability** | **74** | Disciplined layering, additive migrations, solid tests; −for god-services/templates, frontend duplication, dead code. |
| **Scalability** | **68** | Multi-tenant + RLS + Redis + distributed locks; −for no partitioning/read-model strategy beyond KPI, no DR evidence. |
| **UI/UX** | **66** | Cohesive tokens, skeletons, i18n, three shells; −for no component library, god templates, CRM-shaped IA, no field/mobile UX. |
| **Construction Readiness** | **22** | Infra ready to *host*; domain is greenfield — zero construction primitives, no scheduling, no cost/procurement, viewer ≠ BIM. |

### OVERALL PLATFORM MATURITY: **68/100**
A strong, well-disciplined real-estate CRM SaaS with an excellent multi-tenant security core — **production-grade for its current domain**, and a **sound foundation to host construction**, but requiring deliberate structural investment (bounded context, app layer, frontend platform, DR, scheduling decision) **before** construction code is written, or the new domain's complexity will overwhelm the CRM's CRUD-shaped patterns.
