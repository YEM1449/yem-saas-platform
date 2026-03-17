# CI/CD Guide — Engineer Guide

This guide describes the GitHub Actions workflows, what each workflow does, how to interpret failures, and how to extend the pipeline.

## Table of Contents

1. [Workflow Overview](#workflow-overview)
2. [Backend CI](#backend-ci)
3. [Frontend CI](#frontend-ci)
4. [Docker Build and Smoke Test](#docker-build-and-smoke-test)
5. [Workflow Triggers](#workflow-triggers)
6. [Interpreting CI Failures](#interpreting-ci-failures)
7. [Extending the Pipeline](#extending-the-pipeline)

---

## Workflow Overview

All workflows live in `.github/workflows/`:

| File | Purpose |
|------|---------|
| `backend-ci.yml` | Java unit tests + integration tests (Testcontainers) |
| `frontend-ci.yml` | Angular build + lint + unit tests |
| `docker-build.yml` | Docker Compose build + health smoke test |

---

## Backend CI

**File:** `.github/workflows/backend-ci.yml`

**Triggers:** Push or pull request to `main` and any `Epic/*` branch.

### Steps

1. **Checkout** — `actions/checkout@v4`
2. **Set up Java 21** — `actions/setup-java@v4` with Temurin distribution
3. **Cache Maven dependencies** — `~/.m2/repository` keyed on `pom.xml` hash
4. **Run unit tests** — `./mvnw test`
5. **Run integration tests** — `./mvnw failsafe:integration-test`

Integration tests use Testcontainers which requires Docker. GitHub Actions runners have Docker available by default on `ubuntu-latest`.

### Environment Variables

The `application-test.yml` profile provides a hardcoded test JWT secret, so no secrets need to be passed to the CI environment for unit or integration tests.

### Test Reports

Surefire and Failsafe generate XML reports in `hlm-backend/target/surefire-reports/` and `hlm-backend/target/failsafe-reports/`. These are automatically uploaded as GitHub Actions artifacts if you add an `actions/upload-artifact` step.

---

## Frontend CI

**File:** `.github/workflows/frontend-ci.yml`

**Triggers:** Push or pull request to `main` and any `Epic/*` branch.

### Steps

1. **Checkout** — `actions/checkout@v4`
2. **Set up Node.js 20** — `actions/setup-node@v4` with npm cache
3. **Install dependencies** — `npm ci` (deterministic install from `package-lock.json`)
4. **Build** — `npm run build` (production build with AOT)
5. **Unit tests** — `npm test -- --watch=false --browsers=ChromeHeadless`

### Angular CLI Version Alignment

The `@angular/cli` devDependency version must match the Angular framework version (both 19.x). A mismatch causes build failures like:

```
Error: The Angular CLI requires a minimum Node.js version of v18.19...
```

Verify alignment:
```bash
cat hlm-frontend/package.json | grep '"@angular'
```

Both `@angular/core` and `@angular/cli` should have the same major version.

---

## Docker Build and Smoke Test

**File:** `.github/workflows/docker-build.yml`

**Triggers:** Push to `main`.

### Steps

1. **Checkout** — `actions/checkout@v4`
2. **Create `.env`** — Writes a minimal `.env` with `JWT_SECRET` from GitHub Secrets
3. **Build all images** — `docker compose build`
4. **Start stack** — `docker compose up -d`
5. **Wait for backend** — Poll `GET /actuator/health` with retries (10 s intervals, 60 s timeout)
6. **Smoke test** — Assert `{"status":"UP"}` response
7. **Tear down** — `docker compose down -v`

### Required GitHub Secret

| Secret | Value |
|--------|-------|
| `JWT_SECRET` | A 32+ character string for the CI environment |

Set this in: Repository → Settings → Secrets and variables → Actions → New repository secret.

---

## Workflow Triggers

| Workflow | Push to main | PR to main | Push to Epic/* | Manual |
|----------|-------------|-----------|---------------|--------|
| `backend-ci.yml` | Yes | Yes | Yes | No |
| `frontend-ci.yml` | Yes | Yes | Yes | No |
| `docker-build.yml` | Yes | No | No | No |

To add a manual trigger, add `workflow_dispatch:` to the `on:` section of any workflow.

---

## Interpreting CI Failures

### Backend unit test failure

```
[ERROR] Tests run: 41, Failures: 1, Errors: 0, Skipped: 0
```

1. Click the failing step in GitHub Actions UI.
2. Look for the `FAILED` line and the exception stack trace.
3. Run locally to reproduce: `./mvnw test -Dtest=FailingTest`.

### Backend integration test failure

Common causes:
- **Constructor arity mismatch** — If you added a field to a constructor, update all callers including test data builders.
- **Liquibase checksum mismatch** — Never edit an applied changeset.
- **Foreign key violation in test data** — Ensure parent entities are created before children.
- **Testcontainers Docker unavailable** — Run `docker info` to verify Docker is running.

### Frontend build failure

Common causes:
- **`@angular/cli` version mismatch** — Both `@angular/core` and `@angular/cli` must be the same major version (19.x).
- **TypeScript type error** — Check the error line number and fix the type annotation.
- **Missing import** — Standalone components must import every Angular directive they use.

### Docker smoke test failure

```
curl: (7) Failed to connect to localhost port 8080
```

1. Check backend logs: `docker compose logs hlm-backend`.
2. Common causes:
   - `JWT_SECRET` not set or too short.
   - Database not yet healthy when backend starts.
   - Port 8080 already in use on the CI runner.

---

## Extending the Pipeline

### Add a linting step (backend)

Add after the unit test step in `backend-ci.yml`:

```yaml
- name: Run Checkstyle
  run: ./mvnw checkstyle:check
```

### Add test coverage reporting

```yaml
- name: Run tests with coverage
  run: ./mvnw verify -Pcoverage

- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v4
  with:
    files: target/site/jacoco/jacoco.xml
```

### Add Docker Hub push (on release)

```yaml
- name: Log in to Docker Hub
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}

- name: Build and push backend image
  uses: docker/build-push-action@v5
  with:
    context: ./hlm-backend
    push: true
    tags: your-org/hlm-backend:latest
```

### Add a staging deployment step

After the smoke test succeeds, trigger a deployment to staging:

```yaml
- name: Deploy to staging
  if: github.ref == 'refs/heads/main'
  run: |
    # SSH to staging server and pull new image
    ssh staging "cd /opt/hlm && docker compose pull && docker compose up -d"
```
