# Prompt Playbook — HLM CRM (AI Guide)

> Recipes for common tasks. Each entry: goal → exact steps → gotchas.

---

## Adding a new domain entity

1. **Liquibase changeset** (`NNN-description.yaml`):
   - Table with `societe_id UUID NOT NULL REFERENCES societe(id)`
   - FK constraint `fk_<table>_societe`
   - Index on `societe_id`
   - RLS policy (or add to changeset 051)
2. **JPA entity** in `<module>/domain/`:
   - `@Column(name = "societe_id", nullable = false)` field
   - No `@ManyToOne` on `societe` — just the UUID column
3. **Repository** in `<module>/repo/`:
   - `findAllBySocieteId(UUID societeId)`
   - All other query methods include `societeId` parameter
4. **Service** in `<module>/service/`:
   - `requireSocieteId()` as first line of every public method
5. **Controller** + **DTOs** in `<module>/api/`:
   - Request/Response records (Java records preferred)
   - `@PreAuthorize` on class or method level
6. **Security**: add path to `SecurityConfig` in correct tier
7. **IT test**: `*IT.java` — no `@Transactional`, uid-suffix emails

---

## Adding a new API endpoint to an existing module

1. Add method to controller — check `@PreAuthorize` matches role requirements
2. Add service method — start with `requireSocieteId()`
3. Add repository method — include `societeId` in every query
4. Write IT test verifying:
   - Happy path (200/201)
   - Cross-société isolation (autre société → 404)
   - Role that shouldn't access (403)
5. If changing response shape: update frontend `*.service.ts` interface

---

## Adding a new Angular feature

1. Create component in `features/<module>/`
2. Add route in `app.routes.ts` (lazy-loaded preferred):
   ```typescript
   { path: 'my-feature', loadComponent: () => import('./features/my-module/my.component').then(m => m.MyComponent) }
   ```
3. Add nav link in shell component (`app-shell.component.html`)
4. Add `data-testid` to all interactive elements
5. Build check: `npx ng build --configuration=ci` → 0 errors

Template rules to follow:
- Move regex to component getter — never `/pattern/.test()` in template
- Move arrow functions to component method — never `items.filter(x => ...)` in template
- Use `@if`/`@for` not `*ngIf`/`*ngFor`

---

## Adding a Playwright E2E test

1. Create or extend `hlm-frontend/e2e/<feature>.spec.ts`
2. Add a project to `playwright.config.ts` if new file:
   ```typescript
   { name: 'my-feature-tests', testMatch: /my-feature\.spec\.ts/ }
   ```
3. Prefix all `page.request` API calls:
   ```typescript
   const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';
   ```
4. Use `data-testid` selectors — never `button[type="submit"]` as fallback
5. For portal tests: use `page.route()` to mock API responses (no real magic-link in E2E)
6. Run: `npx playwright test <file>.spec.ts --headed` to verify locally

---

## Changing a contact/vente status

**Vente**: `PATCH /api/ventes/{id}/statut { "statut": "FINANCEMENT" }`
- Enforced by `VenteService.validateTransition()`
- Invalid → 409 `INVALID_STATUS_TRANSITION`

**Contact (manual)**: `PATCH /api/contacts/{id}/status { "status": "QUALIFIED_PROSPECT" }`

**Contact (auto)**: Triggered by vente events:
- Vente created → `ACTIVE_CLIENT`
- Vente → `LIVRE` → `COMPLETED_CLIENT`

---

## Uploading a file

**Vente document**: `POST /api/ventes/{id}/documents` (multipart/form-data, field `file`)  
**Property media**: `POST /api/properties/{id}/media` (multipart/form-data)  
**Project logo**: `POST /api/projects/{id}/logo` (multipart/form-data)  

All uploads go to S3/R2 via `MediaService`. The entity record stores a key, not a URL.  
Download: `GET /api/documents/{id}/download` (CRM) or the portal equivalent.

---

## Debugging IT test failures

| Symptom | Likely cause | Fix |
|---|---|---|
| 500 instead of 201 on POST | `@Transactional` on test class | Remove it |
| 401 on login | User has no `AppUserSociete` entry | Add membership row |
| `uk_user_email` constraint | Hardcoded email used across tests | Add uid suffix |
| 409 on status transition | Invalid state machine step | Check allowed transitions |
| `ExceptionInInitializerError` | Testcontainers can't find Docker socket | Set `DOCKER_HOST` |
| 400 on password field | Password < 12 chars or missing character class | Use `Admin123!Secure` pattern |

---

## Debugging Angular build errors

| Error | Cause | Fix |
|---|---|---|
| `NG5002: Parser Error: Unexpected token /` | Regex literal in template | Move to component getter |
| `NG2: Property 'x' does not exist` | Missing property in component | Add getter or field |
| `NG8001: 'app-foo' is not a known element` | Component not imported | Add to `imports: []` |
| `npm ci` fails with chokidar conflict | `@angular-eslint/*` version mismatch | Align all to same minor as `@angular/cli` |

---

## Debugging E2E failures

| Symptom | Likely cause | Fix |
|---|---|---|
| `page.request.post()` returns 501 or HTML | Python SPA server (port 4200, GET-only) | Use `${API_BASE}/api/...` with `PLAYWRIGHT_API_BASE=http://localhost:8080` |
| Test not discovered | Spec file not matched by any project | Add project entry to `playwright.config.ts` |
| Portal tests fail auth | No real portal JWT | Use `page.route()` to mock `/api/portal/**` |
| Login rate-limit hit | Parallel workers all login simultaneously | `playwright.config.ts` must have `workers: 1` |
| Wrong button clicked | `button[type="submit"]` matches multiple | Use `data-testid` selector |
| Activation URL 404 | Using `/activation/:token` path | Use `/activation?token=...` query param |

---

## Useful one-liners

```bash
# Backend
cd hlm-backend && ./mvnw compile -q                   # check compilation
cd hlm-backend && ./mvnw test -q                      # unit tests
cd hlm-backend && ./mvnw failsafe:integration-test -q # IT tests (needs Docker)

# Frontend
cd hlm-frontend && npx ng build --configuration=ci    # production build
cd hlm-frontend && npx playwright test --list         # list all discovered tests
cd hlm-frontend && npx playwright test activation.spec.ts --headed  # single spec

# Docker
docker compose up -d --wait --wait-timeout 180        # full stack + health-wait
docker compose logs hlm-backend -f                    # backend logs

# WSL2 Docker socket
export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
```
