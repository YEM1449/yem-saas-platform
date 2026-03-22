# Implementation Plan — Wave 3: Frontend Completion + E2E

**Branch:** `Epic/users-management`  
**Previous waves:** All 10 audit security tasks ✅ | User management module ✅ | SuperAdmin UI ✅ | CI/CD ✅

## What's Done

All backend APIs are complete (21 modules, 50 Liquibase changesets, RLS enabled). SuperAdmin societe management has full Angular UI. CI/CD pipelines exist.

## What's Missing

Three backend APIs have no Angular frontend, and no E2E tests exist.

## Task Order

| # | File | What | Effort |
|---|------|------|--------|
| 16 | `16-frontend-tasks.md` | Angular UI for Task/follow-up management | 3 hours |
| 17 | `17-frontend-documents.md` | Angular UI for Document attachments | 2 hours |
| 18 | `18-frontend-usermanagement-upgrade.md` | Upgrade admin-users to use new UserManagement API | 3 hours |
| 19 | `19-e2e-playwright.md` | Playwright E2E test suite | 3 hours |
| 20 | `20-production-readiness.md` | Final production checklist + deployment | 2 hours |

## Claude Code Usage

```bash
cd yem-saas-platform
git checkout Epic/users-management
claude
```

Then:
```
Read tasks/16-frontend-tasks.md and implement it
```

## Changeset Numbering

Next available Liquibase changeset: **051**
