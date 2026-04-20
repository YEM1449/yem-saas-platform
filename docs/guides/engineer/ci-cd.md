# CI/CD Guide

This guide explains the current GitHub Actions pipeline and how to work with it.

## 1. Current Workflows

| Workflow | Purpose |
| --- | --- |
| `backend-ci.yml` | backend unit tests, package, integration tests |
| `frontend-ci.yml` | frontend unit tests and production build |
| `e2e.yml` | Playwright end-to-end verification |
| `docker-build.yml` | Docker image build and push, plus compose smoke on PRs |
| `secret-scan.yml` | lightweight secret pattern scan |
| `snyk.yml` | dependency and container security scanning |

## 2. What A Healthy PR Should Satisfy

- backend tests pass when backend code changes
- frontend tests and build pass when UI code changes
- E2E stays green for auth and critical workflows
- Docker build still succeeds for deployable artifacts
- security scans do not introduce unacceptable issues

## 3. Local Reproduction Strategy

Before pushing, reproduce the narrowest relevant checks locally:

- backend change: `./mvnw test`
- frontend change: `npm test -- --watch=false` and `npm run build`
- auth, session, or route change: add Playwright or manual browser validation
- infrastructure change: `docker compose up -d --wait --wait-timeout 180`

## 4. Workflow Notes

### Backend CI

- runs unit tests, package build, and integration tests
- integration stage depends on Docker for Testcontainers

### Frontend CI

- installs dependencies
- runs Angular unit tests
- builds production output

### E2E

- starts infrastructure and backend
- builds Angular in CI mode
- serves the built SPA
- runs Playwright against the live system

### Docker build

- builds backend and frontend images
- pushes on non-PR flows
- can run a compose smoke test in PR contexts

## 5. Debugging Failures

- read the exact workflow that failed; assumptions differ per job
- for E2E, check whether the failure is frontend route, backend API, or test harness
- for backend IT failures, inspect Docker/Testcontainers assumptions first
- for Docker failures, verify `.env` defaults and container health checks

## 6. Documentation Rule

If your change alters:

- auth behavior
- route families
- build or deploy shape
- migration requirements

then update the relevant docs in the same PR.
