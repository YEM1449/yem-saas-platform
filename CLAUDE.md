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

### Multi-Société Isolation Architecture

```
Browser / Mobile App
        │
        ▼
 JwtAuthenticationFilter
  • Sets SocieteContext (ThreadLocal) from JWT claim "sid"
  • For ROLE_PORTAL: reads contactId from sub; skips UserSecurityCacheService
        │
        ▼
 SecurityConfig (Spring Security)
  • permitAll: /auth/**, /api/portal/auth/**
  • hasRole("PORTAL"):  /api/portal/**
  • hasAnyRole(ADMIN,MANAGER,AGENT): /api/**
  • hasRole("SUPER_ADMIN"): /api/admin/**
        │
        ▼
 Controller → Service → Repository
  • Service calls requireSocieteId() (throws if null)
  • All queries WHERE societe_id = ?
        │
        ▼
 PostgreSQL — societe_id on every domain table (NOT NULL)
```

Every domain entity has `societe_id UUID NOT NULL`. Isolation enforced at:

1. **Service layer**: call `requireSocieteId()` → reads `SocieteContext.getSocieteId()`, throws if null.
2. **Repository layer**: all queries include `societe_id` as first parameter.

### Package Structure

`module/api/` (controllers + DTOs), `module/domain/` (JPA entities), `module/repo/` (repositories), `module/service/` (business logic).

Base package: `com.yem.hlm.backend`

### Current Modules (24)

audit, auth, commission, common, contact, contract, dashboard, deposit, document, gdpr, **immeuble**, media, notification, outbox, payments, portal, project, property, reminder, reservation, societe, task, user, usermanagement

### Frontend Surfaces

- `/app/*` — CRM shell (staff; ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT)
- `/superadmin/*` — Platform shell (SUPER_ADMIN only)
- `/portal/*` — Buyer portal (ROLE_PORTAL, magic-link)

### Key API Paths

| Resource | Backend path | Notes |
|---|---|---|
| Admin user CRUD | `/api/users` | Was `/api/admin/users` — moved to avoid SUPER_ADMIN-only security block |
| **User typeahead** | `GET /api/users/suggest?q=` | Returns `[{id, displayName, email}]`; accessible to ADMIN, MANAGER, AGENT (method-level `@PreAuthorize` overrides class-level ADMIN-only) |
| Company members | `/api/mon-espace/utilisateurs` | Active path for HR/membership; MANAGER can read, ADMIN can write |
| **Immeubles** | `/api/immeubles` | **Building CRUD; optional `?projectId=` filter** |
| Properties | `/api/properties` | Now supports `?projectId=&immeubleId=&type=&status=` filters |
| Tasks | `/api/tasks` | Default list = current user's tasks (assigneeId filter) |
| Documents | `/api/documents` | Cross-entity attachments |
| Super-admin societes | `/api/admin/societes` | SUPER_ADMIN only |
| Portal auth | `/api/portal/auth/**` | Magic-link flow; ROLE_PORTAL token; contactId as principal |

## Critical Rules

- **Never use `SocieteContext.getSocieteId()` without null-check.** Always use `requireSocieteId()` via `SocieteContextHelper`.
- For backend data changes, use additive Liquibase changesets only. Next available: **059**.
- Reuse existing package boundaries and patterns.
- Keep controllers on DTO contracts and error envelope (`ErrorResponse`, `ErrorCode`).
- Run relevant tests before finishing.
- `@Transactional` on IT test classes conflicts with `Propagation.REQUIRES_NEW` in `AuditEventListener` — **never annotate IT test classes with `@Transactional`**. Use unique email UIDs per test instead of rollback.
- E2E Playwright tests use `workers: 1` to prevent parallel login rate-limit races.
- `AppUserSociete.role` stores short form: `ADMIN`/`MANAGER`/`AGENT` (no `ROLE_` prefix). `AdminUserService.toSocieteRole()` strips it; `AuthService.toJwtRole()` adds it back.

## Seed Credentials

| Account | Email | Password | Role |
|---|---|---|---|
| ACME admin | `admin@acme.com` | `Admin123!Secure` | ROLE_ADMIN |
| Super admin | `superadmin@yourcompany.com` | `YourSecure2026!` | SUPER_ADMIN |

## E2E data-testid Map

Login form: `email`, `password`, `login-button`, `error-message`
Shell: `logout-button`
Contacts: `create-contact`, `firstName`, `lastName`, `save-button`
Tasks: `task-title` (form input), `task-submit` (submit button)

## Liquibase Changeset Chain (001–056)

| Range | Domain |
|---|---|
| 001–007 | Tenant/user bootstrap, contacts v1 |
| 008–015 | User roles, property, projects |
| 016–023 | Sale contracts, outbox, payments v1, media |
| 024–030 | Commission rules, portal tokens, reservations, lockout, GDPR, password fix |
| 031–035 | Multi-société: societe table, AppUserSociete, tenant→societe rename, migration, keys |
| 036–047 | User management: dedup email, indexes, version columns, extended fields, quotas, seed users, superadmin seed, rename tenant indexes |
| 048–049 | Task and document tables |
| 050 | RLS phase 1 (PostgreSQL Row-Level Security scaffolding) |
| 051 | RLS phase 2 — all domain tables + nil-UUID system bypass |
| 052 | ShedLock table for distributed scheduler locking |
| 053–055 | Project/société logo fields, contract templates |
| 056 | Immeuble (Building) table + property.immeuble_id FK |
| 057 | Schema hardening — reservation FK, RLS on immeuble, optimistic lock version |
| 058 | Vente pipeline — vente, vente_echeance, vente_document tables + RLS |

Next available changeset: **059**

## CI Pipeline Map

| Workflow | Trigger | Jobs |
|---|---|---|
| `backend-ci.yml` | Push/PR on `hlm-backend/**` | `unit-and-package` → `integration-test` (needs Docker) |
| `frontend-ci.yml` | Push/PR on `hlm-frontend/**` | `test-and-build` (ChromeHeadlessCI) |
| `e2e.yml` | Push/PR to `main` | docker compose full stack + `npx playwright test` |
| `docker-build.yml` | Push/PR to `main` on `hlm-*/**` | build backend image, build frontend image, `compose-smoke` |
| `snyk.yml` | Push/PR on `hlm-*/**`, weekly | OSS scan + SAST scan (skipped if `SNYK_TOKEN` absent) |
| `secret-scan.yml` | Push/PR on `hlm-*/**` | Regex audit (warn-only unless `SECRET_SCAN_ENFORCE=true`) |

E2E test flow in CI:
1. `.env` created with `JWT_SECRET`
2. `docker compose up -d --wait --wait-timeout 180` (builds & starts full stack)
3. `npm ci` + `npx playwright install chromium`
4. `npx playwright test` — starts `ng serve` on port 4200 via `webServer` config; proxies `/api` to Docker backend on port 8080

## Common CI Failure Patterns

| Symptom | Root Cause | Fix |
|---|---|---|
| IT test: `ExceptionInInitializerError` / `Could not find valid Docker environment` | Testcontainers can't find `/var/run/docker.sock` (WSL2 Docker Desktop) | Set `DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock` locally; works automatically on ubuntu-latest CI |
| IT test: FK violation on `REQUIRES_NEW` | `@Transactional` on IT test class — outer transaction not committed when AuditEventListener opens new connection | Remove `@Transactional`; add UID suffix to all emails in `@BeforeEach` |
| IT test: `uk_user_email` constraint | Hardcoded emails without UIDs collide across `@BeforeEach` invocations | `uid = UUID.randomUUID()...substring(0,8)` in `@BeforeEach`; append to all emails |
| Frontend: `npm ci` fails with chokidar conflict | `@angular-eslint/*` version doesn't match `@angular/cli` minor | All `@angular-eslint/*` packages must be `^19.2.0` (same minor as cli@19.2.0) |
| nginx: `host not found in upstream` at startup | `proxy_pass http://hlm-backend/` resolves DNS at config load before container registered | Use `resolver 127.0.0.11 valid=30s; set $backend http://hlm-backend:8080;` pattern |
| Backend startup: `JWT_SECRET` blank | `JwtProperties` has `@NotBlank` — app fails fast | Set `JWT_SECRET` env var (32+ chars) in `.env` and CI workflow env |
| E2E: login rate-limit races | Playwright parallel workers all log in simultaneously | `playwright.config.ts` must have `workers: 1` |
| E2E: wrong button clicked | Comma CSS selector `button[type="submit"]` matches unlabelled buttons | Use `data-testid` as primary selector; never use `button[type="submit"]` as fallback |

## Angular 19 Test Setup Quirks

- **Jasmine 5.x spy properties**: `jasmine.createSpyObj(name, methods, { prop: val })` sets `configurable: false` — `Object.defineProperty` to override throws `TypeError`. Solution: pass only methods to `createSpyObj`, then `Object.defineProperty(spy, 'prop', { get: () => var, configurable: true })` separately.
- **Guard specs**: Use `TestBed.runInInjectionContext(() => guardFn(...))` pattern for functional guards.
- **ChromeHeadlessCI**: `karma.conf.js` must define `customLaunchers.ChromeHeadlessCI` with `--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage`.

## Multi-Tenant Guard Checklist

When adding a new service method or repository query:

- [ ] Service: call `requireSocieteId()` as first line of any write/read method
- [ ] Repository: `WHERE societe_id = :societeId` on every query
- [ ] Domain entity: `societe_id UUID NOT NULL` column
- [ ] Liquibase: FK constraint `fk_<table>_societe` to `societe(id)`
- [ ] IT test: no `@Transactional` on test class; UID-based emails in `@BeforeEach`
- [ ] IT test for cross-société isolation: verify société B cannot see société A resources (expect 404)

## Current Backlog

See `tasks/IMPLEMENTATION_PLAN.md` — Wave 5 complete:
- Tasks 01–15: Security audit fixes + CI/CD ✅
- Tasks 16–19: Frontend tasks/documents/usermgmt + E2E ✅
- Task 20: Production readiness — Wave 4 hardening complete ✅
- Wave 5: After-deploy bug fixes ✅ (Immeuble entity, property filters, prospect auto-promotion, email AFTER_COMMIT, phone-or-email validation)
- Wave 6: UX hardening ✅ (no UUID exposure via pickers, reservation docs everywhere, pipeline KPI bar, project list card grid, project detail hero+KPIs+progress bars, `FRONTEND_BASE_URL` docker fix)
- Wave 7: Sales Pipeline + Buyer Portal ✅ (Vente entity/service/API on changeset 058, portal `/api/portal/ventes` endpoints, CRM vente list/detail UI, portal ventes tab, buyer magic-link invite `POST /api/ventes/{id}/portal/invite`)

### Wave 4 — Production Hardening (complete)

| Priority | Task | Files |
|---|---|---|
| P0 | PostgreSQL RLS phase 2 — all domain tables + nil-UUID bypass | `RlsContextAspect.java`, changeset `051-rls-phase2-all-tables.yaml` |
| P0 | Async boundary fix — SocieteContext propagation across @Async | `SocieteContextTaskDecorator.java`, `AsyncConfig.java` |
| P1 | ShedLock distributed scheduler lock | `ShedLockConfig.java`, `OutboundDispatcherScheduler.java`, changeset `052-shedlock-table.yaml` |
| P1 | Redis cache — PROJECTS_CACHE + SOCIETES_CACHE wired | `RedisCacheConfig.java` |
| P1 | OTel / Prometheus metrics endpoint | `application.yml` — `management.metrics`, `management.otlp` |
| P1 | Springdoc OpenAPI `@Tag` on all 21 controllers | All `*Controller.java` files |
| P2 | Self-service profile endpoint | `UserProfileController`, `UserProfileService`, `UserProfileResponse`, `UpdateProfileRequest` |
| P2 | Quota enforcement | `QuotaService` — wired into `InvitationService` + `AdminUserService` |
| P2 | Invitation rate limiting (10 req/h per admin) | `RateLimiterService.checkInvitation()`, `RateLimitProperties.invitation`, `UserManagementController` |

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

# WSL2 + Docker Desktop: Testcontainers needs the correct socket
export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
```
