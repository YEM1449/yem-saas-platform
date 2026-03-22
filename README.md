# YEM SaaS Platform

Multi-société real estate CRM. Spring Boot 3.5.8 backend, Angular 19 frontend, PostgreSQL 16.

---

## Architecture

```
Browser
  |
  | HTTP :4200
  v
Angular SPA (hlm-frontend)
  |
  | Dev proxy: /auth, /api → :8080
  | (proxy.conf.json)
  v
Spring Boot API (hlm-backend :8080)
  |                    |
  | JDBC               | Lettuce
  v                    v
PostgreSQL :5432     Redis :6379
                         (distributed cache, optional)
```

In production, Nginx (`:80`) serves the Angular build and reverse-proxies `/auth` and `/api` to the backend. MinIO (`:9000`) provides S3-compatible object storage for media uploads.

---

## Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| Java | 21 | See `hlm-backend/pom.xml` |
| Maven Wrapper | bundled | Use `./mvnw` — no system Maven needed |
| Node.js | 20 | LTS recommended |
| npm | 9+ | Bundled with Node 20 |
| Docker | 24+ | Required for Docker Compose and Testcontainers integration tests |
| Docker Compose | v2 | Bundled with Docker Desktop |

---

## Quickstart

```bash
# 1. Copy env template and fill in secrets
cp .env.example .env
#    Required: POSTGRES_PASSWORD, JWT_SECRET (≥32 random chars)

# 2. Start all services
docker compose up -d

# 3. Verify backend is up
curl -s http://localhost:8080/actuator/health
#    Expected: {"status":"UP"}

# 4. Open the frontend
open http://localhost:80   # Docker Compose
# or for local dev mode:
# cd hlm-frontend && npm ci && npm start  → http://localhost:4200
```

---

## Default Credentials

These credentials are provided by Liquibase seed data for the `demo` société.

| Email | Password | Role | Notes |
|---|---|---|---|
| `admin@demo.ma` | `Admin123!Secure` | ADMIN | Full company admin |
| `manager@demo.ma` | `Admin123!` | MANAGER | Read + write, no delete |
| `agent@demo.ma` | `Admin123!` | AGENT | Read-only |

> **Change all default passwords before exposing the platform to a network.**

---

## Creating the First SUPER_ADMIN

The SUPER_ADMIN account is bootstrapped on first deploy. It is idempotent and safe to run multiple times.

```bash
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com \
APP_BOOTSTRAP_PASSWORD='YourSecure2026!' \
cd hlm-backend && ./mvnw spring-boot:run
```

Password requirements: ≥12 chars, upper, lower, digit, special character, must not contain the email local-part.

After the log line `[BOOTSTRAP] SUPER_ADMIN bootstrapped successfully`, stop the server, **remove** the three `APP_BOOTSTRAP_*` environment variables, and restart normally.

---

## Permission Matrix

| Action | SUPER_ADMIN | ADMIN | MANAGER | AGENT |
|---|---|---|---|---|
| Create / suspend / reactivate company | YES | NO | NO | NO |
| View all companies (platform-wide) | YES | NO | NO | NO |
| Impersonate a company user | YES | NO | NO | NO |
| Invite user with ADMIN role | YES | NO | NO | NO |
| Invite user with MANAGER / AGENT role | YES | YES | NO | NO |
| Change member role to ADMIN | YES | **NO** | NO | NO |
| Change member role to MANAGER / AGENT | YES | YES | NO | NO |
| Remove member from company | YES | YES | NO | NO |
| Unblock locked account | YES | YES | NO | NO |
| List / view members | YES | YES | YES | NO |
| Create / edit properties | YES | YES | YES | NO |
| Delete properties | YES | YES | NO | NO |
| View properties / contacts / contracts | YES | YES | YES | YES |
| Export personal data (RGPD Art. 15) | YES | YES | YES | NO |
| Anonymise user (RGPD Art. 17) | YES | YES | NO | NO |

> **Key rule:** A company ADMIN cannot assign the ADMIN role. Only SUPER_ADMIN can. Attempting to do so returns `403 ROLE_ESCALATION_FORBIDDEN`. See [docs/adr/002-admin-cannot-escalate-to-admin.md](docs/adr/002-admin-cannot-escalate-to-admin.md).

---

## Running Tests

```bash
# All tests (unit + integration) — requires Docker for Testcontainers
cd hlm-backend && ./mvnw verify -q

# Unit tests only
cd hlm-backend && ./mvnw test

# Integration tests only
cd hlm-backend && ./mvnw failsafe:integration-test

# Frontend unit tests
cd hlm-frontend && npm test -- --watch=false
```

Integration tests (`*IT.java`) use Testcontainers to spin up a real PostgreSQL instance. Docker must be running.

---

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | YES | `jdbc:postgresql://localhost:5432/hlm` | JDBC connection string |
| `DB_USER` | YES | `hlm_user` | Database username |
| `DB_PASSWORD` | YES | — | Database password |
| `JWT_SECRET` | YES | — | HS256 signing key (≥32 chars). App fails to start if blank. |
| `JWT_TTL_SECONDS` | NO | `3600` | Access token lifetime (seconds) |
| `EMAIL_HOST` | NO | *(blank)* | SMTP host. Leave blank to use no-op email sender. |
| `EMAIL_PORT` | NO | `587` | SMTP port |
| `EMAIL_USER` | NO | *(blank)* | SMTP username |
| `EMAIL_PASSWORD` | NO | *(blank)* | SMTP password |
| `EMAIL_FROM` | NO | `noreply@example.com` | Sender address |
| `TWILIO_ACCOUNT_SID` | NO | *(blank)* | Twilio SID. Leave blank to use no-op SMS sender. |
| `TWILIO_AUTH_TOKEN` | NO | *(blank)* | Twilio auth token |
| `TWILIO_FROM` | NO | *(blank)* | Twilio sender number |
| `REDIS_ENABLED` | NO | `false` | `true` to use Redis for distributed caching |
| `REDIS_HOST` | NO | `localhost` | Redis hostname |
| `CORS_ALLOWED_ORIGINS` | NO | `http://localhost:4200,http://127.0.0.1:4200` | Comma-separated allowed origins |
| `GDPR_RETENTION_DAYS` | NO | `1825` | Contact data retention in days (5 years) |
| `APP_BOOTSTRAP_ENABLED` | NO | `false` | Set `true` on first deploy only to create SUPER_ADMIN |
| `APP_BOOTSTRAP_EMAIL` | NO | *(blank)* | SUPER_ADMIN email (used only when bootstrap is enabled) |
| `APP_BOOTSTRAP_PASSWORD` | NO | *(blank)* | SUPER_ADMIN password (used only when bootstrap is enabled) |

Full variable reference: [docs/runbook-operations.md](docs/runbook-operations.md#2-environment-variables)

---

## Repository Map

```
hlm-backend/            Spring Boot 3.5.8 API (Java 21)
  src/main/java/
    auth/               JWT generation, login, invitation flow
    societe/            Multi-société context, SUPER_ADMIN AOP aspect
    usermanagement/     Company member CRUD, RGPD, invitation
    admin/              SUPER_ADMIN bootstrap
    common/             ErrorCode enum, ErrorResponse, SocieteRoleValidator
    property/           Real estate listings
    contact/            CRM contacts
    contract/           Sale contracts
    project/            Real estate projects
    payments/           Payment schedules and calls
    reservation/        Property reservations
    portal/             Client-facing portal (magic link)
    outbox/             Transactional outbox for email/SMS
    gdpr/               Contact data retention sweep
  src/main/resources/
    db/changelog/       Liquibase changesets (additive only)

hlm-frontend/           Angular 19 standalone components
  src/app/
    features/           Feature modules (contacts, properties, contracts, …)
    core/               Auth interceptor, portal interceptor, guards
  proxy.conf.json       Dev proxy: /auth, /api → :8080

docs/
  runbook-operations.md Full operations runbook
  adr/                  Architecture Decision Records
nginx/                  Nginx production config
docker-compose.yml      Base Compose file
docker-compose.prod.yml Production overlay
scripts/
  smoke-auth.sh         Authentication smoke test
```

---

## Key Documentation

| Document | Description |
|---|---|
| [docs/runbook-operations.md](docs/runbook-operations.md) | Operations runbook: startup, env vars, SUPER_ADMIN setup, curl examples, error table, health checks |
| [docs/adr/001-multi-societe-not-multi-tenant.md](docs/adr/001-multi-societe-not-multi-tenant.md) | Why the platform uses a membership table instead of per-tenant isolation |
| [docs/adr/002-admin-cannot-escalate-to-admin.md](docs/adr/002-admin-cannot-escalate-to-admin.md) | Why company ADMINs cannot assign the ADMIN role |
| [docs/adr/003-jwt-tokenversion-revocation.md](docs/adr/003-jwt-tokenversion-revocation.md) | How JWTs are revoked without a blocklist |
| [docs/adr/004-optimistic-locking-version-field.md](docs/adr/004-optimistic-locking-version-field.md) | Why mutable entities carry a `@Version` field |
| [docs/index.md](docs/index.md) | Full documentation index |

---

## API Reference

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

OpenAPI JSON: `http://localhost:8080/api-docs`

---

## Security Notes

- `JWT_SECRET` must be at least 32 characters. The application refuses to start if it is blank or missing (`@Validated` + `@NotBlank` on `JwtProperties`).
- CORS is locked to `CORS_ALLOWED_ORIGINS`. In production, set this to your exact frontend domain.
- HSTS is emitted only when `SSL_ENABLED=true` (TLS termination at the application level).
- Rate limiting: 20 login attempts per IP per 60 seconds; accounts lock after 5 failed attempts for 15 minutes.
- All bootstrap environment variables (`APP_BOOTSTRAP_*`) must be removed after the first deploy.
