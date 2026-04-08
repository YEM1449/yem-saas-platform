# CI/CD Guide — Engineer Guide

This guide describes the active GitHub Actions workflows, how the pipeline is structured, and how to reproduce the important failure classes locally.

## Workflow Overview

All workflows live in `.github/workflows/`:

| File | Purpose |
| --- | --- |
| `backend-ci.yml` | Java unit and integration tests |
| `frontend-ci.yml` | Angular build, lint, and unit tests |
| `e2e.yml` | Full-stack Playwright E2E against the static CI frontend build |
| `docker-build.yml` | Docker image build plus compose smoke validation |
| `secret-scan.yml` / `snyk.yml` | Security scanning |

The E2E workflow is intentionally separate from frontend unit tests because it exercises a different runtime shape:

- Angular is built with `--configuration=ci`
- the build output is served statically on `http://localhost:4200`
- Playwright calls backend write APIs directly through `PLAYWRIGHT_API_BASE=http://localhost:8080`
- no `ng serve` proxy is present in CI

## Backend CI

**File:** `.github/workflows/backend-ci.yml`

### What it does

1. Checks out the repository.
2. Sets up Java 21.
3. Restores Maven cache.
4. Runs backend tests with Maven.

### Notes

- Integration tests rely on Docker/Testcontainers.
- `application-test.yml` already provides test-safe defaults, so no production secrets are required for routine CI execution.

## Frontend CI

**File:** `.github/workflows/frontend-ci.yml`

### What it does

1. Checks out the repository.
2. Sets up Node.js 22 with npm cache.
3. Installs frontend dependencies with `npm ci`.
4. Runs the Angular build.
5. Runs Karma/Jasmine unit tests.

### Common failure classes

- Angular or TypeScript compilation errors
- missing standalone component imports
- dependency/version drift between Angular packages and Angular CLI

## E2E CI

**File:** `.github/workflows/e2e.yml`

### What it does

1. Checks out the repository.
2. Creates a minimal `.env` for CI, including:
   - a valid `JWT_SECRET`
   - `CORS_ALLOWED_ORIGINS=http://localhost:4200,http://127.0.0.1:4200`
3. Starts infrastructure containers with Docker Compose.
4. Waits for PostgreSQL and Redis health.
5. Starts the backend container and waits for actuator health.
6. Sets up Node.js 22 and installs frontend dependencies.
7. Installs Playwright Chromium.
8. Builds Angular with `npx ng build --configuration=ci`.
9. Serves `dist/frontend/browser` on port `4200` with a Python SPA server.
10. Runs Playwright with:

```bash
CI=1 PLAYWRIGHT_API_BASE=http://localhost:8080 npx playwright test
```

11. Uploads `hlm-frontend/playwright-report/` on failure.

### Why CI uses a static frontend build

This catches classes of bugs that `ng serve` can hide:

- missing dependence on the dev proxy
- incorrect direct API base URLs
- CORS gaps for `localhost:4200`
- route refresh and SPA fallback issues
- brittle E2E selectors that only fail once the real rendered DOM stabilizes

### Local reproduction of CI E2E

Use this when a Playwright suite passes under `ng serve` but fails in GitHub Actions:

```bash
docker compose up -d postgres redis minio hlm-backend
cd hlm-frontend
npx ng build --configuration=ci
python3 /tmp/spa_server_4200.py   # or any equivalent SPA static server on :4200
CI=1 PLAYWRIGHT_API_BASE=http://localhost:8080 npx playwright test
```

The important detail is that Playwright must target the static build on `:4200`, not the Angular dev server.

## Docker Build and Smoke Test

**File:** `.github/workflows/docker-build.yml`

### What it does

1. Checks out the repository.
2. Creates a minimal `.env`.
3. Builds Docker images.
4. Starts the stack with Docker Compose.
5. Waits for backend health.
6. Verifies `GET /actuator/health` returns `{"status":"UP"}`.

## Interpreting CI Failures

### Backend CI failure

Look for the first failing test class or stack trace. Reproduce with the narrowest possible Maven invocation.

Examples:

```bash
cd hlm-backend
./mvnw test -Dtest=PasswordEncoderTest
./mvnw failsafe:integration-test -Dit.test=PortalAuthIT
```

### Frontend CI failure

Look for:

- TypeScript compile errors
- template diagnostics
- missing standalone imports
- dependency version conflicts in `package-lock.json`

### E2E CI failure

Start by deciding whether the failure is a runtime-path issue or a test-selector issue.

Common causes:

- `page.request` accidentally targets `http://localhost:4200` instead of the backend.
  - Symptom: HTML or 501/404-style responses from the static server.
  - Fix: use `PLAYWRIGHT_API_BASE=http://localhost:8080` for direct API setup calls.
- The test depends on the `ng serve` proxy.
  - Symptom: works locally, fails only in CI mode.
  - Fix: reproduce with `CI=1` and the static build.
- Missing CORS allowance for `http://localhost:4200` or `http://127.0.0.1:4200`.
  - Symptom: browser-side network errors during login or API fetches.
- Brittle locator unions under Playwright strict mode.
  - Symptom: `strict mode violation` where a selector like `'a, b, c'` matches more than one element.
  - Fix: add a dedicated `data-testid` and assert against a single-purpose locator.

Concrete example from the portal suite:

- `e2e/portal.spec.ts` previously asserted `.portal-alert-error, .state-invalid, .portal-login-page`
- in CI mode both the page shell and the error alert were present
- Playwright correctly failed because `toBeVisible()` expects one resolved element
- the fix was to add a dedicated `data-testid` for the error alert and target that element only

### Docker smoke failure

If the backend never becomes healthy:

1. Check `docker compose logs hlm-backend`.
2. Verify `JWT_SECRET` is present and long enough.
3. Verify database and Redis are healthy before backend startup.

## Extending the Pipeline

Keep these rules in mind when adding new checks:

- Put build-time correctness in `frontend-ci.yml` or `backend-ci.yml`.
- Put browser/runtime/full-stack behavior in `e2e.yml`.
- Keep E2E selectors stable with `data-testid`.
- If a test needs direct backend setup in CI, use `PLAYWRIGHT_API_BASE`, not the frontend base URL.
- Prefer reproducing failures locally in the same mode as CI before changing workflow logic.
