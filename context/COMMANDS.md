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

## TLS / HTTPS

```bash
# Generate dev TLS certificate
bash scripts/generate-dev-cert.sh

# Start backend with TLS (dev)
SSL_ENABLED=true SERVER_PORT=8443 cd hlm-backend && ./mvnw spring-boot:run

# Verify HTTPS is working
curl -k https://localhost:8443/actuator/health

# Run TLS integration test
cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=TlsConfigIT
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

# Run ESLint
cd hlm-frontend && npm run lint
```

## Docker / Container Stack

```bash
# Start full stack locally (uses docker-compose.override.yml automatically)
docker compose up -d

# Verify full stack health
./scripts/smoke-stack.sh

# Auth smoke test (requires running backend)
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!Secure' ./scripts/smoke-auth.sh

# Production stack (pulls images from registry)
IMAGE_TAG=v1.2.3 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Object Storage (S3-compatible)

```bash
# Configure for MinIO (local docker-compose stack):
export MEDIA_OBJECT_STORAGE_ENABLED=true
export MEDIA_OBJECT_STORAGE_ENDPOINT=http://localhost:9000
export MEDIA_OBJECT_STORAGE_REGION=us-east-1
export MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
export MEDIA_OBJECT_STORAGE_ACCESS_KEY=minioadmin
export MEDIA_OBJECT_STORAGE_SECRET_KEY=minioadmin

# Configure for OVH Object Storage (Gravelines):
export MEDIA_OBJECT_STORAGE_ENABLED=true
export MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.gra.perf.cloud.ovh.net
export MEDIA_OBJECT_STORAGE_REGION=gra
export MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
export MEDIA_OBJECT_STORAGE_ACCESS_KEY=<ovh-s3-access-key>
export MEDIA_OBJECT_STORAGE_SECRET_KEY=<ovh-s3-secret-key>
# Then start the backend (no MinIO container needed):
cd hlm-backend && ./mvnw spring-boot:run

# Migrate existing local uploads to object storage (works with OVH, MinIO, AWS, etc.):
aws s3 sync ./uploads s3://hlm-media \
  --endpoint-url https://s3.gra.perf.cloud.ovh.net \
  --region gra
```

## Smoke Tests (scripts/)

```bash
# Auth smoke test (requires running backend)
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!Secure' ./scripts/smoke-auth.sh
```

## Database / Migrations

```bash
# Liquibase runs automatically on boot via Spring Boot auto-config
# Changelog master: hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml
# New changesets: add files numbered sequentially (e.g., 028_*.yaml)
# NEVER edit applied changesets — always ADD new ones
```

## Ops (Nginx / TLS)

```bash
# Check Nginx config syntax
nginx -t

# Reload Nginx (cert renewal / config change)
nginx -s reload

# Get Let's Encrypt cert
certbot certonly --standalone -d <DOMAIN> --agree-tos -m admin@<DOMAIN>

# Test TLS from command line
openssl s_client -connect <DOMAIN>:443 -tls1_2 | head -20
```

## CI Workflows (GitHub Actions)

| Workflow | File | Trigger |
|----------|------|---------|
| Backend CI | backend-ci.yml | push/PR on hlm-backend/** |
| Frontend CI | frontend-ci.yml | push/PR on hlm-frontend/** |
| Docker Build | docker-build.yml | push/PR on backend/frontend/compose |
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
| `MEDIA_STORAGE_DIR` | `./uploads` | Local media storage root (used when object storage is disabled) |
| `MEDIA_MAX_FILE_SIZE` | `10485760` | Max media file size (bytes) |
| `MEDIA_OBJECT_STORAGE_ENABLED` | `false` | `true` activates ObjectStorageMediaStorage (S3-compatible) |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | (blank) | Provider URL. Blank = AWS S3 auto-resolve. OVH ex: `https://s3.gra.perf.cloud.ovh.net` |
| `MEDIA_OBJECT_STORAGE_REGION` | `eu-west-1` | Region code (e.g. `gra`, `fr-par`, `eu-west-1`) |
| `MEDIA_OBJECT_STORAGE_BUCKET` | `hlm-media` | Bucket / container name |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | (none) | Provider S3 access key |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | (none) | Provider S3 secret key |
| `APP_PORTAL_BASE_URL` | `http://localhost:4200` | Base URL used in portal magic-link generation (`app.portal.base-url`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed CORS origins (prod override strongly recommended) |
| `SSL_ENABLED` | `false` | Enable embedded Tomcat TLS (Mode A — dev/staging) |
| `SERVER_PORT` | `8080` | HTTP or HTTPS listener port |
| `SSL_KEYSTORE_PATH` | `classpath:ssl/hlm-dev.p12` | PKCS12 keystore path (Mode A only) |
| `SSL_KEYSTORE_PASSWORD` | `changeit` | Keystore password (Mode A only) |
| `SSL_KEY_ALIAS` | `hlm-dev` | Key alias inside keystore (Mode A only) |
| `HTTP_REDIRECT_PORT` | `8080` | HTTP port for HTTP→HTTPS redirect connector (Mode A only) |
| `FORWARD_HEADERS_STRATEGY` | `NONE` | Set to `FRAMEWORK` behind Nginx or any reverse proxy |
| `TWILIO_ACCOUNT_SID` | (none) | Twilio SID — blank activates NoopSmsSender |
| `TWILIO_AUTH_TOKEN` | (none) | Twilio auth token |
| `TWILIO_FROM` | (none) | Twilio sender phone number |
| `PORTAL_CLEANUP_CRON` | `0 0 3 * * *` | Cron expression for PortalTokenCleanupScheduler |
