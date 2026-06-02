# HLM — Real Estate Developer Operating System: KPI & Dashboard Framework

**Date:** 2026-06-02
**Reframe:** This is not "a CRM with real-estate features." It is a **Real Estate Developer Operating System**. CRM + Sales are the first modules; **Construction Management** is the next. The platform serves developers who **build → sell → deliver**. Every KPI must originate from the developer capital lifecycle and support a real decision by a named role — or it is cut.

---

## 1. The spine: the developer capital lifecycle

```
LAND → PROJECT → CONSTRUCTION → MARKETING → SALES → RESERVATION → CONTRACT → COLLECTION → DELIVERY → WARRANTY
 │        │           │            │          │          │            │           │           │          │
 cash    margin     cost+        demand    price &    conversion   legal/     cash-in    handover   defect
 out     set        schedule     creation  velocity   & expiry     finance    vs plan    & title    liability
```

**The master metric every executive view rolls up to: the project cash-and-margin curve.**
A developer lives or dies on *capital deployed (land + construction) vs. capital recovered (collections), and realized margin vs. underwritten margin, per project, over time.* Land and construction **consume** cash; sales create **contracted** value; collection **recovers** cash; delivery **releases** the final tranche and starts warranty liability. Every KPI below is a leading or lagging indicator on that curve. If a proposed KPI doesn't move cash, margin, schedule, or risk on this spine, it does not belong on a dashboard.

---

## 2. KPI design law (the test every KPI must pass)

A metric earns a dashboard slot **only** if all five are answerable:

| # | Question | If unanswerable → |
|---|---|---|
| 1 | **Decision** it supports | vanity — cut |
| 2 | **Role** who owns that decision | orphan — cut |
| 3 | **Screen** where the action is taken (drill target) | dead-end — cut or make it link |
| 4 | **Action triggered when it deteriorates** | wallpaper — cut |
| 5 | **Revenue / cost / risk impact** of that action | decorative — cut |

Corollary (anti-card-explosion): a role dashboard carries **4–6 decision-grade KPIs**, not 18 cards. Counts ("biens au total", "acomptes count") are *context*, shown beside a decision KPI, never as the headline.

---

## 3. Role dashboards (prioritized) — decision-traceability tables

Each table is the role's dashboard. Columns: **KPI · Lifecycle stage · Decision supported · Screen (action) · Deterioration → action · Impact.**

### 3.1 CEO — *portfolio capital & risk*
Question they open the screen to answer: *"Where is my capital at risk, and which project needs me this month?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Project cash position** (deployed vs recovered, per project) | All | Capital allocation; which project to fund/pause | Portfolio dashboard → project workspace | Negative cash gap widening → halt new land/CapEx, accelerate collection | Cash / solvency |
| **Realized margin vs underwritten** (prix/m² réalisé vs cible) | Project→Sales | Pricing & launch strategy | Project workspace → Commercialisation | Margin erosion >X pts → stop discounting, re-price, escalate | Revenue/margin |
| **Net commercialisation vs plan** (contracted value vs target, by project) | Sales | Launch timing, marketing spend | Commercial cockpit | Behind plan → reallocate marketing, phase release | Revenue |
| **Delivery-risk exposure** (Σ value of units in projects behind schedule) | Construction→Delivery | Escalation; penalty/caducité exposure | Construction dashboard (next module) | Rising → COO intervention, renegotiate delivery dates | Risk/penalty |
| **Cancellation rate (90d, trend)** | Contract | Product/pricing/process problem detection | Pipeline analysis | Trend up → root-cause (financing? price? delivery fear?) | Revenue/risk |

### 3.2 COO — *execution across all projects*
*"Which projects are red on the build-vs-sell balance, and where are the bottlenecks?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Projects on-track vs at-risk** (schedule+budget composite RAG) | Construction | Where to intervene this week | Portfolio → project workspace | Project turns red → resource reallocation, recovery plan | Cost/schedule |
| **Build-vs-sell balance** (physical progress % vs absorption %, per project) | Construction↔Sales | Don't oversell ahead of build / overbuild ahead of demand | Project workspace | Divergence → throttle sales OR accelerate marketing | Cash/risk |
| **Delivery pipeline** (units to deliver in next 3/6 months vs readiness) | Delivery | Staffing & handover scheduling | Delivery dashboard | Readiness gap → expedite snagging, add delivery capacity | Cash (final tranche)/CSAT |
| **Contractor/resource bottlenecks** | Construction | Resequence, claim, replace | Construction dashboard (next module) | Bottleneck → re-plan critical path | Schedule/cost |

### 3.3 Commercial Director — *commercial performance across projects*
*"Which stock is selling below plan, and where is price leaking?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Absorption velocity vs plan** (per project/tranche, units/week vs target) | Sales | Promo, price, phase release | Commercial cockpit → Plan de commercialisation | Below plan → launch promo, adjust price grid, release/hold stock | Revenue/velocity |
| **Price realization / discount leakage** (réalisé vs grille, by agent/project) | Sales | Discount governance | Discount analytics | Leakage up → tighten approval, retrain | Margin |
| **Inventory aging** (slow-moving units, days on market by type) | Sales | Which units to push/re-price | Inventory intelligence → properties | Aging stock grows → targeted promo, bundle, re-price | Revenue/cash |
| **Source / channel ROI** (cost per reserved unit by source) | Marketing | Marketing budget reallocation | Source funnel | Channel ROI drops → shift spend | Cost/CAC |

### 3.4 Sales Director — *team & pipeline*
*"Is the pipeline converting, and which deals/agents need me today?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Weighted pipeline value & velocity** | Sales | Forecast, staffing | Commercial dashboard | Velocity drops → lead reallocation, coaching | Revenue |
| **Stage conversion** (lead→reservation→contract) | Sales→Contract | Fix the leaking stage | Funnel | A stage drops → process fix at that stage | Revenue |
| **Reservation→contract conversion & expiry** | Reservation→Contract | Follow-up vs release unit | Reservations | Expiries rising → chase or release stock | Revenue/stock |
| **Agent quota attainment & stalled deals** | Sales | Coaching, deal intervention | Ventes (filtered) | Below quota / stalled >30d → 1:1, reassign | Revenue |

### 3.5 Construction Director — *build delivery* (**defines the next module**)
*"Which project is slipping on cost or schedule, and what's the recovery?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Physical progress % vs baseline (S-curve)** | Construction | Accelerate / re-baseline | Construction dashboard | Behind curve → recovery plan, overtime, resequence | Schedule/penalty |
| **Cost performance (CPI) & budget vs committed vs actual** | Construction | Cost control, change-order discipline | Cost dashboard | CPI<1 → freeze scope, claim, value-engineer | Cost/margin |
| **Schedule performance (SPI) & critical-path slippage** | Construction | Resequence critical path | Planning/Gantt | Slippage → re-plan, expedite long-leads | Schedule/penalty |
| **Contractor performance** (progress, quality, claims) | Construction | Pay/withhold, replace | Contractor view | Underperformance → withhold, escalate, replace | Cost/schedule |
| **HSE incident rate** | Construction | Compliance, stop-work | HSE dashboard | Incident → audit, training, stop-work | Risk/legal |

### 3.6 Project Manager — *single project execution*
*"What do I act on today on this site?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **WBS task completion vs baseline** | Construction | Daily resequencing | Project planning | Tasks late → reassign, expedite | Schedule |
| **Milestone status** (next milestone at risk) | Construction | Escalate early | Project workspace | At risk → escalate to Construction Director | Schedule/penalty |
| **Budget consumed vs progress** (mini-EVM) | Construction | Catch overrun early | Project cost | Spend > progress → investigate | Cost |
| **Open issues / RFIs / snag backlog** | Construction→Delivery | Clear blockers | Quality/issues | Backlog grows → prioritize closure | Schedule/CSAT |

### 3.7 Finance Director — *cash & margin*
*"Are we collecting to plan, and is project margin holding?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Collections vs plan (cash-in curve)** | Collection | Cash management, financing | Receivables dashboard | Behind → intensify dunning, draw facility | Cash |
| **DSO / aging / impayés (with debtor list)** | Collection | Dunning prioritization | Receivables → vente | DSO up → targeted relances, legal | Cash/risk |
| **Committed vs budget vs actual cost** | Construction | Cost control, cash forecast | Cost dashboard (next module) | Commitment > budget → freeze, re-forecast | Cost/cash |
| **Realized project margin** | All | Go/no-go on next phase | Project P&L | Margin compresses → re-underwrite phase 2 | Margin |

### 3.8 Customer Delivery Manager — *handover & warranty*
*"What's ready to hand over, what's overdue, and what defects are open?"*

| KPI | Stage | Decision | Screen | Deterioration → action | Impact |
|---|---|---|---|---|---|
| **Units ready vs delivered vs overdue** | Delivery | Handover scheduling | Delivery dashboard | Overdue rises → expedite, communicate, manage penalty | Cash (final tranche)/CSAT |
| **PV réception / titre foncier completion** | Delivery | Legal closeout, final collection trigger | Vente detail (post-livraison) | Stalled → chase notary/administration | Cash/legal |
| **Snag/defect backlog & SLA** | Warranty | Contractor accountability | Quality/snag | Backlog/SLA breach → escalate to contractor (withhold retention) | Cost/CSAT |
| **Warranty claims & provisioning** (parfait achèvement / décennale) | Warranty | Reserve provisioning, contractor recovery | Warranty register | Claims rise → provision, pursue contractor | Cost/risk |

---

## 4. Reject list — KPIs to remove or demote (vanity / non-decision)

These currently appear (or are tempting) but fail the 5-question test as **headline** KPIs:

| Metric | Why it fails | Verdict |
|---|---|---|
| **"Biens au total" / "Acomptes count" / "Réservations count"** as headline tiles | Pure counts; no decision, no target, no deterioration action | Demote to *context* beside a decision KPI |
| **"CA Réalisé (Livré)"** as a standalone big number | Lagging vanity unless shown vs *delivery plan* or as cash-in trigger | Keep only inside Finance/Delivery, tied to plan |
| **"Montant acomptes (MAD)"** tile | No decision owner; sub-component of collections | Fold into Collections-vs-plan |
| **Generic "CA ce mois"** with no target | A number without a target supports no decision | Always pair with quota/target or cut |
| **Property "type breakdown" bars** on a project overview | Inventory composition ≠ a decision; relevant only for pricing/mix analysis | Move to commercial analysis, not overview headline |
| **Société-wide "taux d'absorption"** shown without a target line | Absorption only drives a decision *against plan* | Always render vs target |
| **Raw "active ventes count"** as a hero | Context for pipeline value, not a decision itself | Demote to sub-line |

Principle: **a count is not a KPI.** A KPI is a rate, a ratio-to-target, a trend, or an exception list that triggers an action.

---

## 5. Navigation architecture — organize the OS by lifecycle, not by entity

Today's sidebar is **entity-flat** (Dashboard, Projets, Propriétés, Contacts, Tâches, Réservations, Ventes, Contrats) — a CRM shape. An OS should be **lifecycle/role shaped**, role-aware and feature-flagged:

```
OS SHELL (role-aware)
├── ▸ Direction            → executive dashboards (CEO/COO/Finance role views)
├── ▸ Développement        → Land bank · Projets · Faisabilité (margin/pricing)
├── ▸ Construction         → Chantiers · Planning · Budget/Coûts · Qualité · HSE   [next module, feature-flagged]
├── ▸ Commercialisation    → Marketing/Sources · Pipeline (Ventes) · Réservations · Contrats
├── ▸ Finance              → Encaissements · Créances/DSO · Marge projet
├── ▸ Livraison            → Remises · PV/Titres · Garanties/SAV
└── ▸ Référentiel          → Biens · Contacts · Utilisateurs · Société
```

Rules:
- **Entry point is a role dashboard**, not a list. The list (Biens, Contacts) is reference data under *Référentiel*, reached by drill-down — not the front door.
- **Project workspace** (already built) is the cross-lifecycle unit: opening a project scopes Développement→Construction→Commercialisation→Livraison for *that* project. This is the OS's core navigational object.
- Construction group is **signposted now** (the "à venir" tabs already added to the project workspace) so the IA doesn't get rewritten when the module ships.

---

## 6. Construction module KPI prep — the KPIs define the schema

The Construction Director / PM KPIs above **dictate** what the module must capture from day one (build the data model to serve the decision, not the other way round):

| KPI needs → | Data the module must capture |
|---|---|
| Physical progress % vs baseline (S-curve) | **WBS** + **baseline schedule** + periodic **progress measurement** per work package |
| CPI / budget vs committed vs actual | **Budget lines** + **commitments (PO)** + **actuals**, change-order ledger (append-only) |
| SPI / critical path | Task **dependencies / predecessors**, baseline vs actual dates (CPM engine) |
| Contractor performance | **Contractor/subcontractor** records linked to work packages + claims/retention |
| HSE incident rate | **Incident** + audit register |
| Build-vs-sell balance (COO) | Construction progress **joined to** the existing sales/absorption data on the shared property/lot model |
| Delivery readiness (CDM) | Per-unit completion + snag closure feeding the **Delivery** stage |

The crucial integration: the **build-vs-sell balance** KPI is the platform's unique wedge — it only exists because one system holds *both* construction progress *and* sales absorption on the same lots (the existing `lot_3d_mapping` / property model is the join). No CRM and no Procore computes it; the OS can.

---

## 7. Reporting strategy

- **Single source of truth:** every KPI materialized via the existing **event-carried read-model / KPI-snapshot** pattern (already in the platform: `KpiSnapshot`, `KpiComputationService`). Construction KPIs (EVM, S-curves) extend the same pattern — never live-aggregated on read.
- **Definitions registry:** one canonical formula per metric (we just did this for absorption via `core/utils/absorption.ts`); extend to a documented metric catalog so "margin", "DSO", "progress %" each have exactly one definition.
- **Per-role scheduled digests:** each role gets a weekly/monthly digest of *their* 4–6 KPIs with deltas and the exception lists — driven from the same read models, exported (PDF/CSV already exist).
- **Drill path is mandatory:** every KPI tile links to the screen where the action happens (the decision-traceability table's "Screen" column is the wiring spec).

---

## 8. Mapping to the platform today (what's done / what's next)

**Already aligned (this session):**
- Dashboard de-cluttered to a **worklist + anchors** (kills the card explosion §4 condemns) — both admin and agent variants.
- Absorption unified to one definition (§7 definitions registry, in microcosm).
- Project workspace as the cross-lifecycle unit (§5), with construction signposts (§6).
- Project cards/overview now lead with a decision metric (absorption vs target), not raw counts.

**Next, in priority order (all derive from the tables above):**
1. **Role-based executive dashboards** — replace the one-size dashboard with CEO / Finance / Commercial / Sales views, each carrying only its 4–6 decision KPIs. (Highest exec value.)
2. **Always-render-vs-target** — every absorption/CA/collection KPI gets its target line (kills the §4 "number without a target" failure).
3. **Lifecycle navigation** (§5) — reorganize the shell from entity-flat to lifecycle/role.
4. **Build-vs-sell balance** scaffolding — the COO KPI; even pre-construction-module, expose sales absorption vs a manual progress input per project, so the join is ready.
5. **Construction module** — built KPI-first per §6.

---

### One-line thesis
Design the dashboards backward from **the eight people's decisions on the cash-and-margin lifecycle**. Anything that isn't one of those people acting on one of those decisions is not a KPI — it's noise, and the OS shows less of it than a CRM would, on purpose.
