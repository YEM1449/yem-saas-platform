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

**⚠ KNOWN BUG:** Several controllers still use bare `SocieteContext.getSocieteId()` without null-check. See tasks 01 and 11.

### Package Structure

`module/api/` (controllers + DTOs), `module/domain/` (JPA entities), `module/repo/` (repositories), `module/service/` (business logic).

Base package: `com.yem.hlm.backend`

### Current Modules (19)

audit, auth, commission, common, contact, contract, dashboard, deposit, gdpr, media, notification, outbox, payments, portal, project, property, reminder, reservation, societe, user, **usermanagement** (new)

## Critical Rules

- **Never use `SocieteContext.getSocieteId()` without null-check.** Always use `requireSocieteId()`.
- For backend data changes, use additive Liquibase changesets only. Next available: **042**.
- Reuse existing package boundaries and patterns.
- Keep controllers on DTO contracts and error envelope (`ErrorResponse`, `ErrorCode`).
- Run relevant tests before finishing.

## Current Backlog

See `tasks/IMPLEMENTATION_PLAN.md` — 15 tasks total:
- Tasks 01–11: Security audit fixes (critical → low priority)
- Tasks 12–15: Next wave (CI/CD, frontend audit, frontend user management UI, E2E tests)
- Task 06: SKIP (already done via @Size)

## Quick Commands

```bash
cd hlm-backend && ./mvnw spring-boot:run          # Backend
cd hlm-backend && ./mvnw test                      # Unit tests
cd hlm-backend && ./mvnw verify                    # Full verify
cd hlm-frontend && npm start                       # Frontend dev
cd hlm-frontend && npm run build                   # Frontend build
```
