# CLAUDE.md

Auto-load guide for Claude Code. Captures operating rules, architecture context, and the current implementation backlog.

## Architecture Context

**Multi-company (multi-société) real-estate CRM SaaS** platform (codename HLM).

- **Société** = company entity. Entity: `Societe`, column: `societe_id`
- **SocieteContext** = ThreadLocal request context (set by `JwtAuthenticationFilter`, cleared in `finally`)
- **AppUserSociete** = many-to-many User ↔ Societe with per-société role
- **SUPER_ADMIN** = platform-level role (manages sociétés). No `societe_id` in JWT.
- **ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT** = société-level CRM roles
- **ROLE_PORTAL** = client portal role (magic-link auth, read-only)

### Multi-Société Isolation Pattern

Every domain entity has `societe_id UUID NOT NULL`. Isolation enforced at:

1. **Service layer**: call `requireSocieteId()` → reads `SocieteContext.getSocieteId()`, throws if null.
2. **Repository layer**: all queries include `societe_id` as first parameter.

### Package Structure

`module/api/` (controllers + DTOs), `module/domain/` (JPA entities), `module/repo/` (repositories), `module/service/` (business logic).

Base package: `com.yem.hlm.backend`

### Current Modules (23)

audit, auth, commission, common, contact, contract, dashboard, deposit, document, gdpr, media, notification, outbox, payments, portal, project, property, reminder, reservation, societe, task, user, usermanagement

### Frontend Surfaces

- `/app/*` — CRM shell (staff; ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT)
- `/superadmin/*` — Platform shell (SUPER_ADMIN only)
- `/portal/*` — Buyer portal (ROLE_PORTAL, magic-link)

### Key Paths

| Resource | Backend path | Notes |
|---|---|---|
| Admin user CRUD | `/api/users` | Was `/api/admin/users` — moved to avoid SUPER_ADMIN-only security block |
| Company members | `/api/mon-espace/utilisateurs` | Active path for HR/membership |
| Tasks | `/api/tasks` | Default list = current user's tasks (assigneeId filter) |
| Documents | `/api/documents` | Cross-entity attachments |
| Super-admin societes | `/api/admin/societes` | SUPER_ADMIN only |

## Critical Rules

- **Never use `SocieteContext.getSocieteId()` without null-check.** Always use `requireSocieteId()` via `SocieteContextHelper`.
- For backend data changes, use additive Liquibase changesets only. Next available: **051**.
- Reuse existing package boundaries and patterns.
- Keep controllers on DTO contracts and error envelope (`ErrorResponse`, `ErrorCode`).
- Run relevant tests before finishing.
- `@Transactional` on IT test classes conflicts with `Propagation.REQUIRES_NEW` in `AuditEventListener` — **never annotate IT test classes with `@Transactional`**. Use unique email UIDs per test instead of rollback.
- E2E Playwright tests use `workers: 1` to prevent parallel login rate-limit races.

## Seed Credentials

| Account | Email | Password | Role |
|---|---|---|---|
| ACME admin | `admin@acme.com` | `Admin123!Secure` | ROLE_ADMIN |
| Super admin | `superadmin@yourcompany.com` | `YourSecure2026!` | SUPER_ADMIN |

## E2E data-testid Map

Login form: `email`, `password`, `login-button`, `error-message`
Shell: `logout-button`
Contacts: `create-contact`, `firstName`, `lastName`, `save-button`

## Current Backlog

See `tasks/IMPLEMENTATION_PLAN.md` — Wave 3 complete:
- Tasks 01–15: Security audit fixes + CI/CD ✅
- Tasks 16–19: Frontend tasks/documents/usermgmt + E2E ✅
- Task 20: Production readiness — checklist in `tasks/20-production-readiness.md`

## Quick Commands

```bash
cd hlm-backend && ./mvnw spring-boot:run          # Backend
cd hlm-backend && ./mvnw test                      # Unit tests (Surefire, *Test.java)
cd hlm-backend && ./mvnw failsafe:integration-test # IT tests (Failsafe, *IT.java)
cd hlm-backend && ./mvnw verify                    # Full verify (unit + IT)
cd hlm-frontend && npm start                       # Frontend dev (port 4200)
cd hlm-frontend && npm run build                   # Production build
cd hlm-frontend && npx playwright test             # E2E tests (requires backend running)
docker compose up -d                               # Full stack
docker compose up -d --wait --wait-timeout 180     # Full stack + health-wait
```
