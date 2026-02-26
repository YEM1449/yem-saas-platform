# Backlog Status (CDC → Implementation snapshot)

_Last updated: 2026-02-26 (Batch 1 update)_

This file tracks implementation progress against the CDC backlog. It does **not** replace `Backlog_Priorities.md` (which is a CDC extract).

Legend: **DONE / PARTIAL / NOT STARTED / UNKNOWN**.


## P1 – Priorité Critique (MVP)

| # | Item | Status | Notes / Evidence |

|---:|------|--------|------------------|

| 1 | Gestion des utilisateurs & rôles | **PARTIAL** | Endpoints + RBAC exist (AdminUserControllerIT, RbacIT). Add immediate JWT invalidation on role/disable if not already merged. |
| 2 | Gestion multi-sociétés / multi-projets | **PARTIAL** | Tenant isolation + projects exist (TenantControllerIT, CrossTenantIsolationIT). Consolidated reporting may be pending. |
| 3 | Module Commercial – version MVP | **PARTIAL** | Contacts/Prospects + deposits/reservations exist (ContactServiceIT, CrossTenantIsolationIT). ARCHIVED-project guardrail added (Batch 1). Project KPIs endpoint + Angular KPI view added. SaleContract entity not yet implemented (Batch 2+). |
| 4 | Prospection foncière | **NOT STARTED** | No evidence in tests/logs yet. |
| 5 | Workflow administratif simplifié (Maroc + UE) | **NOT STARTED** | No evidence in tests/logs yet. |
| 6 | Tableaux de bord essentiels | **PARTIAL** | Project KPIs mentioned as implemented (basic). Validate exact KPI list + UI coverage. |
| 7 | Module Construction – phase 1 | **NOT STARTED** | No evidence in tests/logs yet. |
| 8 | Gestion des stocks sur chantier | **NOT STARTED** | No evidence in tests/logs yet. |
| 9 | Achats & fournisseurs | **NOT STARTED** | No evidence in tests/logs yet. |
| 10 | Automatisations & notifications avancées | **NOT STARTED** | No evidence in tests/logs yet. |
| 11 | Module Finance complet | **NOT STARTED** | No evidence in tests/logs yet. |
| 12 | Qualité & sécurité chantier | **NOT STARTED** | No evidence in tests/logs yet. |
| 13 | Module Sous-traitants | **NOT STARTED** | No evidence in tests/logs yet. |
| 14 | SAV & gestion des tickets | **NOT STARTED** | No evidence in tests/logs yet. |
| 15 | Intégrations externes (API) | **UNKNOWN** | APIs exist, but external integrations not verified. |
