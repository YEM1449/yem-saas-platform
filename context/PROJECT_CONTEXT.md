# PROJECT_CONTEXT.md — LLM Context Pack

_Compact reference for LLMs. Updated: 2026-03-05._

## What This Is
Multi-tenant SaaS CRM for real estate promotion companies. Tenants are isolated companies. Users within a tenant are Admin/Manager/Agent. Property buyers access a read-only portal.

## Stack (quick)
- Backend: Spring Boot 3.5.8, Java 21, Maven, PostgreSQL, Liquibase, Caffeine
- Frontend: Angular 19.2, standalone components, TypeScript 5.7
- Auth: JWT (HS256), Spring Security, OAuth2 Resource Server
- Testing: JUnit 5, Testcontainers (PostgreSQL), Failsafe (IT tests = `*IT` suffix)
- CI: 4 GitHub Actions workflows

## Repo Layout
```
hlm-backend/src/main/java/com/yem/hlm/backend/  ← 19 packages
hlm-frontend/src/app/                            ← Angular features
docs/                                            ← Documentation
context/                                         ← LLM context files (here)
.github/workflows/                               ← CI workflows
```

## Key Packages
| Package | Purpose |
|---------|---------|
| `auth/` | JWT, login, security config, filter |
| `tenant/` | Tenant entity, TenantContext (ThreadLocal) |
| `user/` | Users, UserRole enum |
| `contact/` | Contacts, prospects, ProspectDetail |
| `property/` | Properties (units), status machine |
| `project/` | Real estate projects |
| `deposit/` | Reservation deposits |
| `contract/` | Sale contracts |
| `commission/` | Commission rules (Phase 3) |
| `dashboard/` | KPI dashboards (commercial + receivables) |
| `outbox/` | Transactional outbox (email/SMS) |
| `portal/` | Client portal (magic link, ROLE_PORTAL) |
| `notification/` | In-app CRM notifications |
| `common/` | ErrorResponse, ErrorCode, GlobalExceptionHandler |

## Critical Rules (apply to ALL changes)
1. Multi-tenancy: `TenantContext.getTenantId()` in services; never trust client payload for tenant ID.
2. RBAC: `@PreAuthorize("hasRole('ADMIN')")` — Spring adds `ROLE_` prefix, never write `ROLE_ADMIN` in annotations.
3. Liquibase: additive changesets only. Never edit applied changesets.
4. DTOs: controllers expose DTOs only; never return entities.
5. Error: use `ErrorCode` + `ErrorResponse` envelope; no raw string errors.
6. Frontend: use relative API paths (`/api/...`), never `http://localhost:8080`.
7. Portal: `ROLE_PORTAL` JWT has `sub`=contactId (not userId). Skip `UserSecurityCacheService` for portal requests.

## Roles
- `ROLE_ADMIN`: full CRUD + user management + delete + KPIs
- `ROLE_MANAGER`: create/update most resources + KPIs, no delete/user mgmt
- `ROLE_AGENT`: read-only most, own data only for deposits/contracts
- `ROLE_PORTAL`: portal clients only — read own contracts via portal JWT

## Auth Flows
- CRM login: `POST /auth/login` → JWT (`sub`=userId, `tid`=tenantId, `tv`=tokenVersion, `roles`)
- Portal magic link: `POST /api/portal/auth/request-link` → email → `GET /api/portal/auth/verify?token=X` → Portal JWT (`sub`=contactId, no `tv`)

## Test Conventions
- Unit tests: `*Test`, Maven Surefire, `mvn test`
- IT tests: `*IT`, Maven Failsafe + Testcontainers, `mvn failsafe:integration-test`
- IT base: `@IntegrationTest` annotation (`@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")`)
- JWT for IT: inject `JwtProvider` (CRM) or `PortalJwtProvider` (portal) and call `.generate()`

## Seed Data
- Tenant: `acme` (ID: 11111111-...)
- Admin user: `admin@acme.com` / `Admin123!`
- Auto-applied via Liquibase on startup

## Links
- Full architecture: `docs/01_ARCHITECTURE.md`
- Commands: `context/COMMANDS.md`
- Domain rules: `context/DOMAIN_RULES.md`
- Security: `context/SECURITY_BASELINE.md`
- Conventions: `context/CONVENTIONS.md`
