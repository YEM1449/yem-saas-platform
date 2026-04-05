# Quick Context — HLM CRM (AI Guide)

> Load this first when starting a new session on this codebase.  
> For deep dives, load `deep-context.md` as well.

---

## What is this?

**Multi-company (multi-société) real-estate CRM SaaS** (codename HLM).  
Spring Boot 3.5.x backend + Angular 19 frontend + PostgreSQL + Redis + S3-compatible object storage.

---

## Critical constraints (never violate)

1. **Tenant isolation is non-negotiable.** Every query must `WHERE societe_id = ?`. Always use `requireSocieteId()` — never `SocieteContext.getSocieteId()` directly.
2. **Liquibase only — additive changesets.** No DROP, no ALTER without a migration. Next available: **059**.
3. **Never `@Transactional` on IT test classes.** AuditEventListener uses `REQUIRES_NEW` → FK violation.
4. **Angular templates forbid regex literals, arrow functions, `?.` on method calls.** Move logic to component getters.
5. **All emails use `AFTER_COMMIT`.** `@TransactionalEventListener(AFTER_COMMIT)` on all event listeners.
6. **Portal JWT is separate from CRM JWT.** `ROLE_PORTAL` cannot access `/api/**`. Portal uses httpOnly cookie.

---

## Architecture at a glance

```
Browser → JwtAuthenticationFilter (sets SocieteContext ThreadLocal)
        → SecurityConfig (PORTAL / CRM / SUPER_ADMIN tiers)
        → Controller → Service → Repository (all filter by societe_id)
        → PostgreSQL (RLS phase 2 on all tables)
```

- **Roles**: SUPER_ADMIN (platform) | ROLE_ADMIN, ROLE_MANAGER, ROLE_AGENT (société CRM) | ROLE_PORTAL (buyer)
- **Contact status pipeline**: PROSPECT → QUALIFIED_PROSPECT → CLIENT → ACTIVE_CLIENT → COMPLETED_CLIENT
- **Vente state machine**: COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE (terminal) | ANNULE (terminal from any)

---

## Key paths

| What | Path |
|---|---|
| Backend base | `hlm-backend/src/main/java/com/yem/hlm/backend/` |
| Frontend base | `hlm-frontend/src/app/` |
| Liquibase | `hlm-backend/src/main/resources/db/changelog/` |
| E2E tests | `hlm-frontend/e2e/` |
| Seed credentials | `admin@acme.com / Admin123!Secure` (ADMIN) |

---

## Quick commands

```bash
cd hlm-backend && ./mvnw compile -q              # verify backend compiles
cd hlm-backend && ./mvnw test -q                 # unit tests
cd hlm-frontend && npx ng build --configuration=ci  # verify frontend
cd hlm-frontend && npx playwright test --list    # list E2E tests
docker compose up -d                             # full stack
```

---

## Most recent wave (Wave 10)

- `portal.spec.ts` E2E test (8 tests for buyer portal flow)
- Activation password strength meter + requirements checklist
- E2E CI fix: `PLAYWRIGHT_API_BASE` env var, activation URL query-param fix
- Playwright config: activation-tests, pipeline-tests, portal-tests projects added
- Docs: user guides (agent, manager, acquereur), api-map, known-issues, AI context

---

## Where to look for things

| What | File |
|---|---|
| All modules | `CLAUDE.md` → Current Modules table |
| API endpoints | `docs/context/api-map.md` |
| Known bugs | `docs/context/known-issues.md` |
| Domain model | `docs/context/DATA_MODEL.md` |
| Architecture decisions | `docs/context/DECISIONS.md` |
| Deployment runbook | `docs/runbook-operations.md` |
| French user guides | `docs/user-guide/` |
| E2E test map | `hlm-frontend/playwright.config.ts` |
