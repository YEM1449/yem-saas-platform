# YEM SaaS Platform

YEM SaaS Platform is a multi-societe real-estate CRM for Moroccan sales teams.
It combines four product surfaces in one repository:

- a CRM for `ADMIN`, `MANAGER`, and `AGENT`
- a platform console for `SUPER_ADMIN`
- a buyer portal authenticated by magic link
- shared services for documents, notifications, dashboards, compliance, and automation

The implementation is a monorepo built with Spring Boot 3.5, Angular 19, PostgreSQL 16, and optional Redis / S3-compatible object storage.

## Product Snapshot

### Internal CRM

- project, immeuble, tranche, and property inventory management
- contact qualification and client lifecycle management
- reservations, deposits, ventes, contracts, and payment schedules
- dashboards for commercial activity, cash collection, and receivables
- tasks, notifications, outbound messages, and audit visibility
- user invitations, role management, and template administration

### Platform Administration

- societe creation and lifecycle management
- quota and subscription settings
- branding and compliance metadata
- member inspection and impersonation

### Buyer Portal

- one-time magic-link authentication
- buyer-specific contract visibility
- payment schedule consultation
- property detail consultation
- controlled document download and upload for vente documents

## Repository Layout

| Path | Responsibility |
| --- | --- |
| [hlm-backend/](hlm-backend/) | Spring Boot API, schedulers, migrations, business rules |
| [hlm-frontend/](hlm-frontend/) | Angular CRM, superadmin UI, and buyer portal |
| [docs/](docs/) | architecture, specifications, guides, and learning material |
| [nginx/](nginx/) | reverse-proxy and TLS reference configuration |
| [.github/workflows/](.github/workflows/) | CI, Docker build, E2E, security scans |

## Runtime Architecture

```text
Browser
  CRM staff UI        -> /login, /app/*
  Superadmin UI       -> /superadmin/*
  Buyer portal        -> /portal/*
        |
        v
Nginx or Angular dev proxy
  /auth
  /api
  /api/portal
        |
        v
Spring Boot 3.5
  Security filter chain
  Controller -> Service -> Repository
  Schedulers / outbox / PDF generation
        |
        v
PostgreSQL 16
  Liquibase-managed schema
  societe-scoped data model
  Row-level security defense-in-depth

Optional runtime services:
  Redis, MinIO/S3, SMTP/Brevo, Twilio, OpenTelemetry collector
```

## Core Architectural Rules

- Multi-societe isolation is mandatory. Every business aggregate is scoped by `societe_id`.
- CRM and portal authentication are separate. Staff sessions use `hlm_auth`; buyers use `hlm_portal_auth`.
- Backend changes ship with Liquibase migrations when schema changes are involved.
- RLS is defense in depth, not a replacement for service-level scoping.
- Contract, payment, reminder, and message flows are asynchronous where reliability matters.

## Quick Start

### Full stack with Docker Compose

```bash
cp .env.example .env
docker compose up -d --wait --wait-timeout 180
curl -s http://localhost:8080/actuator/health
```

Open:

- CRM and superadmin UI: `http://localhost`
- Backend health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### Local development mode

Backend:

```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend:

```bash
cd hlm-frontend
npm ci
npm start
```

## Seed Accounts

| Account | Role | Notes |
| --- | --- | --- |
| `superadmin@yourcompany.com` / `YourSecure2026!` | `ROLE_SUPER_ADMIN` | platform administration |
| `admin@acme.com` / `Admin123!Secure` | `ROLE_ADMIN` | societe-scoped CRM |
| `manager@demo.ma` / `Manager123!Sec` | `ROLE_MANAGER` | seeded test user |
| `agent@demo.ma` / `Agent123!Secure` | `ROLE_AGENT` | seeded test user |

## Authentication Model

### CRM staff login

- `POST /auth/login`
- If the user has one active societe membership, the backend sets `hlm_auth`.
- If the user has multiple memberships, the backend returns a short-lived partial token and a societe list.
- The frontend calls `POST /auth/switch-societe` with the partial token to obtain the final scoped session.

### Buyer portal login

- `POST /api/portal/auth/request-link`
- `GET /api/portal/auth/verify?token=...`
- Verification consumes the token once and sets `hlm_portal_auth`.

## Test Commands

```bash
# Backend
cd hlm-backend && ./mvnw test
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify

# Frontend
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build

# End-to-end
cd hlm-frontend && npx playwright test
```

## Documentation Map

Start here:

- [docs/index.md](docs/index.md)
- [docs/context/ARCHITECTURE.md](docs/context/ARCHITECTURE.md)
- [docs/spec/requirements-spec.md](docs/spec/requirements-spec.md)

Engineer onboarding:

- [docs/guides/engineer/getting-started.md](docs/guides/engineer/getting-started.md)
- [docs/guides/engineer/backend-deep-dive.md](docs/guides/engineer/backend-deep-dive.md)
- [docs/guides/engineer/frontend-deep-dive.md](docs/guides/engineer/frontend-deep-dive.md)

User enablement:

- [docs/guides/user/overview.md](docs/guides/user/overview.md)
- [docs/guides/user/getting-started-user.md](docs/guides/user/getting-started-user.md)
- [docs/guides/user/sales-pipeline.md](docs/guides/user/sales-pipeline.md)

Learning path:

- [docs/course/README.md](docs/course/README.md)

## Security and Operations

- Security baseline: [SECURITY.md](SECURITY.md)
- Runtime runbook: [docs/runbook-operations.md](docs/runbook-operations.md)
- Deployment guidance: [docs/07-deployment/README.md](docs/07-deployment/README.md)
