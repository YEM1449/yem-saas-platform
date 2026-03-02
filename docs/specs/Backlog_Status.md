# Backlog Status (CDC → Implementation snapshot)

_Last updated: 2026-03-02 (Phase 3 Commercial Intelligence)_

This file tracks implementation progress against the CDC backlog. It does **not** replace `Backlog_Priorities.md` (which is a CDC extract).

Legend: **DONE / PARTIAL / NOT STARTED / UNKNOWN**.


## P1 – Priorité Critique (MVP)

| # | Item | Status | Notes / Evidence |

|---:|------|--------|------------------|

| 1 | Gestion des utilisateurs & rôles | **PARTIAL** | Endpoints + RBAC exist (AdminUserControllerIT, RbacIT). Add immediate JWT invalidation on role/disable if not already merged. |
| 2 | Gestion multi-sociétés / multi-projets | **PARTIAL** | Tenant isolation + projects exist (TenantControllerIT, CrossTenantIsolationIT). Consolidated reporting may be pending. |
| 3 | Module Commercial – version MVP | **DONE** | Contacts/Prospects + Deposits/Reservations + Sales Contracts + Commercial Dashboard + Receivables Dashboard + Commission Tracking + Discount Analytics + Prospect Source Funnel fully implemented through Phase 3. RBAC, tenant isolation, audit trail all covered. Commission rule CRUD with project-specific priority. Receivables KPIs (outstanding, overdue, aging buckets, collection rate). Discount analytics (avg/max discount %, top-10 agents by discount). Prospect source funnel (source grouping + conversion rates). IT: `CommissionIT`, `ReceivablesDashboardIT`, `CommercialDashboardIT` (7 tests). |
| 4 | Prospection foncière | **NOT STARTED** | No evidence in tests/logs yet. |
| 5 | Workflow administratif simplifié (Maroc + UE) | **NOT STARTED** | No evidence in tests/logs yet. |
| 6 | Tableaux de bord essentiels | **DONE** | Project KPIs (`GET /api/projects/{id}/kpis`), Commercial Dashboard (sales, deposits, reservations, prospects, trend charts, top-10 tables, drill-down), Cash Dashboard (payment schedule KPIs), Receivables Dashboard (outstanding/overdue/collection rate/aging buckets), Discount Analytics (avg/max discount, top-10 agents), Prospect Source Funnel — all implemented through Phase 3. Angular components at `/app/dashboard/commercial`, `/app/dashboard/commercial/cash`, `/app/dashboard/receivables`. |
| 7 | Module Construction – phase 1 | **NOT STARTED** | No evidence in tests/logs yet. |
| 8 | Gestion des stocks sur chantier | **NOT STARTED** | No evidence in tests/logs yet. |
| 9 | Achats & fournisseurs | **NOT STARTED** | No evidence in tests/logs yet. |
| 10 | Automatisations & notifications avancées | **NOT STARTED** | No evidence in tests/logs yet. |
| 11 | Module Finance complet | **NOT STARTED** | No evidence in tests/logs yet. |
| 12 | Qualité & sécurité chantier | **NOT STARTED** | No evidence in tests/logs yet. |
| 13 | Module Sous-traitants | **NOT STARTED** | No evidence in tests/logs yet. |
| 14 | SAV & gestion des tickets | **NOT STARTED** | No evidence in tests/logs yet. |
| 15 | Intégrations externes (API) | **UNKNOWN** | APIs exist, but external integrations not verified. |
