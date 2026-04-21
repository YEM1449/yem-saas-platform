# Frontend Developer Guide

Angular 19 · TypeScript 5 · Standalone Components · Playwright E2E

---

## 1. Architecture Overview

### Framework and Tooling

| Item | Version |
|---|---|
| Angular | 19 |
| TypeScript | 5.x |
| Component model | Standalone (no NgModules) |
| Test runner | Karma + Jasmine |
| E2E | Playwright |

### Route Trees

The application has three separate route trees, each with its own auth guard and interceptor:

| Prefix | Surface | Role Required |
|---|---|---|
| `/app/*` | CRM shell (staff) | `ROLE_ADMIN`, `ROLE_MANAGER`, or `ROLE_AGENT` |
| `/superadmin/*` | Platform administration | `ROLE_SUPER_ADMIN` |
| `/portal/*` | Buyer portal (magic-link) | `ROLE_PORTAL` |

### Guards

| Guard | Protects | Mechanism |
|---|---|---|
| `AuthGuard` | `/app/*` | Calls the backend session-validation flow through `AuthService.verifySession()` and redirects to `/login` when the `hlm_auth` cookie-backed session is invalid |
| `AdminGuard` | Admin-only sections within CRM | Validates the current user profile and requires `ROLE_ADMIN` |
| `SuperAdminGuard` | `/superadmin/*` | Validates the current user profile and requires `ROLE_SUPER_ADMIN` |
| `PortalGuard` | `/portal/*` (except `/portal/login`) | Calls `PortalAuthService.validateSession()` and redirects to `/portal/login` when the httpOnly portal session cookie is missing or invalid |

Functional guards use the pattern:

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  return TestBed.runInInjectionContext(() => inject(AuthService).isAuthenticated());
};
```

### HTTP Interceptors

Two interceptors are registered in `app.config.ts`. They must not interfere with each other.

| Interceptor | Token Source | Applied to |
|---|---|---|
| CRM Interceptor | cookie-backed session plus explicit partial-token header only during societe switching | `/auth/**` and `/api/**` (non-portal) |
| Portal Interceptor | `withCredentials: true` | `/api/portal/**` only |

### Token Storage

| Key | Purpose |
|---|---|
| `hlm_auth` | CRM and SUPER_ADMIN JWT stored as an httpOnly cookie |
| `hlm_portal_auth` | Buyer portal JWT stored as an httpOnly cookie scoped to `/api/portal` |
| `PortalSessionStore` | In-memory frontend flag indicating a validated portal session in the current SPA runtime |

---

## 2. Development Setup

### Prerequisites

- Node.js 20+
- Angular CLI 19 (installed via `npx` or globally)

### Install Dependencies

```bash
cd hlm-frontend && npm ci
```

### Start Dev Server

```bash
cd hlm-frontend && npm start
# Angular dev server: http://localhost:4200
```

### Dev Proxy Configuration

`hlm-frontend/proxy.conf.json` proxies the following prefixes from port 4200 to the backend at port 8080:
- `/auth`
- `/api`
- `/dashboard`
- `/actuator`

### CORS Configuration Note

`application-local.yml` (backend) must list **both** allowed origins:

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:4200
      - http://127.0.0.1:4200
```

Windows browsers frequently resolve `localhost` to `127.0.0.1`. Omitting one of these origins causes CORS failures that appear as network errors in the browser console.

### Production Build

```bash
cd hlm-frontend && npm run build
# Output: hlm-frontend/dist/
```

---

## 3. Testing

### Unit Tests (Karma + Jasmine)

```bash
cd hlm-frontend && npm test -- --watch=false
```

Uses `ChromeHeadlessCI` launcher in CI (see Section 4 for configuration).

### E2E Tests (Playwright)

```bash
cd hlm-frontend && npx playwright test
```

Requirements:
- Backend must be running (Docker Compose or local `./mvnw spring-boot:run`)
- Local default: `playwright.config.ts` starts `ng serve` on port 4200 via `webServer` and uses the Angular dev proxy
- CI mode: Angular is built with `--configuration=ci`, served statically on port 4200, and Playwright setup calls target the backend with `PLAYWRIGHT_API_BASE=http://localhost:8080`

### Playwright Worker Configuration

`playwright.config.ts` must have `workers: 1` for serial execution. Multiple workers cause parallel login attempts that trigger the login rate-limit lockout, causing flaky test failures.

```typescript
// playwright.config.ts
export default defineConfig({
  workers: 1,
  // ...
});
```

### E2E Tests Location

`hlm-frontend/e2e/`

---

## 4. Jasmine 5.x Pitfalls

### configurable: false on createSpyObj properties

`jasmine.createSpyObj(name, methods, { prop: val })` creates a property descriptor with `configurable: false`. Calling `Object.defineProperty` to override it in a test throws:

```
TypeError: Cannot redefine property: prop
```

**Fix**: Pass only the method list to `createSpyObj`. Use a closure variable for any property you need to control.

```typescript
// Wrong
const spy = jasmine.createSpyObj('Router', ['navigate'], { url: '/initial' });
Object.defineProperty(spy, 'url', { get: () => '/new' }); // throws TypeError

// Correct
let currentUrl = '/initial';
const spy = jasmine.createSpyObj('Router', ['navigate']);
Object.defineProperty(spy, 'url', { get: () => currentUrl, configurable: true });
// later: currentUrl = '/new';
```

### Functional Guards in TestBed

Functional guards cannot be instantiated directly. Use `TestBed.runInInjectionContext`:

```typescript
it('should redirect when not authenticated', () => {
  const result = TestBed.runInInjectionContext(() =>
    authGuard(activatedRouteSnapshot, routerStateSnapshot)
  );
  expect(result).toBeFalse();
});
```

### ChromeHeadlessCI Launcher

`karma.conf.js` must define the `ChromeHeadlessCI` custom launcher with sandbox flags for CI environments:

```javascript
customLaunchers: {
  ChromeHeadlessCI: {
    base: 'ChromeHeadless',
    flags: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage'
    ]
  }
},
browsers: ['ChromeHeadlessCI']
```

---

## 5. E2E Test Pitfalls

### data-testid is mandatory

Every form input and action button that E2E tests interact with must have a `data-testid` attribute. Text-based or role-based selectors are fragile:
- Labels change when the UI is translated.
- CSS selectors like `button:has-text("Nouveau")` break on any copy change.

### NEVER use button[type="submit"] as a fallback

HTML buttons without an explicit `type` attribute default to `type="submit"`. A selector like `button[type="submit"]` matches **all** buttons in a form that lack an explicit type. Playwright returns the first match in DOM order — which is often "Annuler" (Cancel) before "Créer" (Create).

```typescript
// Wrong — may click the wrong button
await page.click('button:has-text("Créer"), button[type="submit"]');

// Correct — always target by data-testid
await page.click('[data-testid="save-button"]');
```

### Playwright comma-selector is a CSS union, and strict mode still applies

The Playwright selector `'selector-a, selector-b'` matches **all** elements that satisfy either branch. Any single-element action or assertion such as `click()`, `fill()`, or `toBeVisible()` must still resolve to exactly one element.

That means a union like:

```typescript
page.locator('.portal-alert-error, .portal-login-page')
```

will fail with a strict-mode violation if both elements are present. Prefer a dedicated `data-testid`:

```typescript
await expect(page.getByTestId('portal-error-message')).toBeVisible();
```

### data-testid Map

| data-testid | Location | Element |
|---|---|---|
| `email` | Login form | Email input |
| `password` | Login form | Password input |
| `login-button` | Login form | Submit button |
| `error-message` | Login form | Error display |
| `logout-button` | CRM shell | Logout button |
| `create-contact` | Contacts page | New contact button |
| `firstName` | Contact form | First name input |
| `lastName` | Contact form | Last name input |
| `save-button` | Contact form | Save button |
| `task-title` | Task form | Title input |
| `task-submit` | Task form | Submit button |

---

## 6. Key Components Map

| Route | Component | Feature | Auth Required |
|---|---|---|---|
| `/app` | CRM shell | Main CRM layout and navigation | ADMIN / MANAGER / AGENT |
| `/app/contacts` | `ContactsComponent` | Contact list, create, detail | ADMIN / MANAGER / AGENT |
| `/app/properties` | `PropertiesComponent` | Property list, detail, status transitions | ADMIN / MANAGER / AGENT |
| `/app/projects` | `ProjectsComponent` | Project list and detail | ADMIN / MANAGER / AGENT |
| `/app/tasks` | `TasksComponent` | Task list and create; default filtered by assigneeId | ADMIN / MANAGER / AGENT |
| `/app/reservations` | `ReservationsComponent` | Property hold management | ADMIN / MANAGER / AGENT |
| `/app/contracts/:id/payments` | `PaymentScheduleComponent` | v2 payment schedule per contract | ADMIN / MANAGER |
| `/app/mon-espace` | User management | Company member list and invite | ADMIN (write), MANAGER (read) |
| `/app/me` | `UserProfileComponent` | Self-service profile edit | ADMIN / MANAGER / AGENT |
| `/superadmin` | `SuperAdminComponent` | Societe list and manage | SUPER_ADMIN |
| `/portal/login` | `PortalLoginComponent` | Magic-link request form | Public |
| `/portal` | `PortalComponent` | Buyer dashboard (contracts, payments) | ROLE_PORTAL |

---

## 7. Angular Version Alignment

All `@angular-eslint/*` packages must match the `@angular/cli` minor version. Mismatched versions pull conflicting `chokidar` transitive dependencies, which causes `npm ci` to fail in Docker builds.

Example correct alignment:

```json
{
  "@angular/cli": "^19.2.0",
  "@angular-eslint/builder": "^19.2.0",
  "@angular-eslint/eslint-plugin": "^19.2.0",
  "@angular-eslint/eslint-plugin-template": "^19.2.0",
  "@angular-eslint/schematics": "^19.2.0",
  "@angular-eslint/template-parser": "^19.2.0"
}
```

If the `@angular/cli` minor version is upgraded (e.g., 19.2 → 19.3), all `@angular-eslint/*` packages must be updated to the same minor in the same commit.

---

## 8. API Path Reference for Frontend Services

| Resource | Method | Backend Path | Notes |
|---|---|---|---|
| Login | POST | `/auth/login` | Returns a cookie-backed final session or `requiresSocieteSelection: true` with a partial token |
| Switch societe | POST | `/auth/switch-societe` | Uses partial token; final session is written to `hlm_auth` |
| Users (admin CRUD) | GET/POST/PUT/DELETE | `/api/users` | NOT `/api/admin/users` — that prefix is SUPER_ADMIN-only |
| Company members | GET/POST/PATCH/DELETE | `/api/mon-espace/utilisateurs` | Membership lifecycle and invitations |
| Self profile | GET/PATCH | `/api/me` | Editable: prenom, nomFamille, telephone, poste, langueInterface |
| Tasks | GET/POST | `/api/tasks` | Default GET filtered by current user's assigneeId |
| Documents | GET/POST | `/api/documents` | Multipart form upload |
| Societes (super) | GET/POST/PUT | `/api/admin/societes` | SUPER_ADMIN only |
| Portal auth | POST | `/api/portal/auth/request-link` | Public; sends magic-link email |
| Portal verify | GET | `/api/portal/auth/verify?token=...` | Validates token, sets `hlm_portal_auth` cookie, returns empty `accessToken` payload |
