# Project Overview

## Mission
YEM SaaS Platform is a multi-tenant CRM for real estate promotion and construction teams. The system provides tenant-isolated data access, role-based permissions, and a REST API consumed by an Angular single-page application.

## Repository layout

```
.
├── hlm-backend/          # Spring Boot 3.x backend (Java 21)
├── frontend/             # Angular 19 SPA
├── docs/                 # Architecture, API, runbook, and operations guides
├── scripts/              # Utility scripts (smoke tests)
└── README.md             # Quickstart and entry point
```

## Primary components

### Backend (Spring Boot)
- **Auth & multi-tenancy:** JWT tokens include `tid` (tenant ID) and `roles`. Requests are authenticated by `JwtAuthenticationFilter`, which stores tenant context in a ThreadLocal for downstream services and repositories.
- **RBAC:** Roles are `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`. Controllers use `@PreAuthorize` to enforce permissions.
- **Data access:** PostgreSQL with Liquibase-managed schema; Hibernate validates mappings (`ddl-auto: validate`).
- **Error handling:** Consistent JSON `ErrorResponse` envelope with `ErrorCode` values for client-side handling.

### Frontend (Angular)
- **Auth flow:** Login returns JWT which is stored in `localStorage`. An HTTP interceptor attaches `Authorization: Bearer <token>` to requests and logs out on 401.
- **Routing:** `/login` is public; `/app/*` routes require authentication and include the properties view.
- **API integration:** Dev proxy forwards `/auth`, `/api`, and `/actuator` to the backend to avoid CORS.

## Documentation map
- [Architecture](architecture.md)
- [Backend Guide](backend.md)
- [Frontend Guide](frontend.md)
- [API Catalog](api.md)
- [API Quickstart](api-quickstart.md)
- [Database](database.md)
- [Security](security.md)
- [Runbook](runbook.md)
- [Contributing](contributing.md)
- [AI Context Pack](ai/quick-context.md)

## Typical workflows

### Local backend
1. Configure `.env` with `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `JWT_SECRET`.
2. Start PostgreSQL (local or Docker).
3. Run `./mvnw spring-boot:run` from `hlm-backend/`.

### Local frontend
1. Install dependencies with `npm ci` in `frontend/`.
2. Start with `npm start` and use the dev proxy to access the backend.

### API smoke test
Run `scripts/smoke-auth.sh` after the backend is running to verify login and token-protected endpoints.
