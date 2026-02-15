# CLAUDE.md

This is a lightweight auto-load guide for Claude Code. It captures only high-signal operating rules for this repo; detailed, canonical instructions live in `AGENTS.md`.

## Canonical Context
- Read `AGENTS.md` first and treat it as the source of truth for architecture, workflows, and command references.

## Critical Rules
- Keep changes small, scoped, and easy to review.
- Ground decisions in repo files; do not invent commands or workflows.
- Never commit secrets/tokens; only reference env var names (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_TTL_SECONDS`).
- Respect multi-tenancy: rely on JWT `tid` + `TenantContext`; do not trust tenant IDs from client payloads.
- Respect RBAC: preserve `ROLE_ADMIN` / `ROLE_MANAGER` / `ROLE_AGENT` enforcement.
- For backend data changes, use additive Liquibase changesets only (do not edit applied ones).
- Keep controllers on DTO contracts and existing error envelope (`ErrorResponse`, `ErrorCode`).
- Reuse existing package boundaries (`api`, `service`, `repo`, `domain`).
- Run relevant tests for touched areas before finishing.
- Update docs when behavior, setup, or commands change.
- If a command is uncertain, mark `TODO verify` and point to the file to confirm.
- Prefer `rg` for searching; avoid expensive recursive grep patterns.
- For frontend local dev, use proxy-relative API calls (`/auth`, `/api`, etc.), not hardcoded backend URLs.
- Do not add broad refactors unless explicitly requested.

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

# Auth smoke test
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```
