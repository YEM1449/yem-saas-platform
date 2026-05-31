# HLM CRM — Go-Live, Compliance, Architecture, Security, Reporting & Construction Readiness Assessment

**Date:** 2026-05-31
**Bar applied:** *Calibrated to actual maturity* — "is this focused real-estate CRM SaaS ready to launch for property-developer clients in Morocco/MENA," **not** Fortune-500/hyperscale. Recommendations are justified by business/technical value; over-engineering is explicitly avoided.
**Method:** Direct codebase inspection (541 backend `.java`, 169 frontend `.ts`, 74 Liquibase changesets, 69 test files) + prior `docs/CONSTRUCTION_READINESS_AUDIT_2026-05.md` and `docs/AUDIT_REPORT.md`.

**Category legend:** ① Go-Live Blocker · ② Go-Live Recommended · ③ Post-Go-Live · ④ Construction Prerequisite · ⑤ Long-Term Strategic.

---

## 0. Maturity Calibration (read this first)

HLM is a **mature, single-product real-estate sales CRM** built by a disciplined team over 15 delivery waves. On a *go-live* bar it is **much closer to ready than an enterprise bar suggests**. The first audit (enterprise/construction-absorption bar) scored it 68; recalibrated to "ready to launch this CRM for its actual users," it is **73 and READY WITH CONDITIONS**. Crucially, two areas I under-credited on first pass are in fact **strengths**: **legal/compliance** (a real GDPR + Law 09-08 implementation, not a checkbox) and **reporting** (a deep, multi-persona dashboard layer with live SSE + exports). The go-live blockers that remain are **operational and legal-process items, not code defects**.

---

## 1. Business & Functional Assessment

### Coverage matrix (① = present, ◐ = partial, ✗ = absent)

| Capability | State | Notes |
|---|---|---|
| **CRM** — Lead/prospect mgmt | ① | Source funnel, prospect→client lifecycle, auto-promotion |
| Contact mgmt | ① | Rich `Contact` (consent, processing basis, completeness validation) |
| Opportunity / pipeline | ① | `Vente` state machine COMPROMIS→FINANCEMENT→ACTE_NOTARIE→LIVRE, ANNULE terminal |
| Activities / follow-up | ◐ | Tasks + due-now notifications; no full activity timeline/sequences |
| Customer follow-up | ◐ | Reminders + portal; no campaign/sequence automation |
| **Real Estate** — Property inventory | ① | 9 property types, Project→Tranche→Immeuble→Property |
| Unit management | ① | Lifecycle status machine, 2D plan, 3D viewer |
| Reservations | ① | Unique refs, expiry scheduler, cancellation reasons |
| Contracts | ① | DRAFT→SIGNED→CANCELED, immutable buyer snapshot, PDF, template versioning |
| Customer mgmt | ① | Contacts + buyer portal (magic-link) |
| Property lifecycle | ① | Status transitions wired to sale events |
| Deposits / payment schedules / commissions | ① | Full v2 payments, echeances, commission rules |
| **Gaps** — Rental / property-management (leases, tenants, maintenance) | ✗ | Platform is **primary-market sales only** |
| Customer-service / ticketing / SLA | ✗ | No case/ticket module |
| Marketing automation (campaigns, lead scoring, web-to-lead) | ✗ | Minimal |

**Functional Coverage Score: 78/100** — complete for primary-market developer sales (the actual target); gaps (rental PM, ticketing, marketing automation) are **out of the launch scope**, so they are ③/⑤, not blockers.

**Workflow inconsistency to fix (②):** `SaleContractStatus` carries an `[OPEN POINT]` comment — SIGNED→CANCELED rescission RBAC is unresolved. Decide and enforce (ADMIN/MANAGER only) before go-live.

---

## 2. Architecture Assessment

Modular monolith, 27 modules, consistent `api/domain/repo/service` layering, pervasive `requireSocieteId()` (33 services) + RLS. Good DI examples (`MediaStorageService`, `EmailSender`). **For the CRM's scale this architecture is appropriately sized — not under- nor over-engineered.**

- **Architecture Score: 76/100**
- **Maintainability Score: 74/100** (god-services: `GlobalExceptionHandler` 997, `DashboardCockpitService` 743, `HomeDashboardService` 696; god-templates 2037 LOC — all ③)
- **Scalability Score: 70/100** (multi-tenant + Redis + ShedLock good; live-aggregate dashboards + no partitioning are ④)

**Frontend:** clean routing/standalone/lazy + design tokens, but **no shared component library** (only `shared/pickers/`) and **dead `auth.store.ts`** / no state strategy — ② for go-live polish, ④ before construction's 15+ screens.

---

## 3. Security Assessment

**The strongest non-functional area.** Benchmarked OWASP Top 10 (2021) / ASVS L2.

| Control | State | Cat |
|---|---|---|
| JWT in **httpOnly `hlm_auth` cookie** (HttpOnly+Secure+SameSite=Lax); no JS-readable token | ① strong | — |
| Multi-tenant authz: `requireSocieteId()` **+ PostgreSQL RLS phase 2** (defense-in-depth) + nil-UUID system bypass; `CrossSocieteIsolationIT` | ① strong | — |
| RBAC `@EnableMethodSecurity` + `@PreAuthorize` (36 files); SUPER_ADMIN path isolation | ① | — |
| CSP (`default-src 'self'`, `frame-ancestors 'none'`), HSTS (TLS-gated), X-CTO, Referrer/Permissions-Policy | ① | — |
| Rate limiting (bucket4j): login + invitation | ◐ narrow | ② extend to magic-link/reset |
| Secret scanning (`secret-scan.yml`) + Snyk OSS+SAST | ① | — |
| **No refresh tokens** (1h hard logout) | gap | ② |
| **CSRF disabled with cookie auth** (SameSite=Lax mitigates cross-site mutations) | residual | ② SameSite=Strict-for-mutations or double-submit |
| **File-upload deep validation** (magic-byte, filename sanitization, AV) unverified | gap | ② |
| **`.env` (8.9 KB) present in working tree** — confirm gitignored / never committed; rotate if it was | **risk** | **①** |
| ABAC (subcontractor/site-scoped) | absent | ④ |

**Security Score: 84/100.** Only **one ① blocker**: verify/rotate secrets (`S1`). Everything else is ②/④.

---

## 4. Legal & Regulatory Compliance — **stronger than expected**

This is a **real implementation**, not stubs. Evidence: dedicated `gdpr/` module (852 LOC) + compliance modeling in `contact/`.

| Requirement | Implementation | State |
|---|---|---|
| **Consent management** (Art. 6/7) | `Contact.ConsentMethod` + `ProcessingBasis` enums; `CONSENT_CHANGED` audited | ① modeled |
| **Right of access / portability** (Art. 15/20) | `GdprService.exportContact` → `DataExportBuilder` | ① |
| **Rectification** (Art. 16) | `getRectifyView` + `PATCH /api/contacts/{id}` | ① |
| **Erasure** (Art. 17) | `AnonymizationService`; **blocked when SIGNED contracts exist** (legal-basis retention) — sophisticated | ① |
| **Retention** (Art. 5(1)(e) / Law 09-08 Art. 4) | `DataRetentionScheduler` daily 02:00, 1825-day default window, auto-anonymize | ① |
| **Record of processing** (Art. 30) | `ProcessingRegisterService` + `PrivacyNoticeLoader` | ① |
| **Auditability** | `CommercialAuditEvent` + `AuditEventType` covers **financial, commercial, GDPR, AND admin/user actions** (DEPOSIT/CONTRACT/PAYMENT/RESERVATION/USER_ROLE_CHANGED/USER_REMOVED/CONSENT_CHANGED/CONTACT_ANONYMIZED), each in `REQUIRES_NEW` tx; separate `SecurityAuditLogger` | ① comprehensive |
| **Contract traceability** | DRAFT→SIGNED→CANCELED, immutable buyer snapshot at SIGNED, `signedAt`, template `@Version` | ① |

**Gaps:**
- **CNDP declaration / registration** — an *organizational/legal* obligation (declare processing to Morocco's CNDP before production). No artifact in repo → **① legal-process blocker** (file the declaration; the technical controls to back it already exist).
- **E-signature** — SIGNED is an internal status flip; **no qualified e-signature integration** (DocuSign/Yousign/eIDAS-equivalent). For Moroccan legal validity, either integrate or **document a wet-signature/scan-back process** → **② (bridgeable at launch)**.
- Confirm a **published privacy notice + cookie/consent UI** is wired on the frontend (loader exists backend-side) → ②.

**Legal Compliance Score: 80/100** — well above typical SaaS at this stage. Blocker is paperwork (CNDP), not code.

---

## 5. Database Assessment

74 additive changesets, strict additive discipline, `societe_id NOT NULL` + FK + RLS + optimistic `version` everywhere, deliberate indexes (061 composite/partial, 062 unique), reference-counter tables. **Quality: high.**

- **Database Quality Score: 80/100**
- **Future readiness:** physical hierarchy (Project/Tranche/Immeuble/Property) is reusable as a construction *physical breakdown*, but **work breakdown, cost, procurement, resources, scheduling = absent** → ④. Plan a dedicated changeset range (100+) and namespace for construction.
- Risks: live-aggregate reads, no partitioning for future high-volume tables → ④.

---

## 6. Code Quality

SOLID mostly honored; god-services/templates and frontend template duplication are the debt. Dead code (`auth.store.ts`, legacy `CrossTenantAccessException`). Tests solid for CRM (69 files, ~290 passing per CLAUDE.md). **Code Quality Score: 74/100.** Refactors are ③ (post-launch), except module-boundary work which is ④.

---

## 7. Performance

Caffeine+Redis, KPI snapshots, ShedLock, lazy 3D. Risks: live-aggregate dashboards, N+1 hygiene on list endpoints unverified, no load-test baseline, CSS-budget bloat (20 KB). **Performance Score: 70/100.** ② Run a basic load smoke-test on hot paths before launch; ③ projections/read-models; ④ materialized construction read models.

---

## 8. UI/UX

Cohesive tokens, skeletons, i18n (FR), three well-separated shells, recent design-system pass. Weak: no component library, god templates, CRM-shaped IA, no mobile/field UX. **UX Score: 68/100 · UI Score: 70/100.** Benchmark: ahead of typical in-house CRM, behind Salesforce/HubSpot on configurability/report-builders. ② decompose top god-templates + ship a minimal component lib; ④ project-scoped nav + field/mobile track for construction.

---

## 9. Dashboards & Reporting — **a genuine strength**

Far deeper than first credited. Evidence (`dashboard/api/`):
- **Executive/owner:** Home dashboard + **`/shareholder`** + **`/project-director`** views; cancellation rate, avg ticket, conversion, collections, top-5 agent leaderboard.
- **Commercial cockpit:** `/funnel`, `/forecast`, `/agents-performance`, `/inventory-intelligence`, `/discount-analytics`, `/pipeline-analysis`, `/sales-intelligence`, `/insights`, `/alerts`.
- **Receivables** (aging buckets), **KPI by-project / by-immeuble / by-tranche** (materialized snapshots — single source of truth via `KpiComputationService` + events).
- **Live updates:** SSE `/events` stream. **Exports:** PDF + CSV (ventes, agents).

Coverage vs. the requested KPI set: pipeline value ①, reservation/lead conversion ①, revenue forecast ①, inventory status ①, collections/outstanding ①. **Occupancy/SLA** ✗ (out of sales scope). Missing: historical *trend* charting depth and a self-service report builder (⑤).

**Dashboard Maturity: 78/100 · Reporting Maturity: 76/100.** KPI consistency is well-handled via the snapshot/event pattern — this is also the correct foundation for construction EVM/S-curves (④ reuse).

---

## 10. Go-Live Readiness

**Operational:** prod compose with `restart: always` + actuator `health,info,metrics,prometheus,loggers` exposed (health/info public; rest auth-gated), TLS-gated HSTS, OTel/Prometheus, structured logs, CI (backend/frontend/e2e/docker/snyk/secret-scan). **Gaps:** **no automated DB backup service / restore drill** in prod compose (① — data-loss risk), readiness probe nuances handled (Redis/mail gated). Confirm `COOKIE_SECURE=true` + TLS termination in prod.

### GO-LIVE DECISION: **READY FOR GO-LIVE WITH CONDITIONS**

**Go-Live Readiness Score: 72/100.** The product, security, compliance, and reporting are launch-grade. The conditions are a short, finite set of **operational + legal-process** items (§14 Phase A), none requiring deep engineering:

**Conditions (must clear before flipping prod):**
1. ① Verify `.env` never committed; rotate `JWT_SECRET`/DB creds if it was; enable `SECRET_SCAN_ENFORCE`.
2. ① Automated PostgreSQL backups (PITR or nightly dump) + one tested restore drill.
3. ① File the **CNDP declaration** (Law 09-08) for personal-data processing.
4. ① Confirm prod TLS + `COOKIE_SECURE=true` + exact `CORS_ALLOWED_ORIGINS`.
5. ② E-signature decision (integrate or documented wet-signature process) + resolve contract-rescission RBAC `[OPEN POINT]`.
6. ② Basic load smoke-test on dashboard/list hot paths; verify file-upload deep validation.

---

## 11. Construction Module Readiness

Infra ready to *host* (multi-tenant, RLS, storage, events, 3D, KPI-snapshot reporting pattern, document module). Domain greenfield: WBS, scheduling/CPM, cost/budget/procurement, resources, daily reports, quality, HSE all absent (grep returns only false positives). Physical hierarchy + document + media + KPI patterns are **reusable foundations**.

**Construction Readiness Score: 24/100.** Recommended architecture: dedicated **bounded context** (own schema/changeset range, module-facing ports, no cross-context entity imports, ArchUnit-enforced), light **application/command layer**, **event-carried read models** (extend KPI-snapshot to EVM/S-curves), and a **build-vs-buy scheduling engine** decision (the long-pole). Full detail in `docs/CONSTRUCTION_READINESS_AUDIT_2026-05.md` §9–11.

---

## 12. Benchmarking (position)

Differentiation wedge: **unified sell→reserve→build→deliver→portal on one société-isolated, MENA-tuned model.** Strong vs. peers on RE-sales + buyer portal + multi-tenant + compliance; behind Procore/Autodesk on construction depth/field UX (expected — that's the next module); behind Salesforce/HubSpot on configurability/report-builders (⑤). Don't out-feature Procore; win on **continuity from sales to handover**.

---

## 13. Documentation Review

Good base (`docs/01-…10-`, ADRs, runbook, two audit reports). **Add before launch:** DR/backup runbook (①), CNDP/privacy compliance pack (①), CSRF & file-upload security ADRs (②). **Before construction (④):** bounded-context map + ArchUnit policy ADR, Construction Business Solution + Functional Spec + ERD + process flows (none exist), API-versioning policy, event catalog.

---

## 14. Final Roadmap

### Phase A — Before Go-Live
- **Critical (①):** rotate/verify secrets · automated DB backups + restore drill · CNDP declaration · prod TLS + Secure cookie + CORS · contract-rescission RBAC decision.
- **High (②):** e-signature decision/process · file-upload hardening · extend rate limiting (magic-link/reset) · CSRF posture (Strict/double-submit) · basic load smoke-test · privacy notice/consent UI wired · DR + compliance runbook docs.
- **Medium:** decompose top 2 god-templates · split `GlobalExceptionHandler` · monitoring dashboards (Grafana) provisioned.
- **Low:** delete dead `auth.store.ts` · README/runbook polish.

### Phase B — First 60 Days After Go-Live
- **Critical:** monitor RLS/perf under real load; finalize refresh-token rotation (UX pain emerges fast at 1h TTL).
- **High:** projection-based list endpoints + N+1 audit · shared UI component library (start) · single state strategy.
- **Medium:** split dashboard god-services · virtual scroll/pagination · trend-chart depth in reporting.
- **Low:** accessibility (WCAG AA) pass · bundle budgets.

### Phase C — Construction Preparation (④)
- **Critical:** bounded-context + schema namespace (changeset 100+) · ArchUnit module boundaries · application/command layer template · scheduling engine build-vs-buy spike.
- **High:** module-facing ports for CRM reads · event-carried read-model pattern formalized (outbox-backed bus) · ABAC/policy layer · Construction Business Solution + Functional Spec + ERD · construction project-controls SME.
- **Medium:** API versioning (`/api/v1`) · table-partitioning strategy · project-scoped nav shell · mobile/field UX track.
- **Low:** BIM/IFC roadmap spike.

### Phase D — Construction Module Kickoff (prerequisites)
All Phase-C Critical+High complete; ERD + functional spec signed off by SME; scheduling decision made; read-model + event bus + ABAC in place; component library + state strategy shipped; DR proven under load.

---

## FINAL EXECUTIVE SCORES (maturity-calibrated)

| Dimension | Score |
|---|---|
| Functional Coverage | **78** |
| Architecture | **76** |
| Security | **84** |
| Compliance | **80** |
| Database | **80** |
| Code Quality | **74** |
| Performance | **70** |
| UI/UX | **68** |
| Reporting & Dashboards | **77** |
| Go-Live Readiness | **72** |
| Construction Readiness | **24** |
| **Overall Platform Maturity** | **73** |

*(Higher than the first audit's 68 because this applies the correct go-live bar and credits the compliance + reporting strengths the enterprise-bar pass missed.)*

---

## TOP 25 GO-LIVE ACTIONS (business impact × risk reduction × priority)

1. ① Verify `.env` not in git history; rotate `JWT_SECRET`/DB creds; enable `SECRET_SCAN_ENFORCE`.
2. ① Automated PostgreSQL backups (nightly dump or PITR) + **one tested restore**.
3. ① File CNDP declaration (Law 09-08) for personal-data processing.
4. ① Confirm prod TLS termination + `COOKIE_SECURE=true` + exact `CORS_ALLOWED_ORIGINS`.
5. ① Resolve contract SIGNED→CANCELED rescission RBAC (`[OPEN POINT]`) → ADMIN/MANAGER only.
6. ② E-signature: integrate (Yousign/DocuSign) **or** document wet-signature/scan-back process.
7. ② Harden file uploads: magic-byte type check, filename sanitization, per-type size caps, AV hook.
8. ② Extend rate limiting to magic-link request + password reset.
9. ② CSRF posture: SameSite=Strict for mutations or double-submit token (ADR).
10. ② Wire/publish privacy notice + cookie/consent UI (backend loader exists).
11. ② Basic load smoke-test (k6/Gatling) on dashboard + list hot paths.
12. ② Provision Grafana dashboards + alerts on the existing Prometheus metrics.
13. ② Write DR/backup runbook + compliance (CNDP/GDPR) pack.
14. ② Verify no PII/token in structured logs.
15. ③ Add refresh-token rotation (1h TTL UX pain) — schedule early in Phase B.
16. ③ Decompose top 2 god-templates (home-dashboard 2037 LOC, properties 732).
17. ③ Split `GlobalExceptionHandler` (997 LOC) into per-domain advices.
18. ③ Projection/fetch-join audit on list endpoints (N+1).
19. ③ Delete dead `auth.store.ts` or adopt it as the state baseline.
20. ② Confirm actuator `loggers`/`prometheus` endpoints are auth-gated in prod.
21. ② Smoke-test full prod docker-compose (`--wait`) on a clean host.
22. ③ Add historical trend charts to executive dashboards.
23. ② Add `assigneeId`/ownership checks review on any newest-module endpoints (mass-assignment sweep).
24. ③ Accessibility quick pass on login/portal/critical flows.
25. ② Document on-call/incident-response basics (runbook stub exists).

## TOP 25 CONSTRUCTION PREPARATION ACTIONS (strategic value × dependency order)

1. ④ Establish construction **bounded context** + dedicated schema/changeset range (100+).
2. ④ **Build-vs-buy scheduling/CPM engine** spike (long-pole) — recommend integrate (Bryntum/DHTMLX + CPM lib).
3. ④ Add **ArchUnit** module-boundary tests (no cross-context entity imports).
4. ④ Introduce **application/command layer** template for construction workflows.
5. ④ Define **module-facing ports** for CRM/Asset reads (property/tranche/contact by ID).
6. ④ Formalize **event-carried read models** (outbox-backed bus; extend KPI-snapshot).
7. ④ Hire/contract a **construction project-controls SME**.
8. ④ Author **Construction Business Solution + Functional Spec + ERD + process flows**.
9. ④ Add **ABAC/policy layer** (site/subcontractor-scoped access).
10. ④ **Shared UI component library** (`@hlm/ui`) before 15+ new screens.
11. ④ Single **frontend state strategy** (signals + signal-store).
12. ④ **Project-scoped navigation shell** (deep per-project sub-nav).
13. ④ Model **Construction Project / Phase** entities + state machines (reuse Tranche pattern).
14. ④ Model **WBS / Tasks / Milestones** (self-referencing graph + predecessors).
15. ④ Model **financial ledger** (budget/commitments/actuals/change-orders) — append-only/auditable.
16. ④ Model **Contractors/Subcontractors** (procurement-grade party schema).
17. ④ Model **Procurement** (PO/GRN/materials/inventory/stock moves).
18. ④ Model **Daily Site Reports** (high-volume, partitioned, photo-linked) + progress rollup.
19. ④ Model **Quality** (inspections/checklists, defect/snag lifecycle) reusing media module.
20. ④ Model **HSE** (incidents/audits/compliance register/regulatory reports).
21. ④ Model **Resources** (labor/equipment) linked to scheduling.
22. ④ Build **construction read models** (EVM, S-curves, cost-vs-budget, progress KPIs).
23. ④ Add **API versioning** (`/api/v1`) before contracts proliferate.
24. ④ **Mobile/offline field UX** track (PWA) for daily reports/snagging/photos.
25. ⑤ Wire construction delivery progress to the **buyer portal** (the differentiation wedge); 4D/5D on the existing 3D mesh↔lot mapping; BIM/IFC roadmap spike.
