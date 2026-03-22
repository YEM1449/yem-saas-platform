# Implementation Plan — YEM SaaS Platform (Updated)

**Last updated:** Based on `Epic/users-management` branch state.

## Current Status

The `usermanagement` module has been built (invitation flow, GDPR, role management, events, changesets 039–041). All 10 original audit tasks + 1 new task remain to be implemented.

## How to Use with Claude Code

```bash
cd yem-saas-platform
claude
```

Then:
```
Read tasks/IMPLEMENTATION_PLAN.md. Start with task 01 and work through each task in order. After each task, run the specified tests.
```

## Task Order

### Phase 1 — Critical Security (do first)

| # | File | What | Effort |
|---|------|------|--------|
| 01 | `01-critical-null-guard-fix.md` | Fix null societeId in CommissionController + DashboardControllers | 30 min |
| 11 | `11-usermanagement-null-guard.md` | Fix null societeId in new UserManagementController (18 occurrences) | 20 min |
| 02 | `02-societe-context-helper.md` | Extract SocieteContextHelper, refactor all controllers to use it | 1 hour |

### Phase 2 — Hardening

| # | File | What | Effort |
|---|------|------|--------|
| 03 | `03-scheduler-context-standardization.md` | Wrap all schedulers with runAsSystem() | 1 hour |
| 04 | `04-audit-societe-switch.md` | Audit log for société switch | 20 min |
| 05 | `05-rename-tenant-references.md` | Rename findByTenantIdAndIdForUpdate + legacy tenant refs | 1 hour |
| 06 | `06-jwt-secret-validation.md` | ~~Already done via @Size(min=32)~~ SKIP | — |
| 07 | `07-cors-production-guard.md` | CORS localhost check in production | 20 min |

### Phase 3 — New Features

| # | File | What | Effort |
|---|------|------|--------|
| 08 | `08-task-module.md` | Task/follow-up management module | 3 hours |
| 09 | `09-document-module.md` | Document attachment module | 3 hours |

### Phase 4 — Defense in Depth

| # | File | What | Effort |
|---|------|------|--------|
| 10 | `10-rls-policies.md` | PostgreSQL Row-Level Security | 2 hours |

### Phase 5 — Next Wave (after audit tasks)

| # | File | What | Effort |
|---|------|------|--------|
| 12 | `12-ci-cd-pipeline.md` | GitHub Actions CI/CD | 2 hours |
| 13 | `13-frontend-audit.md` | Angular frontend société isolation audit | 2 hours |
| 14 | `14-frontend-usermanagement.md` | Angular UI for user management module | 4 hours |
| 15 | `15-e2e-tests.md` | End-to-end Playwright tests | 3 hours |

## Changeset Numbering

Changesets 039–041 are taken (user management). Next available: **042**.

## Verification

```bash
cd hlm-backend && ./mvnw verify
```
