# CLAUDE.md

Auto-load guide for Claude Code. Captures operating rules, architecture context, and the current implementation backlog.

## Audit Checkpoint — Consolidated Audit (2026-06-13)

**→ Source of truth: `docs/audit/audit-report-2026-06-13.md` + `docs/audit/action-plan-2026-06-13.md`**
(Consolidation of audit 2026-06-03 + cross-functional product review 2026-06-12 + fresh code scan 2026-06-13. Supersedes `audit-report-2026-06-03.md`, `action-plan-2026-06-03.md`, and `team-review-2026-06-12.md` for current state — those files are retained for history.)

**5 open items (2026-06-13, updated):** 0 Critical · 0 Major · all A+B+C+D resolved. Remaining: 5 code-quality (E). Next: Phase E.

**Solid:** multi-société isolation (`requireSocieteId()` ×280 + RLS phase 2), JWT in httpOnly cookie (no token in localStorage), 0 SQL-injection/mass-assignment surface, Vente/Tranche state machines guarded (→409), 3D WebGL hygiene (full dispose, DPR≤1.5, Page Visibility).

**Fixed (2026-06-03):**
- **F-001 (P0, RG-B03) ✅** `VenteService.create()` now calls `existsBySocieteIdAndPropertyIdAndStatutNot(..., ANNULE)` → `PropertyAlreadyEngagedException` (409 `PROPERTY_ALREADY_ENGAGED`). Concurrency backstop: changeset **075** `uk_vente_active_property` partial unique index on `vente(property_id) WHERE statut <> 'ANNULE'`.
- **F-002 (P1) ✅** `VenteServiceTest` (6 Mockito tests: RG-B03 guard, property-status precondition, state machine, ANNULE-needs-motif) + `VenteControllerIT` (8 IT: 401, create→RESERVED, **409 double-vente**, ANNULE frees property, valid/invalid transitions, cross-société 404, 404 unknown property). Unit suite: **108 pass** (was 102).

**Fixed P2 (2026-06-03):**
- **F-003 → requalifié faux positif** : `CrossSocieteAccessException`(403) ne couvre que le contexte manquant (société/user/principal portail) ; l'accès ressource cross-société renvoie déjà 404. Aucun changement code.
- **F-004 ✅** `GlbValidator` (RG-E05) valide les octets GLB à la confirmation (magic glTF + version 2 + `KHR_draco_mesh_compression`) → 422 `INVALID_GLB_FILE`. Property `app.viewer3d.validate-glb-binary` (true prod, false test) ; 7 tests `GlbValidatorTest`.
- **F-010 ✅** Fallback 3D `no-model` gated `canManageModel` (ADMIN/MANAGER) sinon message informatif.

**Fixed P2/P3 batch 2 (2026-06-04):**
- **F-007 ✅** `template-editor` (23 `*ngIf`) + `mesh-mapping-admin` migrated to `@if`/`@for`; 0 legacy control-flow remaining; build green.
- **F-008 → faux positif** : 222 `.subscribe()` = HttpClient one-shot (auto-complétés) ; flux persistants (notification-polling 60s, keep-alive, viewer-3d) déjà détruits (`takeUntil(destroy$)`/`unsubscribe`). 0 fuite réelle.
- **F-005 ⏳ en cours** : `QuotaServiceTest` (8), `ContactCompletenessServiceTest` (5) ajoutés (+ `VenteServiceTest`). Unit suite: **128 pass**.
- **F-011 ⏳ en cours** : `absorption.spec.ts` (KPI canonique).
- **F-013 ✅** bannière CURRENT STATE en tête de `.sprint-state.md`.

**Fixed (2026-06-13):**
- **F-006 ✅** `GET /api/ventes` + `GET /api/properties` migrated to `Page<T>` → `PageResponse.of()` (`@PageableDefault` 20/50); contacts was already paginated. FE: `PagedResult<T>` type + `listPage()`; bounded callers keep capped `list()`. `GET /api/notifications`: `@Max(200) @Min(1)` added at controller layer (service already clamped). 26 sub-resource endpoints remain `List<T>` (FK-bounded). `NotificationControllerTest` (5 tests); unit suite: **200 pass**.

**Fixed — Phase A (2026-06-13):**
- **A-001 ✅** `EcheanceStatut.ANNULEE` ; `cancelAllPendingByVente()` `@Modifying` ; appelé dans `updateStatut(ANNULE)` + `exerciseRetractation()` ; trésorerie exclut ANNULEE.
- **A-002 ✅** Export CSV/PDF + TVA (Prix HT, Taux, Prix TTC via `TvaCalculator`) ; `ReportExportService` batch-load `findAllById`, `ventes-report.html` 9 colonnes.
- **A-003 ✅** Changeset **087** `responsable_user_id` sur `reserve_livraison` ; `ReserveLivraisonProjectController` `GET /api/projects/{id}/reserves` (ADMIN/MANAGER).
- **A-004 ✅** Guard `prixVente ≤ 0` → `PrixVenteInvalideException` 422 `PRIX_VENTE_INVALIDE`.

**Fixed — Phase B (2026-06-13):**
- **B-001 ✅** `MarketConfig.getPenaliteRetardJournalierMad()` (défaut 500 MAD/j, configurable) ; `joursRetard`+`penaliteAccumulee` dans `VenteResponse`+`VenteService.toResponse()` ; `countVentesEnRetardLivraison`+`sumRetardJoursLivraison` dans `VenteRepository` ; `ventesEnRetardLivraison`+`penaliteRetardTotale` dans `TresorerieDashboardDTO`+Service ; section `.penalite` dans PV livraison.
- **B-002 ✅** `DataRetentionScheduler` 3 passes (prospect 730j, acquéreur 1825j, VEFA 3650j) ; `findRetentionCandidatesByStatuses()` ; `docs/legal/data-retention.md`.
- **B-003 ✅** `docs/legal/pdf-review-checklist.md` (27 items Art.618-3/618-13/618-17, mentions obligatoires, actions correctives).
- **B-004 ✅** `@ReadAudit` AOP aspect (`SensitiveDataReadEvent` → `AuditEventListener.onSensitiveDataRead()` REQUIRES_NEW) ; annoté sur `getLegalDetails()` + `getCommercial()`.
- **B-005 ✅** `ComplianceController` `GET/PATCH /api/mon-espace/compliance` (ADMIN) pour saisie `numeroCndp`+`dateDeclarationCndp`.

**Deferred (justifié, P2/P3):** F-009 (232 styles inline — refactor de masse), F-015 (CD — besoin secrets déploiement), F-012 (soft-delete — décision de conception).

Next available changeset: **086** (084 = client_groupe_lien #005, 085 = remboursement #028).

## Wave 12 — Conformité VEFA Loi 44-00 (Maroc) — complete (2026-06-11)

Branch `Epic/Dashboard-UIUX-improvement`. The vente pipeline was **replaced** with the VEFA
state machine (user decision): `PROSPECT→OPTION→RESERVE→EN_RETRACTATION→ACOMPTE→COMPROMIS→FINANCEMENT→ACTE→LIVRE_AVEC_RESERVES→RESERVES_LEVEES→LIVRE_DEFINITIF`, `ANNULE` terminal. `FINANCEMENT` kept; ASCII identifiers; `ACTE_NOTARIE→ACTE`, `LIVRE→LIVRE_DEFINITIF` migrated (changeset 076). 151 unit tests.

- **P1 OPTION + rétractation** (ch. 076-077): `createOption` (1-72h hold), `confirmReservation`
  (deposit ≤5% Art.618-4 → 422), `exerciseRetractation` (7-day window → 409), `VenteVefaScheduler`
  (hourly sweeps). `MarketConfig`/`MarketConstants`/`TvaCalculator` in `legal/` package.
- **P1 livraison avec réserves** (ch. 078): `reserve_livraison`, `recordDelivery`/`liftReserve`.
- **P2 échéancier légal** (ch. 079): `generateEcheancierLegal` (7 calls Art.618-17), cumul≤prix guard.
- **P3** (ch. 080-082): contact legal identity (isolated `/contacts/{id}/legal`), `co_acquereur`
  (`/api/ventes/{id}/co-acquereurs`), `dossier_financement` (`/api/ventes/{id}/dossier-financement`).
- **P4** (ch. 083): property commercial fields + Moroccan VAT (`TvaCalculator`, prix TTC never stored,
  `/api/properties/{id}/commercial`); 3D floor filter + presentation mode.
- **P5**: legal PDF generation (contrat réservation, PV livraison) via `DocumentGenerationService`.
- **P6**: `GET /api/dashboard/tresorerie` (cash + VEFA alerts).

**Pattern note:** legal/VEFA fields were added via **isolated DTOs + dedicated endpoints** (not by
extending the big positional `CreateContactRequest`/`PropertyCreateRequest` records) to avoid breaking
their many call-sites. Session manifest: `.wave12-session.json`. Legal ref: `docs/legal/`.

**Deferred (CI/backlog):** IT tests for the VEFA pipeline (need Docker — run in CI), P5 quittances
appels de fonds, P6 push notifications, VEFA E2E full flows (CI-gated).

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
- For backend data changes, use additive Liquibase changesets only. Next available: **076**.
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
| 066–070 | Pipeline intelligence, commercial targets, legal financing, portal docs, user quota |
| 071 | project_3d_model table (GLB file key + upload metadata, RLS, unique per societe+projet) |
| 072 | lot_3d_mapping table (mesh↔lot links, RLS, unique per societe+projet+mesh) |
| 073 | Vente legal field rename — `date_fin_delai_sru` → `date_fin_delai_reflexion`, `date_limite_condition_credit` → `date_limite_financement` |
| 074 | Reservation hardening — `raison_annulation VARCHAR(100)` + `notified_expiring_soon BOOLEAN` on `property_reservation` |
| 075 | RG-B03 — `uk_vente_active_property` partial unique index on `vente(property_id) WHERE statut <> 'ANNULE'` (one active vente per property) |
| 076 | Wave 12 — VenteStatut VEFA rename (ACTE_NOTARIE→ACTE, LIVRE→LIVRE_DEFINITIF) + data migration |
| 077 | Wave 12 — vente option_expire_at + retractation_exercee_at (OPTION + rétractation) |
| 078 | Wave 12 — reserve_livraison table (RLS) — delivery reserves |
| 079 | Wave 12 — vente_echeance legal fields (etape/pct_prevu/base_legale, Art. 618-17) |
| 080 | Wave 12 — contact legal columns (CIN/passeport/situation/type_acquereur…) |
| 081 | Wave 12 — co_acquereur table (RLS, unique société+vente) |
| 082 | Wave 12 — dossier_financement table (1:1 vente, RLS, statut workflow) |
| 083 | Wave 12 — property commercial columns (prix_ht, tva_taux, surfaces, charges…) |

Next available changeset: **086**

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
- Portal magic-link pipeline fix ✅ (2026-04-14, items below)
- Wave 13: 3D Visualiseur ✅ (2026-04-25, items below) <!-- ✅ CHECKPOINT: feat(3d): upload-url workflow, generatePresignedPutUrl, model-upload-admin component, 10 Mockito + 13 IT + 10 E2E tests, *ngIf→@if fix -->
- Wave 14: Business Rules Hardening ✅ (2026-04-25, items below)
- Wave 15: 2D Plan de Commercialisation ✅ (2026-04-25, items below)

### Wave 13 — 3D Visualiseur + Dashboard 3D Tab (complete, 2026-04-25)

| Item | Files |
|---|---|
| DB: `project_3d_model` table (GLB key, RLS, unique societe+projet) | `071-project-3d-model.yaml` |
| DB: `lot_3d_mapping` table (mesh↔lot, RLS, unique societe+projet+mesh) | `072-lot-3d-mapping.yaml` |
| Backend: `Project3dModel` + `Lot3dMapping` JPA entities | `viewer3d/domain/` |
| Backend: Repos + 2 new methods on `PropertyRepository` (bulk fetch + portal access check) | `viewer3d/repo/`, `PropertyRepository.java` |
| Backend: `Project3dService` — upsert, getModel, getStatusSnapshot (10 s Caffeine), bulkUpsert, portalAccessCheck | `viewer3d/service/Project3dService.java` |
| Backend: DTOs — `Create3dModelRequest`, `Project3dModelResponse`, `Lot3dMappingDto`, `Lot3dStatusDto`, `BulkMappingRequest` | `viewer3d/api/dto/` |
| Backend: `Project3dController` — POST/GET /api/projects/{id}/3d-model, GET /3d-properties-status, PUT /mappings | `viewer3d/api/Project3dController.java` |
| Backend: `PortalProject3dController` — ROLE_PORTAL read-only, access-checked per contact vente | `viewer3d/api/PortalProject3dController.java` |
| Backend: S3 pre-signed URL — `generatePresignedUrl(key, ttl)` + `generatePresignedPutUrl(key, ttl)` on `MediaStorageService` + `ObjectStorageMediaStorage` | `MediaStorageService.java`, `ObjectStorageMediaStorage.java` |
| Backend: `POST /api/projects/{id}/3d-model/upload-url` — two-step GLB upload (step 1); validates dracoCompressed=true, returns pre-signed PUT URL + fileKey | `Project3dController.java`, `UploadUrlRequest`, `UploadUrlResponse` |
| Tests: `Project3dServiceTest` (10 Mockito), `Project3dControllerIT` (13 Testcontainers) | `viewer3d/` test directory |
| Frontend: `ModelUploadAdminComponent` — two-step upload UI, progress bar, .glb + 50 MB validation, error/done states | `model-upload-admin/` |
| Frontend: `Viewer3dApiService.requestUploadUrl()` + `confirmUpload()` | `viewer-3d-api.service.ts` |
| E2E: `viewer-3d.spec.ts` (10 Playwright tests) registered in playwright.config.ts | `e2e/viewer-3d.spec.ts` |
| Backend: `LOT_STATUS_3D_CACHE` (10 s TTL) added to `CacheConfig` | `CacheConfig.java` |
| Backend: `Project3dModelNotFoundException` (404) registered in `GlobalExceptionHandler` | both files |
| Backend: `s3-presigner:2.27.21` added to pom.xml | `pom.xml` |
| Frontend: `three@0.165.1` + `@types/three@0.165.0` + Draco assets in `angular.json` | `package.json`, `angular.json` |
| Frontend: `modules/viewer-3d/` — models, services (ThreeEngineService, ModelLoaderService, LotMappingService, Viewer3dApiService) | `modules/viewer-3d/services/` |
| Frontend: `ProjectViewer3dComponent` — loading skeleton, GLB streaming, colour-coding, 30 s poll, hover tooltip, click→CustomEvent, keyboard Tab nav, portal read-only guard | `project-viewer-3d/` |
| Frontend: `LotTooltip3dComponent` — floating overlay with ref/surface/price | `lot-tooltip-3d/` |
| Frontend: `Dashboard3dTabComponent` — statut filter, KPI overlay panel, PDF export | `dashboard-3d-tab/` |
| Frontend: routes wired in `app.routes.ts` — `/app/projets/:projetId/viewer-3d` + `/app/dashboard/commercial/3d` | `app.routes.ts` |

**3D status colour mapping** (PropertyStatus → display):
- DRAFT, ACTIVE → DISPONIBLE (#3B82F6)
- RESERVED → RESERVE (#F59E0B)
- SOLD → VENDU (#10B981)
- WITHDRAWN, ARCHIVED → LIVRE (#6B7280)

**Portal 3D access rule**: `PortalProject3dController` returns 404 unless the portal user (contactId) has ≥1 vente for a property mapped in this project's `lot_3d_mapping`.

**Adding a dashboard 3D tab to any page**: embed `<app-dashboard-3d-tab [projetId]="...">` — standalone component, lazy-loads Three.js.

---

### Wave 14 — Business Rules Hardening (complete, 2026-04-25)

Five P1 production fixes. 102 unit tests pass. Changeset 073–074.

| Item | Files |
|---|---|
| Property lifecycle fix — `VenteService.create()` keeps property `RESERVED` (not `SOLD`); `ACTE_NOTARIE` triggers `SOLD`; `ANNULE` releases back to `ACTIVE` | `VenteService.java`, `PropertyCommercialWorkflowService.java` |
| French SRU field rename (changeset 073) — `date_fin_delai_sru` → `date_fin_delai_reflexion`, `date_limite_condition_credit` → `date_limite_financement`; `DESISTEMENT_SRU` → `DESISTEMENT_ACHETEUR`; periods from `@Value` config | `Vente.java`, `VenteResponse.java`, `UpdateFinancingRequest.java`, `073-vente-rename.yaml` |
| 3D viewer — `WITHDRAWN` → `RETIRE` (was `LIVRE`); added `retire` count to Dashboard3dTab | `Project3dService.java:236`, `lot-3d-status.model.ts`, `dashboard-3d-tab.component.ts` |
| Quota enforcement — `QuotaService.enforceBienQuota/enforceContactQuota/enforceProjectQuota` wired into `PropertyService`, `ContactService`, `ProjectService`, `ProjectGenerationService` | `QuotaService.java`, error codes `QUOTA_BIENS_ATTEINT`, `QUOTA_CONTACTS_ATTEINT`, `QUOTA_PROJETS_ATTEINT` |
| Reservation cancellation reason + expiry warning (changeset 074) — `raison_annulation`, `notified_expiring_soon`; `CancelReservationRequest`; `RESERVATION_EXPIRING_SOON` notification; `runExpirySoonCheck()` scheduler | `Reservation.java`, `ReservationService.java`, `ReservationExpiryScheduler.java`, `074-reservation-hardening.yaml` |
| Audit doc enhancement — `business-rules-audit.md` §6.3, §7.3, §9 updated; §11–14 added (financial controls, Moroccan legal, alert governance, implementation log) | `docs/spec/business-rules-audit.md` |

**Frontend updates**: `vente.service.ts` fields + labels; `advance-pipeline-dialog` form labels; `reservation.service.ts` `raisonAnnulation`.

---

### Wave 15 — 2D Plan de Commercialisation (complete, 2026-04-25)

Floor-stack building view embedded in project detail as a tab. No new DB changeset (DTO-only backend changes).

| Item | Files |
|---|---|
| Backend: `ImmeubleResponse` now exposes `trancheId` | `immeuble/api/dto/ImmeubleResponse.java` |
| Backend: `PropertyResponse` now exposes `orientation` + `trancheId` | `property/api/dto/PropertyResponse.java` |
| Frontend: `Immeuble` interface adds `trancheId: string \| null` | `features/immeubles/immeuble.service.ts` |
| Frontend: `Property` interface adds `orientation: string \| null` + `trancheId: string \| null` | `core/models/property.model.ts` |
| Frontend: `BuildingViewComponent` — tranche pager, building tabs, floor stack, status legend bar, absorption %, clickable unit cards, property detail panel, parcours juridique stepper | `features/projects/building-view/` (3 files) |
| Frontend: `ProjectDetailComponent` — "Aperçu" / "Plan de commercialisation" tab bar; building view embedded in plan tab | `project-detail.component.ts/.html/.css` |

**Status colour mapping** (2D view, matches HLM UI Kit v2):
- ACTIVE → Disponible (solid green #22c55e)
- DRAFT → Brouillon (beige diagonal hatch)
- RESERVED → Réservé (solid orange-red #ea580c)
- SOLD → Vendu (solid dark #1e293b)
- WITHDRAWN / ARCHIVED → Retiré (gray diagonal hatch)

**Absorption formula** (canonical, single source of truth — frontend `core/utils/absorption.ts`, matches backend `HomeDashboardDTO`): `SOLD / (ACTIVE + RESERVED + SOLD) × 100`. Used by project cards, project Aperçu headline, building view, and the dashboard anchor. (Was previously `(SOLD + RESERVED) / (total − DRAFT)` in the building view — unified 2026-06-02.)

**Data flow**: `forkJoin(tranches, immeubles)` on init → filter immeubles by `trancheId` → `PropertyService.list({ immeubleId })` on building select → `groupBy(floorNumber)` → floors sorted descending.

**Detail panel**: floor-badge (status-coloured), prix, prix/m², exposition, parkings, chambres, parcours juridique 4-step stepper (RESERVED→step1, SOLD→step2), "Créer vente" + "Fiche bien" action buttons.

---

### Portal Magic-Link Pipeline Fix (complete, 2026-04-14)

Three bugs prevented buyers from reaching their portal after clicking "Accéder à mon espace":

| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Buyers landed on CRM `/login` (no password) | `/portal/verify` route missing in `app.routes.ts`; Angular wildcard redirected there | Added `{ path: 'verify', loadComponent: PortalVerifyComponent }` as public sibling of `login` in portal children |
| Post-verification redirect went to `/portal/contracts` (wrong default) | `PortalLoginComponent.verifyToken():65` hard-coded `/portal/contracts` | Changed to `navigateByUrl('/portal')`; shell's `redirectTo:'ventes'` handles the tab |
| Portal auth pages completely unstyled | `PortalLoginComponent` had no `styleUrl`; CSS classes unresolved | Added `portal-login.component.css` with full design-token-based styles + `styleUrl` in decorator |

New component: `PortalVerifyComponent` (`portal/features/portal-verify/`) — dedicated single-purpose magic-link handler. Shows spinner while verifying → redirects to `/portal` on success → branded error + "Demander un nouveau lien" on failure. `PortalLoginComponent` keeps `?token=` fallback for old email links.

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

**Two distinct "clôtures" (don't confuse them):**
- **Clôture commerciale = `ACTE_NOTARIE`** — the unit is legally sold. `PropertyCommercialWorkflowService` sets property `SOLD` at this stage (`VenteService.java:200`). The **absorption rate** is based on property `SOLD`, so absorption measures the *commercial* close (acte notarié). `ANNULE` releases the property back to `ACTIVE`.
- **Clôture livraison = `LIVRE`** — the unit is delivered. `caLivre` ("CA réalisé") counts revenue **only at `LIVRE`** (`HomeDashboardDTO.java:36`). Revenue recognition is at handover, not at the notarial act (conservative).
- **Terminal states**: only `LIVRE` (won) and `ANNULE` (lost) leave the active pipeline (`statut NOT IN ('LIVRE','ANNULE')`). **`ACTE_NOTARIE` is still "active pipeline."**
- **Value tunnel** (dashboard Dirigeant tab, frontend-computed from `pipelineData().stages`): *CA en cours* (COMPROMIS+FINANCEMENT) → *CA acté* (ACTE_NOTARIE, clôture commerciale) → *CA livré* (LIVRE, clôture livraison). `expectedClosingDate` is a forecast **date**, not a state.

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
