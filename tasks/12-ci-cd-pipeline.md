# Task 12 — GitHub Actions CI/CD Pipeline

## Priority: HIGH
## Effort: 2 hours

## Problem

No automated pipeline catches regressions on PRs or builds Docker images on merge. The project has a Docker Compose stack but relies on manual testing.

## Files to Create

### `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: hlm_test
          POSTGRES_USER: hlm_user
          POSTGRES_PASSWORD: hlm_pwd
        ports: ["5432:5432"]
        options: >-
          --health-cmd="pg_isready -U hlm_user -d hlm_test"
          --health-interval=10s --health-timeout=5s --health-retries=5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: maven }
      - name: Run tests
        working-directory: hlm-backend
        env:
          DB_URL: jdbc:postgresql://localhost:5432/hlm_test
          DB_USER: hlm_user
          DB_PASSWORD: hlm_pwd
          JWT_SECRET: ci-test-secret-that-is-at-least-32-characters-long
          REDIS_ENABLED: "false"
        run: ./mvnw verify -B

  frontend-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 22, cache: npm, cache-dependency-path: hlm-frontend/package-lock.json }
      - working-directory: hlm-frontend
        run: npm ci && npm run build -- --configuration=production

  docker-build:
    needs: [backend-test, frontend-build]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: |
          docker compose build hlm-backend hlm-frontend
```

## Acceptance Criteria

- [ ] PRs trigger backend tests + frontend build
- [ ] Merge to main additionally builds Docker images
- [ ] Pipeline passes on current codebase
