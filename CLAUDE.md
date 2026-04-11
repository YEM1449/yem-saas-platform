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

### Current Modules (26)

audit, auth, commission, common, contact, contract, dashboard, deposit, document, gdpr, **immeuble**, media, notification, outbox, payments, portal, project, property, reminder, reservation, societe, task, **tranche**, user, usermanagement, **vente**

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
| **Ventes** | `/api/ventes` | Full sale pipeline CRUD; `PATCH /{id}/statut` enforces state machine |
| **Portal ventes** | `/api/portal/ventes` | Buyer read-only view; ROLE_PORTAL; returns vente + echeances + docs |
| **Vente portal invite** | `POST /api/ventes/{id}/portal/invite` | Sends magic-link email to buyer; ADMIN/MANAGER only |
| **Invitation flow** | `GET /auth/invitation/{token}` + `POST /auth/invitation/{token}/activer` | Validate + activate account; both in `permitAll()`; JWT set as httpOnly cookie |

## Critical Rules

- **Never use `SocieteContext.getSocieteId()` without null-check.** Always use `requireSocieteId()` via `SocieteContextHelper`.
- For backend data changes, use additive Liquibase changesets only. Next available: **060**.
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
| 059 | Tranche + generation — tranche table, immeuble.tranche_id, property.tranche_id + orientation, project location fields (adresse/ville/code_postal), project_generation_config (JSONB), project_generation_log |
| 060 | Reservation ref counter — reservation_ref_counter table, reservation_ref column on property_reservation |
| 061 | Task indexes — composite index (societe_id, assignee_id, status), partial index on due_date for open tasks |
| 062 | KPI snapshot — kpi_snapshot table with RLS, unique(societe_id, tranche_id) |
| 063 | Vente contract status — contract_status column on vente table |
| 064 | Vente ref counter — vente_ref_counter table, vente_ref column on vente table |
| 065 | Project professional fields — maitre_ouvrage, date_ouverture_commercialisation, tva_taux, surface_terrain_m2, prix_moyen_m2_cible |

Next available changeset: **066**

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

See `tasks/IMPLEMENTATION_PLAN.md` — Wave 10 complete:
- Tasks 01–15: Security audit fixes + CI/CD ✅
- Tasks 16–19: Frontend tasks/documents/usermgmt + E2E ✅
- Task 20: Production readiness — Wave 4 hardening complete ✅
- Wave 5: After-deploy bug fixes ✅ (Immeuble entity, property filters, prospect auto-promotion, email AFTER_COMMIT, phone-or-email validation)
- Wave 6: UX hardening ✅ (no UUID exposure via pickers, reservation docs everywhere, pipeline KPI bar, project list card grid, project detail hero+KPIs+progress bars, `FRONTEND_BASE_URL` docker fix)
- Wave 7: Sales Pipeline + Buyer Portal ✅ (Vente entity/service/API on changeset 058, portal `/api/portal/ventes` endpoints, CRM vente list/detail UI, portal ventes tab, buyer magic-link invite `POST /api/ventes/{id}/portal/invite`)
- Wave 8: Pipeline UX + Activation redesign ✅ (items below)
- Wave 9: Contact status lifecycle + Dashboard homepage ✅ (items below)
- Wave 10: Tranche + Bulk Project Generation ✅ (items below)
- Wave 11: UX + Performance Sprint (F1–F10) ✅ (items below)
- Wave 12: Owner KPIs + Template builder + UI polish ✅ (items below)

### Wave 8 — Pipeline UX + Activation Redesign (complete, 2026-04-05)

| Item | Files |
|---|---|
| Activation page premium redesign — split layout, 4 states (loading/form/error/done), role chip, expiry note | `activation.component.html`, `activation.component.css` |
| Backend VenteStatut state machine — transition guard `validateTransition()` | `VenteService.java:validateTransition()`, `InvalidVenteTransitionException.java`, `GlobalExceptionHandler.java` |
| `PipelineStepperComponent` — horizontal/vertical, 4 steps, ANNULE pill | `features/ventes/pipeline-stepper.component.ts/.css` |
| `AdvancePipelineDialogComponent` — context-sensitive forms per target statut, cancel mode | `features/ventes/advance-pipeline-dialog.component.ts/.html/.css` |
| Vente detail rewrite — templateUrl/styleUrl, stepper at top, advance dialog, invite card | `features/ventes/vente-detail.component.ts/.html/.css` |
| Deposit → Vente conversion banner — on prospect-detail for CONFIRMED deposits | `features/prospects/prospect-detail.component.html/.ts/.css` |
| Portal ventes upgrade — `PipelineStepperComponent`, `@if`/`@for`, paid/remaining totals | `portal/features/portal-ventes/portal-ventes.component.ts/.html/.css` |
| R2 EU endpoint comment in `application.yml` | `application.yml` |

### Wave 9 — Contact Lifecycle + Dashboard Homepage (complete, 2026-04-05)

| Item | Files |
|---|---|
| Backend: `VenteService.create()` auto-advances contact to `ACTIVE_CLIENT` on vente creation | `VenteService.java` |
| Backend: `VenteService.updateStatut()` auto-advances contact to `COMPLETED_CLIENT` on LIVRE | `VenteService.java` |
| Backend: `VenteResponse` enriched with `contactFullName` (denormalised for UI) | `VenteResponse.java`, `VenteService.java:toResponse()` |
| Backend: `GET /api/ventes?contactId=` filter — `VenteService.findByContactId()` | `VenteController.java`, `VenteService.java` |
| Frontend: `vente.service.ts` — added `contactFullName` to `Vente` interface, `listByContact()`, `uploadDocument()` | `vente.service.ts` |
| Frontend: Vente list — added Acquéreur column with link to contact detail | `vente-list.component.html` |
| Frontend: Vente detail — Acquéreur link in info card, document upload UI with file input | `vente-detail.component.ts/.html` |
| Frontend: Contact detail — Ventes tab (lazy-loaded via `listByContact()`) | `contact-detail.component.ts/.html` |
| Frontend: `HomeDashboardComponent` — KPI row, shortcut grid, recent ventes table, link to commercial dashboard | `home-dashboard.component.ts` |
| Frontend: Default CRM route changed `/app` → `dashboard` (was `properties`) | `app.routes.ts` |
| Frontend: Default portal route changed `contracts` → `ventes` | `app.routes.ts` |

### Wave 10 — Tranche + Bulk Project Generation (complete, 2026-04-07)

| Item | Files |
|---|---|
| DB: `tranche` table (phased delivery group), `project_generation_config` (JSONB wizard config), `project_generation_log` (audit) | `059-create-tranche-and-generation.yaml` |
| DB: `immeuble.tranche_id` FK, `property.tranche_id` + `property.orientation`, `project.adresse/ville/code_postal` | changeset 059 |
| Backend: `Tranche` entity + `TrancheStatut` enum (EN_PREPARATION → EN_COMMERCIALISATION → EN_TRAVAUX → ACHEVEE → LIVREE) | `tranche/domain/Tranche.java`, `TrancheStatut.java` |
| Backend: `PropertyType.PARKING` + `PropertyCategory.PARKING` added | `property/domain/PropertyType.java`, `PropertyCategory.java` |
| Backend: `ProjectGenerationService` — single-TX generation of Project + Tranches + Immeubles + Properties | `tranche/service/ProjectGenerationService.java` |
| Backend: `TrancheService` — CRUD + forward-only statut transition (InvalidTrancheTransitionException → 409) | `tranche/service/TrancheService.java` |
| Backend: `POST /api/projects/generate` (ADMIN/MANAGER), `GET /api/projects/{id}/tranches`, `GET /{id}/tranches/{tid}`, `PATCH /{id}/tranches/{tid}/statut` | `tranche/api/TrancheController.java` |
| Backend: `GlobalExceptionHandler` updated — `TrancheNotFoundException` (404), `InvalidTrancheTransitionException` (409) | `GlobalExceptionHandler.java` |
| Backend IT: `ProjectGenerationIT` — auth, validation, happy path (units count), parking, tranche statut lifecycle | `tranche/ProjectGenerationIT.java` |
| Frontend: `TrancheService` — `generate()`, `listByProject()`, `getById()`, `advanceStatut()` | `features/projects/tranche.service.ts` |
| Frontend: 5-step `ProjectCreateWizardComponent` (project info → tranches → buildings → lots → validation) | `features/projects/project-create-wizard/` |
| Frontend: `/app/projects/new` route wired; "Créer un projet" button now navigates to wizard | `app.routes.ts`, `projects.component.ts` |
| E2E: `project-wizard.spec.ts` — wizard navigation, step validation, full happy-path, duplicate name error | `e2e/project-wizard.spec.ts` |

### Wave 11 — UX + Performance Sprint (complete, 2026-04-10)

| Item | Files |
|---|---|
| F5: Reservation unique ref (RES-YEAR-CODE-SEQ05) | `changeset 060`, `ReservationRefGenerator`, `Reservation.reservationRef`, `ReservationResponse.reservationRef` |
| F1: Contact validation groups (RESERVATION/VENTE stages) | `ContactValidationStage`, `ContactCompletenessService.validateForStage()`, `ClientIncompleteException` |
| F4: Date coherence enforcement | `DateCoherenceValidator`, `DateCoherenceException`, wired in `VenteService.create()`, `addEcheance()`, `updateEcheanceStatut()` |
| F8: Task DB indexes + due-now endpoint | `changeset 061`, `GET /api/tasks/due-now` → `DueTaskDto` |
| F10: Outbox attempt counter starts at 1 | `outbox.component.html` — `retriesCount + 1` |
| F3: KPI auto-update via Spring events | `KpiSnapshot`, `KpiSnapshotRepository`, `SaleFinalizedEvent`, `EcheanceChangedEvent`, `KpiComputationService`, `GET /api/kpis/tranche/{id}`, `changeset 062` |
| F2: Contract generation guard before signing | `ContractStatus` enum, `changeset 063`, `VenteService.generateContract()` / `signContract()`, `ContractNotGeneratedException`, `POST /api/ventes/{id}/contract/generate` + `sign` |
| F6: Reservation detail page | `ReservationDetailResponse`, `GET /api/reservations/{id}/detail`, `ReservationDetailComponent`, route `/app/reservations/:id` |
| F7: Create sale from reservation | `VentePrefillResponse`, `GET /api/reservations/{id}/vente-prefill`, `VenteCreateComponent`, route `/app/ventes/new?reservationId=` |
| F9: Persistent task due notifications | `NotificationPollingService` (60s interval, `GET /api/tasks/due-now`), `NotificationToastComponent` (max 3, amber border, "Marquer fait") mounted in `ShellComponent` |

### Wave 12 — Multi-task Sprint: Owner KPIs + Template Builder + UI Polish (complete, 2026-04-11)

Four distinct user asks delivered as four commits on `Epic/ProjectCreationUpgrade-TrancheImplementation`:

| Commit | Scope | Files |
|---|---|---|
| A (4237c78) | Tasks page stuck-loading fix + reservation→sale verify | `hlm-frontend/src/app/core/models/page-response.model.ts` (flattened to match Spring `Page<T>` JSON shape — `content/number/size/totalElements/totalPages`), `hlm-frontend/src/app/features/tasks/task.model.ts` (flat `TaskPage`), `tasks.component.ts:65-66` and `admin-users.component.ts:63-64` updated from `page.page.totalPages` → `page.totalPages`. Reservation→sale button already wired via Wave 11 F7 — smoke-verified only. |
| B (39c3366) | Dashboard owner KPIs — cancellation rate 90d, avg ticket LIVRE, conversion rate 30d, encaissé mois courant, top-5 agent leaderboard | Backend repo queries: `VenteRepository.countCreatedInPeriod/countByStatutInPeriod/avgPrixVenteByStatut/topAgentsByCA`, `VenteEcheanceRepository.sumPaidInPeriod`, `ReservationRepository.countCreatedInPeriod`. DTO: `HomeDashboardDTO` + `AgentLeaderboardRow` record. Service: `HomeDashboardService` section 11 (ADMIN/MANAGER only for leaderboard). Frontend: `home-dashboard.component.html` SECTION 3.5 — 4 KPI cards + leaderboard card with podium `::before` emoji. |
| C (40fe1e0) | Contract template drag-drop variable builder | `hlm-frontend/src/app/features/templates/template-editor.component.ts` rewrite — 4 variable groups (societe/property/buyer/contract, grouped palette with icons), drag-drop via HTML5 `dataTransfer`/`caretPositionFromPoint` with `setRangeText` caret insertion, live `${model.*}` counter, search filter, toast flash. Backing textarea keeps Thymeleaf `${model.varName}` tokens intact for server-side rendering. |
| D (75de0d7) | UI/UX polish pass — skeleton loaders + empty-state refresh | `hlm-frontend/src/styles.css` — `.skeleton` primitives (shimmer keyframe + `prefers-reduced-motion`), `.skeleton-grid`/`.skeleton-stack` layout helpers, refreshed `.empty-state` with dashed border + `.empty-state-actions` row. Applied to `tasks.component.html` (5-row skeleton stack, full empty state with CTA) and `home-dashboard.component.html` (skeleton-grid matching KPI layout). Budget bump: `angular.json` `anyComponentStyle` → 20kB error (from 16kB) to accommodate the expanded dashboard CSS. |

### Tranche State Machine (as-implemented)
```
EN_PREPARATION → EN_COMMERCIALISATION → EN_TRAVAUX → ACHEVEE → LIVREE
```
`TrancheService.validateTransition()` enforces sequential forward-only steps.
Invalid transitions → HTTP 409 `INVALID_STATUS_TRANSITION`.

### Property Hierarchy (as-implemented, Wave 10)
```
Société
  └── Project (adresse, ville, code_postal added)
        └── Tranche (phased delivery group — has dateLivraisonPrevue)
              └── Immeuble (building — tranche_id FK)
                    └── Property (unit — tranche_id + orientation FKs)
```

### Vente State Machine (as-implemented)
```
COMPROMIS ──→ FINANCEMENT ──→ ACTE_NOTARIE ──→ LIVRE
    │               │               │
    └───────────────┴───────────────┴──→ ANNULE (terminal)
LIVRE → (terminal)
```
`VenteService.validateTransition()` enforces this. Invalid transitions → HTTP 409 `INVALID_STATUS_TRANSITION`.

### Infrastructure Notes (Production)
- **R2 EU endpoint**: `MEDIA_OBJECT_STORAGE_ENDPOINT=https://<account-id>.eu.r2.cloudflarestorage.com` — using the global endpoint on an EU bucket causes 403
- **Frontend URL**: `FRONTEND_BASE_URL=https://yem-hlm.youssouf-mehdi.workers.dev` (default already set in application.yml)
- **Portal URL**: `PORTAL_BASE_URL=https://yem-hlm.youssouf-mehdi.workers.dev` (default already set)
- **Activation flow**: `GET /auth/invitation/{token}` (validate) → `POST /auth/invitation/{token}/activer` (activate); JWT set as httpOnly cookie; both already in `permitAll()`

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
