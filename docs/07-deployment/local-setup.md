# Local Development Setup

**Audience**: engineers setting up the stack for the first time
**Updated**: 2026-03-25

---

## 1. Prerequisites

| Tool | Minimum Version | Notes |
| --- | --- | --- |
| Docker | 24+ | Required |
| Docker Compose v2 | 2.20+ | `docker compose` (not `docker-compose`) |
| Java | 21 | Only needed for backend dev mode (Mode B/C) |
| Node.js | 20+ | Only needed for frontend dev mode (Mode B/C) |
| npm | 10+ | Bundled with Node 20 |
| Git | any | |

> **WSL2 (Windows)**: If using Docker Desktop on Windows with WSL2, Testcontainers needs the correct socket path. See the [WSL2 note](#wsl2--docker-desktop) below.

---

## 2. Initial Setup (all modes)

```bash
# 1. Clone the repository
git clone <repo-url>
cd yem-saas-platform

# 2. Create your environment file
cp .env.example .env

# 3. Open .env and set JWT_SECRET at minimum:
#    JWT_SECRET=<output of: openssl rand -base64 48>
```

`JWT_SECRET` is the only required variable to start. The backend will **fail to start** with a
`@NotBlank` exception if it is absent or blank.

---

## 3. Deployment Modes

### Mode A — Full Docker Compose (recommended for first run)

All services run in containers — no local JDK or Node required.

```bash
docker compose up -d

# Verify all containers are healthy
docker compose ps

# Stream backend logs
docker compose logs -f hlm-backend
```

Access:
- Frontend: `http://localhost`
- Backend Swagger (if enabled): `http://localhost:8080/swagger-ui.html`

### Mode B — Mixed Local (recommended for active development)

Infrastructure (Postgres, Redis, MinIO) in Docker; backend and frontend on host.
Gives hot-reload and faster iteration.

```bash
# Start only infrastructure
docker compose up -d postgres redis minio

# Backend (separate terminal)
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend (separate terminal)
cd hlm-frontend
npm ci
npm start
```

Access:
- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`

> **CORS note**: The `local` Spring profile relaxes CORS but you must ensure
> `CORS_ALLOWED_ORIGINS` includes **both** `http://localhost:4200` **and**
> `http://127.0.0.1:4200` — Windows browsers often use `127.0.0.1` even when typing
> `localhost`.

### Mode C — Backend only (frontend in Docker)

```bash
# All infra + frontend in Docker, backend on host
docker compose up -d postgres redis minio hlm-frontend
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 4. Verify the Stack

```bash
# Backend health
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# Test login
curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","password":"Admin123!Secure"}' | python3 -m json.tool
```

A successful login returns:
```json
{
  "accessToken": "",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "requiresSocieteSelection": false
}
```

The empty `accessToken` is expected for final browser sessions because the actual JWT is stored in the `hlm_auth` httpOnly cookie.

---

## 5. Complete Environment Variables Reference

### Core Runtime

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `SERVER_PORT` | no | `8080` | HTTP port | `8080` |
| `DB_URL` | yes | — | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/hlm` |
| `DB_USER` | yes | — | Database username | `hlm_user` |
| `DB_PASSWORD` | yes | — | Database password | `hlm_password` |

### Security

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `JWT_SECRET` | **yes** | — | Min 32 chars; HS256 signing key | `openssl rand -base64 48` |
| `JWT_TTL_SECONDS` | no | `3600` | JWT expiry in seconds | `3600` |
| `LOCKOUT_MAX_ATTEMPTS` | no | `5` | Failed login attempts before lockout | `5` |
| `LOCKOUT_DURATION_MINUTES` | no | `15` | Account lockout duration | `15` |
| `RATE_LIMIT_LOGIN_IP_MAX` | no | `20` | Max login attempts per IP per window | `20` |
| `RATE_LIMIT_LOGIN_KEY_MAX` | no | `10` | Max login attempts per email per window | `10` |
| `RATE_LIMIT_LOGIN_WINDOW_SECONDS` | no | `900` | Rate limit sliding window (seconds) | `900` |
| `RATE_LIMIT_PORTAL_CAPACITY` | no | `3` | Portal magic-link requests per window | `3` |
| `RATE_LIMIT_PORTAL_REFILL_PERIOD` | no | `3600` | Portal rate limit window (seconds) | `3600` |

### CORS and URLs

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:4200` | Comma-separated allowed origins | `http://localhost:4200,http://127.0.0.1:4200` |
| `FRONTEND_BASE_URL` | no | `http://localhost:4200` | Used in invitation/reset email links | `https://app.yourcompany.com` |
| `PORTAL_BASE_URL` | no | `http://localhost:4200` | Used in portal magic-link emails | `https://portal.yourcompany.com` |

### Redis (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `REDIS_ENABLED` | no | `false` | Enable Redis for distributed cache | `true` |
| `REDIS_HOST` | no | `localhost` | Redis hostname | `redis` |
| `REDIS_PORT` | no | `6379` | Redis port | `6379` |
| `REDIS_PASSWORD` | no | — | Redis AUTH password | `secret` |

Without Redis: Caffeine in-process cache (no sharing between instances).
With Redis: shared cache safe for horizontal scaling.

### Email / SMTP (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `EMAIL_HOST` | no | — | SMTP hostname (blank = no-op sender) | `smtp.mailgun.org` |
| `EMAIL_PORT` | no | `587` | SMTP port | `587` |
| `EMAIL_USER` | no | — | SMTP username | `apikey` |
| `EMAIL_PASSWORD` | no | — | SMTP password | `(from provider)` |
| `EMAIL_FROM` | no | `noreply@example.com` | From address | `noreply@yourcompany.com` |

> **Important**: `SmtpEmailSender` activates only when `EMAIL_HOST` is non-blank.
> `@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")` is used — NOT
> `@ConditionalOnProperty`, which treats an empty string as "present" and would activate
> SMTP with no host configured.

### SMS / Twilio (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `TWILIO_ACCOUNT_SID` | no | — | Twilio account SID (blank = no-op sender) | `ACxxxxxxxxxxxxxxx` |
| `TWILIO_AUTH_TOKEN` | no | — | Twilio auth token | `(from Twilio console)` |
| `TWILIO_FROM` | no | — | Twilio sender number | `+12125551234` |

### Media / Object Storage (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `MEDIA_STORAGE_DIR` | no | `./media` | Local filesystem path for uploads | `/data/media` |
| `MEDIA_OBJECT_STORAGE_ENABLED` | no | `false` | Use S3-compatible storage | `true` |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | no | — | S3 endpoint URL | `http://localhost:9000` |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | no | — | S3 access key | `minioadmin` |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | no | — | S3 secret key | `minioadmin` |
| `MEDIA_OBJECT_STORAGE_BUCKET` | no | `hlm-media` | S3 bucket name | `hlm-media` |
| `MEDIA_OBJECT_STORAGE_REGION` | no | `us-east-1` | S3 region | `us-east-1` |

### Observability (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `OTEL_ENABLED` | no | `false` | Enable OTLP trace export | `true` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | no | — | OTLP collector endpoint | `http://jaeger:4317` |
| `OTEL_SAMPLE_RATE` | no | `1.0` | Trace sampling rate (0.0–1.0) | `0.1` |

### Schedulers (optional)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `REMINDER_CRON` | no | `0 0 8 * * *` | Deposit/payment reminder schedule | `0 0 8 * * *` |
| `PAYMENTS_OVERDUE_CRON` | no | `0 0 7 * * *` | Overdue payments check schedule | `0 0 7 * * *` |
| `PORTAL_CLEANUP_CRON` | no | `0 0 2 * * *` | Portal token cleanup schedule | `0 0 2 * * *` |
| `DATA_RETENTION_CRON` | no | `0 0 3 * * 0` | GDPR retention sweep schedule | `0 0 3 * * 0` |
| `OUTBOX_BATCH_SIZE` | no | `10` | Outbox dispatcher batch size | `10` |
| `OUTBOX_MAX_RETRIES` | no | `3` | Max outbox message send retries | `3` |
| `OUTBOX_POLL_INTERVAL_MS` | no | `10000` | Outbox poll interval in milliseconds | `10000` |

### Super-Admin Bootstrap (startup-only)

| Variable | Required | Default | Description | Example |
| --- | --- | --- | --- | --- |
| `APP_BOOTSTRAP_ENABLED` | no | `false` | Enable first SUPER_ADMIN creation | `true` |
| `APP_BOOTSTRAP_EMAIL` | no | — | Bootstrap SUPER_ADMIN email | `superadmin@yourcompany.com` |
| `APP_BOOTSTRAP_PASSWORD` | no | — | Bootstrap SUPER_ADMIN password | `YourSecure2026!` |

> After successful bootstrap: remove all three `APP_BOOTSTRAP_*` variables and restart.

---

## 6. Seed Accounts

| Email | Password | Role |
| --- | --- | --- |
| `admin@acme.com` | `Admin123!Secure` | `ROLE_ADMIN` (societe: acme) |
| `superadmin@yourcompany.com` | `YourSecure2026!` | `SUPER_ADMIN` |

---

## 7. Running Tests

```bash
# Backend unit tests (fast, no Docker needed)
cd hlm-backend && ./mvnw test

# Backend integration tests (requires Docker for Testcontainers)
cd hlm-backend && ./mvnw failsafe:integration-test

# Full backend verify (unit + IT)
cd hlm-backend && ./mvnw verify

# Frontend unit tests
cd hlm-frontend && npm test -- --watch=false

# Frontend build check
cd hlm-frontend && npm run build

# E2E tests (requires full stack running)
cd hlm-frontend && npx playwright test
```

---

## 8. WSL2 + Docker Desktop

Testcontainers needs the correct Docker socket when running inside WSL2:

```bash
export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
```

Add this to your `~/.bashrc` or `~/.zshrc` to persist it. CI runs on `ubuntu-latest` where
the default socket works automatically.

---

## 9. Common Setup Errors

| Error | Cause | Fix |
| --- | --- | --- |
| `Field jwtSecret ... @NotBlank` | `JWT_SECRET` not set in `.env` | Set `JWT_SECRET` to a 32+ char value |
| Backend returns 401 on all requests | Wrong token or token expired | Re-run login curl; copy fresh token |
| CORS error in browser | `CORS_ALLOWED_ORIGINS` missing `127.0.0.1` | Add `http://127.0.0.1:4200` to allowed origins |
| Testcontainers: `no valid Docker environment` | Wrong Docker socket path (WSL2) | Export `DOCKER_HOST` (see above) |
| IT test: FK violation (500 instead of 201) | `@Transactional` on IT test class | Remove `@Transactional`; use UID email suffixes |
| IT test: `uk_user_email` constraint | Hardcoded emails reused across `@BeforeEach` | Append `UUID.randomUUID().toString().substring(0,8)` to all emails |
| `npm ci` fails with `chokidar` conflict | `@angular-eslint` version mismatch | All `@angular-eslint/*` must match `@angular/cli` minor (e.g. both `^19.2.0`) |
| `login@acme.com` always used instead of real email | `String.formatted()` with wrong arg count | Count `%s` placeholders; extra args are silently dropped |
