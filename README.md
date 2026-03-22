# YEM SaaS Platform

YEM is a multi-societe real-estate CRM with:

- a Spring Boot backend in [hlm-backend](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend)
- an Angular CRM and buyer portal in [hlm-frontend](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend)
- PostgreSQL, optional Redis, optional S3-compatible storage, and async email/SMS delivery

## What The System Does

The current codebase supports:

- company (`societe`) administration for `SUPER_ADMIN`
- company member management for `ADMIN`
- CRM workflows for projects, properties, contacts, reservations, deposits, contracts, collections, commissions, messaging, notifications, tasks, and audit
- a buyer-facing portal using one-time magic links

## Architecture

```text
Browser
  |- CRM SPA
  |- Super-admin SPA
  |- Buyer portal
  v
Angular / Nginx
  |- /auth
  |- /api
  |- /api/portal
  v
Spring Boot API
  |- security
  |- business services
  |- schedulers
  v
PostgreSQL

Optional:
  Redis
  MinIO / S3-compatible object storage
  SMTP
  Twilio
```

## Quickstart

```bash
cp .env.example .env
docker compose up -d
curl -s http://localhost:8080/actuator/health
```

Then open:

- frontend via `http://localhost`
- backend docs via `http://localhost:8080/swagger-ui.html` when enabled

## Seed Accounts

Current seed users from Liquibase:

| Email | Password | Role |
| --- | --- | --- |
| `admin@demo.ma` | `Admin123!Secure` | `ADMIN` |
| `manager@demo.ma` | `Manager123!Sec` | `MANAGER` |
| `agent@demo.ma` | `Agent123!Secure` | `AGENT` |
| `superadmin@yourcompany.com` | `YourSecure2026!` | `SUPER_ADMIN` local/dev seed |

## First `SUPER_ADMIN` Bootstrap

You can also bootstrap the first platform operator explicitly:

```bash
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com \
APP_BOOTSTRAP_PASSWORD='YourSecure2026!' \
cd hlm-backend && ./mvnw spring-boot:run
```

After success, remove the bootstrap variables and restart normally.

## Important Auth Note

The backend supports multi-societe users:

- one membership -> full JWT immediately
- multiple memberships -> partial token plus `requiresSocieteSelection=true`
- client must then call `POST /auth/switch-societe`

This is already implemented in the backend contract.

## Repository Map

| Path | Purpose |
| --- | --- |
| [hlm-backend](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend) | Spring Boot application |
| [hlm-frontend](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend) | Angular application |
| [docs](/home/yem/CRM-HLM/yem-saas-platform/docs) | architecture, specs, runbook, guides |
| [scripts](/home/yem/CRM-HLM/yem-saas-platform/scripts) | smoke tests and API helpers |
| [.github/workflows](/home/yem/CRM-HLM/yem-saas-platform/.github/workflows) | CI/CD workflows |

## Key Documentation

| Document | Purpose |
| --- | --- |
| [docs/index.md](docs/index.md) | documentation index |
| [docs/context/ARCHITECTURE.md](docs/context/ARCHITECTURE.md) | architecture overview |
| [docs/context/DATA_MODEL.md](docs/context/DATA_MODEL.md) | inferred data model |
| [docs/spec/functional-spec.md](docs/spec/functional-spec.md) | workflow and business behavior |
| [docs/spec/technical-spec.md](docs/spec/technical-spec.md) | technical implementation details |
| [docs/spec/api-reference.md](docs/spec/api-reference.md) | endpoint catalog |
| [docs/spec/requirements-spec.md](docs/spec/requirements-spec.md) | reconstructed requirements |
| [docs/runbook-operations.md](docs/runbook-operations.md) | operational runbook |
| [docs/guides/engineer/getting-started.md](docs/guides/engineer/getting-started.md) | engineer setup |

## Testing

```bash
cd hlm-backend && ./mvnw test
cd hlm-backend && ./mvnw verify
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build
```

Smoke helpers live in [scripts](/home/yem/CRM-HLM/yem-saas-platform/scripts).
