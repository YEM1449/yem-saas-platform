# COMMANDS.md — Canonical Command Reference

_Source of truth for all build/test/run commands. Updated: 2026-03-05._

## Backend (hlm-backend/)

```bash
# Compile only (fast check)
cd hlm-backend && ./mvnw -B -ntp -DskipTests compile

# Run unit tests (Surefire, ~36 tests, no Docker needed)
cd hlm-backend && ./mvnw -B -ntp test

# Run integration tests (Failsafe + Testcontainers — requires Docker)
cd hlm-backend && ./mvnw -B -ntp failsafe:integration-test

# Full verify (unit + IT — use in CI)
cd hlm-backend && ./mvnw -B -ntp verify

# Package (skip tests)
cd hlm-backend && ./mvnw -B -ntp -DskipTests package

# Run locally (requires .env or env vars: DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET)
cd hlm-backend && ./mvnw spring-boot:run

# Run with local profile
cd hlm-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Frontend (hlm-frontend/)

```bash
# Install dependencies
cd hlm-frontend && npm ci

# Start dev server (proxies /auth /api /dashboard /actuator → :8080)
cd hlm-frontend && npm start

# Run unit tests (Karma + ChromeHeadless)
cd hlm-frontend && npm test

# Run tests CI-style (headless, coverage, no watch)
cd hlm-frontend && npm test -- --watch=false --browsers=ChromeHeadless --code-coverage --progress=false

# Production build
cd hlm-frontend && npm run build
```

## Smoke Tests (scripts/)

```bash
# Auth smoke test (requires running backend)
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

## Database / Migrations

```bash
# Liquibase runs automatically on boot via Spring Boot auto-config
# Changelog master: hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml
# New changesets: add files numbered sequentially (e.g., 028_*.yaml)
# NEVER edit applied changesets — always ADD new ones
```

## CI Workflows (GitHub Actions)

| Workflow | File | Trigger |
|----------|------|---------|
| Backend CI | backend-ci.yml | push/PR on hlm-backend/** |
| Frontend CI | frontend-ci.yml | push/PR on hlm-frontend/** |
| Snyk Security | snyk.yml | push/PR (requires SNYK_TOKEN) |
| Secret Scan | secret-scan.yml | push/PR (audit-only, no fail) |

> `dependency-review.yml` and `codeql.yml` were removed because GHAS is not enabled on this private repository.

## Required Secrets

| Secret | Required | Purpose |
|--------|----------|---------|
| `SNYK_TOKEN` | For Snyk jobs | Snyk authentication |
| `SNYK_ORG` | Optional | Snyk organization scoping |

## Required Environment Variables (Runtime)

| Variable | Example | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | PostgreSQL JDBC URL |
| `DB_USER` | `hlmuser` | DB username |
| `DB_PASSWORD` | `changeme` | DB password |
| `JWT_SECRET` | (64+ char random) | HMAC-SHA256 signing key |
| `JWT_TTL_SECONDS` | `3600` | Default: 3600 |
| `MAIL_HOST` | `smtp.example.com` | SMTP server |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | `no-reply@example.com` | SMTP user |
| `MAIL_PASSWORD` | `changeme` | SMTP password |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox batch processor size |
| `OUTBOX_MAX_RETRIES` | `3` | Outbox retry cap |
| `OUTBOX_POLL_INTERVAL_MS` | `30000` | Outbox polling interval |
