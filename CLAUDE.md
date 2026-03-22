# CLAUDE.md

This is the auto-load guide for Claude Code. It captures operating rules, architecture context, and the current implementation backlog for this repo.

## Architecture Context

This is a **multi-company (multi-société) real-estate CRM SaaS** platform. The codebase has been migrated from a multi-tenant model to a multi-société model. Key terminology:

- **Société** = company entity (not "tenant"). Entity: `Societe`, column: `societe_id`
- **SocieteContext** = ThreadLocal request context (set by `JwtAuthenticationFilter`, cleared in `finally`)
- **AppUserSociete** = many-to-many join between `User` and `Societe` with per-société role
- **SUPER_ADMIN** = platform-level role (manages sociétés). Has no `societe_id` in JWT.
- **ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT** = société-level CRM roles
- **ROLE_PORTAL** = client-facing portal role (magic-link auth, read-only)

### Multi-Société Isolation Pattern

Every domain entity has a `societe_id UUID NOT NULL` column. Isolation is enforced at two levels:

1. **Service layer**: every method calls `requireSocieteId()` which reads `SocieteContext.getSocieteId()` and throws `CrossSocieteAccessException` if null.
2. **Repository layer**: all queries include `societe_id` as the first parameter (e.g., `findBySocieteIdAndId()`).

**Schedulers** that run cross-société must call `SocieteContext.setSystem()` at entry and `SocieteContext.clear()` in finally.

### Package Structure

Each feature module follows: `module/api/` (controllers + DTOs), `module/domain/` (JPA entities + enums), `module/repo/` (Spring Data repositories), `module/service/` (business logic + exceptions).

Base package: `com.yem.hlm.backend`

## Critical Rules

- **Never use `SocieteContext.getSocieteId()` without null-check in controllers.** Always use `requireSocieteId()` helper or the new `SocieteContextHelper` component.
- Keep changes small, scoped, and easy to review.
- Ground decisions in existing repo files; do not invent commands or workflows.
- Never commit secrets/tokens; only reference env var names.
- For backend data changes, use additive Liquibase changesets only (do not edit applied ones). Next changeset number: **039**.
- Keep controllers on DTO contracts and existing error envelope (`ErrorResponse`, `ErrorCode`).
- Reuse existing package boundaries (`api`, `service`, `repo`, `domain`).
- Run relevant tests for touched areas before finishing.
- Update docs when behavior, setup, or commands change.
- Prefer `rg` for searching; avoid expensive recursive grep patterns.
- Do not add broad refactors unless explicitly requested.

## Current Implementation Backlog

See `tasks/IMPLEMENTATION_PLAN.md` for the full ordered backlog from the security audit. Execute tasks in order. Each task file in `tasks/` is self-contained with exact files to modify, code changes to make, tests to run/add, and acceptance criteria.

### Priority Order:
1. `tasks/01-critical-null-guard-fix.md` — Fix null societeId in Commission + Dashboard controllers
2. `tasks/02-societe-context-helper.md` — Extract shared SocieteContextHelper component
3. `tasks/03-scheduler-context-standardization.md` — Standardize all schedulers
4. `tasks/04-audit-societe-switch.md` — Add audit logging for société switch
5. `tasks/05-rename-tenant-references.md` — Rename legacy "tenant" references
6. `tasks/06-jwt-secret-validation.md` — Add JWT secret length validation at startup
7. `tasks/07-cors-production-guard.md` — CORS production safety check
8. `tasks/08-task-module.md` — New Task/follow-up management module
9. `tasks/09-document-module.md` — New Document attachment module
10. `tasks/10-rls-policies.md` — PostgreSQL Row-Level Security (defense-in-depth)

## Quick Commands

```bash
# Backend run
cd hlm-backend && ./mvnw spring-boot:run

# Backend unit tests
cd hlm-backend && ./mvnw test

# Backend integration tests (Docker/Testcontainers)
cd hlm-backend && ./mvnw failsafe:integration-test

# Frontend dev + build
cd hlm-frontend && npm start
cd hlm-frontend && npm run build

# Search codebase
rg "SocieteContext" hlm-backend/src/main/java --type java

# Auth smoke test
EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```
