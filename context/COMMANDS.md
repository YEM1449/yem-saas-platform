# COMMANDS.md — Canonical Command Reference

_Source of truth for all build/test/run commands. Updated: 2026-03-11._

## Backend (hlm-backend/)

```bash
# Compile only (fast check)
cd hlm-backend && ./mvnw -B -ntp -DskipTests compile

# Run unit tests (Surefire, 41 tests, no Docker needed)
cd hlm-backend && ./mvnw -B -ntp test

# Run integration tests (Failsafe + Testcontainers — requires Docker)
cd hlm-backend && ./mvnw -B -ntp failsafe:integration-test failsafe:verify

# Full verify (unit + IT — use in CI)
cd hlm-backend && ./mvnw -B -ntp verify

# Package (skip tests)
cd hlm-backend && ./mvnw -B -ntp -DskipTests package

# Run locally (local profile provides all defaults — no env vars needed)
cd hlm-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# To override defaults: set -a && source .env && set +a  (before the command above)
# If port 8080 is in use: fuser -k 8080/tcp
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

## Runtime Environment Variables

| Variable | Example | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | PostgreSQL JDBC URL |
| `DB_USER` | `hlmuser` | DB username |
| `DB_PASSWORD` | `changeme` | DB password |
| `JWT_SECRET` | (64+ char random) | HMAC-SHA256 signing key |
| `JWT_TTL_SECONDS` | `3600` | Default: 3600 |
| `EMAIL_HOST` | `smtp.example.com` | SMTP server host |
| `EMAIL_PORT` | `587` | SMTP port |
| `EMAIL_USER` | `no-reply@example.com` | SMTP username |
| `EMAIL_PASSWORD` | `changeme` | SMTP password |
| `EMAIL_FROM` | `noreply@example.com` | Sender address for outbound emails |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox batch processor size |
| `OUTBOX_MAX_RETRIES` | `3` | Outbox retry cap |
| `OUTBOX_POLL_INTERVAL_MS` | `30000` | Outbox polling interval |
| `PAYMENTS_OVERDUE_CRON` | `0 0 6 * * *` | Payment overdue scheduler cron |
| `REMINDER_CRON` | `0 0 8 * * *` | Reminder scheduler cron |
| `MEDIA_STORAGE_DIR` | `./uploads` | Local media storage root |
| `MEDIA_MAX_FILE_SIZE` | `10485760` | Max media file size (bytes) |
| `APP_PORTAL_BASE_URL` | `http://localhost:4200` | Base URL used in portal magic-link generation (`app.portal.base-url`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed CORS origins (prod override strongly recommended) |
