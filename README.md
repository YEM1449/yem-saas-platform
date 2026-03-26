# HLM CRM — YEM SaaS Platform

![CI Backend](https://github.com/yem/yem-saas-platform/actions/workflows/backend-ci.yml/badge.svg)
![CI Frontend](https://github.com/yem/yem-saas-platform/actions/workflows/frontend-ci.yml/badge.svg)
![Security Scan](https://github.com/yem/yem-saas-platform/actions/workflows/snyk.yml/badge.svg)
![License](https://img.shields.io/badge/license-Proprietary-red)

Multi-société real-estate CRM SaaS targeting Morocco.
Built with Spring Boot 3 + Angular 19 + PostgreSQL.

---

## What it does

- **SUPER_ADMIN** — platform management (société CRUD, user quotas, impersonation)
- **ADMIN / MANAGER / AGENT** — full CRM: projects, properties, contacts, prospects, reservations, contracts, payments, commissions, tasks, documents, messaging, notifications
- **Buyer portal** — magic-link authentication, contract status and payment schedule (read-only)
- **Multi-language** — French (default), English, Arabic (RTL) — switchable per user without page reload

---

## Architecture

```
Browser (Angular 19)
  CRM SPA /app/*          →  ADMIN / MANAGER / AGENT
  Super-admin SPA /superadmin/*  →  SUPER_ADMIN
  Buyer portal /portal/*  →  ROLE_PORTAL (magic-link)
       │ HTTPS, TLS 1.2/1.3, HSTS preload
       ▼
Nginx  (TLS termination, CSP, security headers)
       │ proxy_pass → localhost:8080
       ▼
Spring Boot 3 API
  JWT auth  │  Rate limiting  │  RLS context aspect
  23 modules: audit, auth, commission, contact, contract,
              dashboard, deposit, document, gdpr, media,
              notification, outbox, payments, portal,
              project, property, reminder, reservation,
              societe, task, user, usermanagement
       │
       ▼
PostgreSQL 16  (Row-Level Security on all domain tables)
   Optional:  Redis (cache)  │  MinIO / S3  │  SMTP  │  Twilio
```

---

## Quickstart

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Start full stack (postgres, redis, minio, backend, frontend)
docker compose up -d --wait --wait-timeout 180

# 3. Verify backend health
curl -s http://localhost:8080/actuator/health | jq .

# 4. Open the app
open http://localhost
```

> **WSL2 + Docker Desktop:** export `DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock` before running Testcontainers-based integration tests.

---

## Seed Accounts

| Email | Password | Role |
|-------|----------|------|
| `admin@acme.com` | `Admin123!Secure` | `ROLE_ADMIN` (société acme) |
| `superadmin@yourcompany.com` | `YourSecure2026!` | `ROLE_SUPER_ADMIN` |

> Other demo users seeded by changeset 045: `manager@demo.ma`, `agent@demo.ma`.

---

## Local Development

```bash
# Backend only
cd hlm-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend only (proxies /api → :8080)
cd hlm-frontend && npm start
```

---

## Testing

```bash
# Backend unit tests
cd hlm-backend && ./mvnw test

# Backend integration tests (requires Docker)
cd hlm-backend && ./mvnw failsafe:integration-test

# Frontend unit tests
cd hlm-frontend && npm test -- --watch=false

# Production build check
cd hlm-frontend && npm run build -- --configuration=production

# E2E tests (requires full stack via docker compose)
cd hlm-frontend && npx playwright test
```

---

## Multi-Language Support

The platform supports **French** (default), **English**, and **Arabic** (RTL).

Users switch language via the FR / EN / ع buttons in the sidebar footer. The choice is:
1. Stored in `localStorage` for instant re-apply on next load.
2. Persisted to the backend via `PUT /auth/me/langue`.
3. Applied on session restore — no re-login needed.

Translation files live in `hlm-frontend/public/assets/i18n/`.

---

## First SUPER_ADMIN Bootstrap

Create the first platform operator on a fresh deployment:

```bash
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com \
APP_BOOTSTRAP_PASSWORD='YourSecure2026!' \
cd hlm-backend && ./mvnw spring-boot:run
```

Remove the bootstrap variables and restart after the first run.

---

## Security

See [SECURITY.md](SECURITY.md) for the full threat model, security controls map, and vulnerability disclosure policy.

Key controls:
- JWT HS256 with token-version revocation
- Dual-bucket rate limiting (IP + identity)
- Email-enumeration timing-attack mitigation
- PostgreSQL Row-Level Security on all domain tables
- CSP, HSTS preload, X-Frame-Options DENY, Permissions-Policy

---

## Repository Map

| Path | Purpose |
|------|---------|
| [hlm-backend/](hlm-backend/) | Spring Boot application (Java 21, Maven) |
| [hlm-frontend/](hlm-frontend/) | Angular 19 application |
| [docs/](docs/) | Architecture, specs, runbook, guides |
| [scripts/](scripts/) | Smoke tests and API helpers |
| [.github/workflows/](.github/workflows/) | CI/CD workflows |
| [SECURITY.md](SECURITY.md) | Threat model and disclosure policy |
| [CHANGELOG.md](CHANGELOG.md) | Release history |

---

## Key Documentation

| Document | Purpose |
|----------|---------|
| [docs/context/ARCHITECTURE.md](docs/context/ARCHITECTURE.md) | Architecture overview |
| [docs/context/DATA_MODEL.md](docs/context/DATA_MODEL.md) | Data model |
| [docs/spec/functional-spec.md](docs/spec/functional-spec.md) | Business workflows |
| [docs/spec/technical-spec.md](docs/spec/technical-spec.md) | Technical implementation |
| [docs/spec/api-reference.md](docs/spec/api-reference.md) | Endpoint catalogue |
| [docs/runbook-operations.md](docs/runbook-operations.md) | Operational runbook |
| [docs/guides/engineer/getting-started.md](docs/guides/engineer/getting-started.md) | Engineer setup |
