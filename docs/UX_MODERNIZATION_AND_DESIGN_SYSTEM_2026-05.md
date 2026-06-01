# HLM CRM — UX Modernization, Design-System Industrialization & Construction-Ready Experience

**Date:** 2026-06-01
**Mode:** Audit **+ direct implementation** (a working component library was built, compiled, and wired into a real screen — see §3/§10).

---

## 0. What was implemented in this pass (not just recommended)

A real, compiling **shared component library** at [hlm-frontend/src/app/shared/ui/](../hlm-frontend/src/app/shared/ui/), wrapping the existing design-token / global-CSS system so it is visually identical today and adoptable incrementally:

| Component | Selector | Notes |
|---|---|---|
| `UiButtonComponent` | `<ui-button>` | variants primary/secondary/tertiary/danger · sizes md/sm/xs · loading · a11y `aria-busy` |
| `UiCardComponent` | `<ui-card>` | header + `[card-actions]` slot + body (`flush`) |
| `UiKpiCardComponent` | `<ui-kpi-card>` | KPI tile w/ delta + trend — the dashboard decomposition unit |
| `UiProgressCardComponent` | `<ui-progress-card>` | absorption today / construction progress tomorrow · `role="progressbar"` |
| `UiStatusPillComponent` | `<ui-status-pill>` | canonical status palette incl. construction statuses |
| `UiEmptyStateComponent` | `<ui-empty-state>` | dashed empty state + action slot |
| `UiPageHeaderComponent` | `<ui-page-header>` | breadcrumb + title + subtitle + action slot (project-workspace anchor) |

**Verification:** `ng build` green (18 s) with the library; **wired into `vente-list.component`** (empty-state block → `<ui-empty-state>` + `<ui-button>`), rebuilt green (16 s). Barrel `index.ts` + `README.md` (usage + adoption guidance) included. All standalone + OnPush + token-driven; unused components tree-shake.

This is the foundation every later phase (god-template decomposition, project workspace, dashboard widgets, construction cards) builds on.

---

## 1. UX/UI Audit

### Scores (calibrated)
| Dimension | Score | Basis |
|---|---|---|
| UX | 68 | Coherent flows, good shells; CRM-shaped IA, no field UX |
| UI | 71 | Cohesive tokens, skeletons; now + a real component library |
| **Design System** | **62 → 72** | Tokens were strong; **component maturity was the gap — now started** |
| Mobile Experience | 55 | Responsive shell w/ mobile topbar + drawer exists; no field workflows/offline |
| Construction Readiness (UX) | 28 | Progress/KPI/status primitives now construction-ready; no project workspace/field UI |

### Information Architecture — current vs. future
- **Current = CRM-centric flat sidebar** (`shell.component.html`): Pipeline (dashboard, contacts, ventes…), then properties/projects/immeubles as peers. Project is *a list item*, not *a workspace*. This embeds a CRM assumption: "everything is a global list filtered by entity."
- **Problem for construction:** a developer building 3 towers needs to *enter a project* and work inside it (planning, buildings, procurement, site reports) — a flat global sidebar doesn't scale to that depth. Adding 10 construction nav items to the global rail would overwhelm sales users and still not scope to a project.

### God templates (build-confirmed by chunk size)
| Template | LOC | Chunk | Rank to refactor |
|---|---|---|---|
| home-dashboard.component.html | 2037 | **681 KB** | **1 (highest value)** |
| properties.component.html | 732 | 173 KB | 3 |
| contact-detail.component.html | 621 | 166 KB | 4 |
| vente-detail.component.html | 586 | 153 KB | 2 (pipeline-heavy) |
| project-create-wizard | 657 | 154 KB | 5 |

Root cause: repeated inline markup (KPI tiles, status chips, cards, empty states) with no components to absorb it — exactly what the new library targets.

---

## 2. Component Library Blueprint (delivered + next)

**Delivered:** the 7 components above (foundations reused from existing tokens: colors, spacing, type, radius, shadow, motion already in `design-tokens.css`).

**Next (additive, same pattern):**
- Inputs: `ui-text-field`, `ui-number-field`, `ui-currency-field` (DH), `ui-phone-field` (MA format), `ui-date-field`, `ui-search-field` (wrap existing `.search-input`).
- Data: `ui-data-table` (wrap `.data-table` w/ typed columns + sort + row-click), `ui-timeline`, `ui-activity-feed`.
- Feedback: `ui-alert`, `ui-toast` (a `NotificationToastComponent` already exists — fold in), `ui-skeleton` (wrap `.skeleton`).
- Navigation: `ui-tabs`, `ui-breadcrumbs` (extract from page-header), `ui-context-nav` (project workspace rail).
- **Construction cards (when module lands):** `ui-project-card`, `ui-building-card`, `ui-floor-card`, `ui-unit-card`, `ui-milestone-card`, `ui-budget-card`, `ui-site-report-card` — all composable from `ui-card` + `ui-kpi-card` + `ui-progress-card` + `ui-status-pill`.

Usage guidelines + adoption order are in [shared/ui/README.md](../hlm-frontend/src/app/shared/ui/README.md).

---

## 3. Information Architecture Blueprint (target, 5-year)

Group navigation by **domain**, not flat list; keep sales users in a lean default and reveal construction only where relevant:

```
GLOBAL RAIL (role-aware)
├── Accueil / Tableau de bord
├── CRM            → Prospects · Contacts · Ventes · Réservations · Contrats · Tâches
├── Patrimoine     → Projets · Immeubles · Lots (inventory)        ← entry to PROJECT WORKSPACE
├── Construction   → Chantiers · Planning · Procurement · Ressources · Qualité · HSE   (feature-flagged)
├── Analytics      → Cockpit commercial · Encaissements · Rapports · KPIs
└── Administration → Utilisateurs · Société · Audit · Paramètres
```

Principles: role- and feature-flag-aware (sales users never see construction noise); entity lists stay global, but **opening a project enters a scoped workspace** (§4); analytics consolidated; construction is a *domain group*, not items bolted onto Pipeline.

---

## 4. Project Workspace Blueprint (project-scoped navigation)

When a user opens a project, switch the rail to a **project-scoped context** (Jira/Procore/ACC pattern):

```
◂ Projets   |  Résidence Al Manar ▾        (project switcher)
─────────────────────────────────────────
  Overview        ← KPIs, absorption, progress (ui-kpi-card / ui-progress-card)
  Planning        ← WBS, milestones, Gantt          [construction]
  Buildings       ← immeubles → floors → units
  Units / Lots    ← inventory + 2D plan + 3D viewer
  Tasks           ← project tasks
  Procurement     ← POs, contractors                [construction]
  Budget          ← budget vs. actual               [construction]
  Documents       ← drawings, transmittals
  Progress        ← site reports, photos            [construction]
  Reports         ← project dashboards & exports
  Settings
```

**Implementation path (low-risk, incremental):**
1. Add a child route group `/app/projets/:projetId/*` with a `ProjectWorkspaceShellComponent` (its own `ui-context-nav` rail + `ui-page-header` with project switcher).
2. Move existing project-detail tabs (Aperçu, Plan de commercialisation) into Overview/Units.
3. Construction sections render behind a feature flag — present but empty until the module lands, so the IA never needs a second rewrite.

The delivered `UiPageHeaderComponent` (breadcrumb + switcher slot) is the header anchor for this.

---

## 5. Dashboard Modernization Plan

The reporting *backend* is rich (funnel, forecast, agents, inventory-intelligence, discount-analytics, receivables, SSE live stream, PDF/CSV — see go-live assessment §9). The *frontend* concentrates it into one 2037-LOC template. Modernize by:
1. **Decompose** home-dashboard into `<ui-kpi-card>`-driven sections fed by typed arrays (cuts the 681 KB chunk, kills duplication). **Highest-value first refactor.**
2. **Widget architecture:** a `DashboardWidget` interface + a grid host, so KPI/chart/list widgets are declarative and reorderable.
3. **Drill-down:** KPI card → filtered list route (e.g. "Réservations expirantes" → reservations filtered).
4. **Saved views + advanced filtering:** persist filter sets per user (period, agent, project).
5. **Personalization:** role-default widget sets (already have `/shareholder`, `/project-director` backends to drive this).
Patterns sized to HLM (Salesforce-Lightning/Power-BI-lite) — no BI-engine over-build.

---

## 6. Mobile & Field Strategy

Today: responsive shell (mobile topbar + slide-in drawer, confirmed in `shell.component.html`) — **adequate for office use, not field use**. Construction needs a **field-first** track:
- **Mobile navigation:** bottom nav + quick-action FAB + context menus (separate field shell, not the desktop rail squeezed).
- **Field workflows (PWA):** site-report submission, photo upload (reuse media presigned-URL flow), inspection checklist, issue/snag reporting, task/progress updates, document access.
- **Offline-ready:** queue-and-sync (IndexedDB/service worker) for reports + photos on poor sites; optimistic UI.
- Target users: site supervisors, engineers, inspectors, and field property agents.
Roadmap: PWA shell → online field forms → offline sync → push notifications. Decide PWA vs. native early (recommend PWA: reuses Angular + the new component library).

---

## 7. Accessibility & i18n
- **A11y:** new components ship with `aria-busy`, `role="progressbar"`, `aria-current`, focus-visible (inherited), `prefers-reduced-motion`. Next: keyboard nav + focus management audit on dialogs/tables, contrast check on status palette, screen-reader pass on critical flows (login/portal/create-vente).
- **i18n:** FR is primary via `@ngx-translate`. Architecture is translation-key-based (good). For **EN + Arabic (RTL)**: externalize the few hard-coded FR strings (e.g. the empty-state titles just refactored), add a `dir` strategy + logical CSS properties (`margin-inline`, `padding-inline`) so RTL needs no rewrite. Build the construction vocabulary into the i18n dictionary from the start.

---

## 8. Benchmarking (patterns worth adopting, sized to HLM)
| Source | Adopt | Skip (over-build) |
|---|---|---|
| Jira/Procore | **Project-scoped workspace** + project switcher | Full marketplace/app ecosystem |
| Salesforce Lightning | KPI cards + drill-down + saved views | Full no-code app builder |
| HubSpot | Clean list filtering + saved filter sets | Heavy marketing automation suite |
| Monday | Widget-grid dashboards | Generic work-OS flexibility |
| Autodesk ACC | Field/mobile workflows, document sets | BIM heaviness (roadmap only) |
| Power BI | Drill-down + personalization | Standalone BI engine |

---

## 9. Refactoring Plan (god-templates, ranked by value × low-risk × reuse)
1. **home-dashboard** → `ui-kpi-card`/`ui-progress-card` sections (biggest chunk, most duplication, pure win).
2. **vente-detail** → extract pipeline/info/echeance cards (`ui-card`, `ui-status-pill`); pipeline-heavy, sets construction patterns.
3. **properties** → `ui-data-table` + filter components.
4. **contact-detail** → tabbed `ui-card` sections.
5. **project-create-wizard** → step components + shared form fields (precursor to project workspace).
Each: extract presentational pieces, keep the smart container owning state/data — verify with `ng build` after each (proven workflow this pass).

---

## 10. Final Roadmap

**Phase 1 — Quick Wins (1–2 wks)** *(started this pass)*
- ✅ Component library foundation (7 components, compiled, wired into vente-list).
- Roll `ui-empty-state` + `ui-button` across remaining feature empty states.
- Externalize hard-coded FR strings touched during refactors.
- Decompose home-dashboard KPI row into `ui-kpi-card` (immediate chunk + duplication win).

**Phase 2 — UX Foundation (2–4 wks)**
- Add input + data-table + tabs/breadcrumbs components.
- Roll `ui-page-header` across feature roots (IA convergence).
- Dashboard widget architecture + drill-down + saved views.
- Single frontend state strategy (signals + signal-store); retire dead `auth.store.ts`.

**Phase 3 — Construction-Ready Experience (4–8 wks)**
- Project-scoped workspace shell + routes (feature-flagged construction sections).
- Domain-grouped global IA.
- Construction card components (project/building/unit/milestone/budget/site-report).
- RTL + EN scaffolding; construction i18n vocabulary.

**Phase 4 — Enterprise Maturity (8–12 wks)**
- PWA field shell + field workflows + offline sync.
- Full a11y (WCAG AA) pass + visual-regression (Storybook).
- Personalized dashboards per role; advanced filtering.
- Mobile push notifications.

Focus throughout: **adoption, usability, maintainability, construction-readiness** — not cosmetic redesign. The visual language is already good; this industrializes it into components and an IA that scales to the construction module without a future rewrite.
