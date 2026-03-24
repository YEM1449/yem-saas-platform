# Implementation Plan — Wave 3: Frontend Completion + E2E

**Branch:** `Epic/users-management`
**Previous waves:** All 10 audit security tasks ✅ | User management module ✅ | SuperAdmin UI ✅ | CI/CD ✅

## Status: COMPLETE ✅

All Wave 3 tasks are done. The full stack builds, all CI checks pass (backend ITs, frontend unit tests, Docker build, E2E Playwright suite — 14/14 green).

## What Was Done (Wave 3)

| # | File | What | Status |
|---|------|------|--------|
| 16 | `16-frontend-tasks.md` | Angular UI for Task/follow-up management | ✅ Done |
| 17 | `17-frontend-documents.md` | Angular UI for Document attachments | ✅ Done |
| 18 | `18-frontend-usermanagement-upgrade.md` | Upgrade admin-users to use new UserManagement API | ✅ Done |
| 19 | `19-e2e-playwright.md` | Playwright E2E test suite (14 tests, all green) | ✅ Done |
| 20 | `20-production-readiness.md` | Final production checklist + deployment | 🔜 Next |

## CI Fixes Bundled in This Wave

- Removed `@Transactional` from all IT test classes (conflicts with `REQUIRES_NEW` in `AuditEventListener`)
- Added unique email UIDs per test to avoid state leakage between tests
- `AdminUserController` moved from `/api/admin/users` → `/api/users` (was blocked by `SUPER_ADMIN`-only security rule)
- Fixed `$.page.totalElements` JSON path in `CrossSocieteIsolationIT`
- `TlsConfigIT` now disables all default health indicators and enables only `ping`
- Angular tooling realigned to `^19.2.0` (fixes `chokidar` lock-file mismatch in Docker)
- Jasmine 5.x spy property pattern fixed in admin/superadmin guard specs
- ESLint `eqeqeq` fixes in societe-detail component

## Infrastructure Fixes

- `docker-compose.yml`: added `:-` fallback for `JWT_SECRET` (prevents `@NotBlank` failure with empty env vars)
- `nginx.frontend.conf`: added `resolver 127.0.0.11` + `$backend` variable (prevents DNS crash at nginx startup)
- `hlm-frontend/Dockerfile`: switched healthcheck from `wget` to `curl -f`
- `.github/workflows/e2e.yml`: inject `.env` with `JWT_SECRET`, use `docker compose up --wait`

## E2E Test Fixes

- `login.component.html`: added `data-testid="email"`, `password`, `login-button`, `error-message`
- `shell.component.html`: added `data-testid="logout-button"`, Tasks nav link (`/app/tasks`)
- `contacts.component.html`: added `data-testid="create-contact"`, `firstName`, `lastName`, `save-button`
- `tasks.component.ts`: `onSaved()` now inserts new task locally (no reload round-trip)
- `superadmin.spec.ts`: credentials updated to match changeset 046 (`superadmin@yourcompany.com / YourSecure2026!`)
- `playwright.config.ts`: `workers: 1` to serialize tests and prevent login rate-limit races

## Changeset Numbering

Latest applied Liquibase changeset: **050** (`050-enable-rls-phase1`)
Next available: **051**

## Next Steps

Read `tasks/20-production-readiness.md` for the production deployment checklist.
