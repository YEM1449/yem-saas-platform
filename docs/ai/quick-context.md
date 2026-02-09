# Quick Context (High Signal)

- Repo: multi-tenant CRM platform (Spring Boot backend + Angular SPA).
- Backend: `hlm-backend/` (Spring Boot 3.5.x, Java 21, PostgreSQL, Liquibase, JWT HS256).
- Frontend: `hlm-frontend/` (Angular 19 SPA; dev proxy for `/auth`, `/api`, `/dashboard`, `/actuator`).
- Multi-tenancy: JWT claim `tid` → `TenantContext` ThreadLocal → tenant-scoped queries.
- RBAC: roles `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT` enforced via `@PreAuthorize`.
- Error contract: `ErrorResponse` + `ErrorCode` with global handlers for 400/401/403/404/409/500.

## Go-to paths
- Backend controllers: `hlm-backend/src/main/java/com/yem/hlm/backend/**/api/*Controller.java`
- Backend security: `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security`
- JWT provider/properties: `hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/JwtProvider.java`, `auth/config/JwtProperties.java`
- Tenant context: `hlm-backend/src/main/java/com/yem/hlm/backend/tenant/context/TenantContext.java`
- Error handlers: `hlm-backend/src/main/java/com/yem/hlm/backend/common/error/`
- Liquibase master: `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Frontend auth: `hlm-frontend/src/app/core/auth/`
- Frontend routes: `hlm-frontend/src/app/app.routes.ts`
- Dev proxy: `hlm-frontend/proxy.conf.json`

## Auth flow + claims
- `Authorization: Bearer <JWT>` on each request.
- Claims: `sub` = userId, `tid` = tenantId, `roles` = list of role strings.
- Filter sets `TenantContext` + Spring Security Authentication.
- ThreadLocal cleared at end of request.

## Top commands
- Run backend: `cd hlm-backend && ./mvnw spring-boot:run`
- Run frontend: `cd hlm-frontend && npm start`
- Backend tests: `cd hlm-backend && ./mvnw test`

## Do-not-do list
- Do NOT output or commit secrets (use placeholders).
- Do NOT trust tenantId from client input; always use `TenantContext`.
- Do NOT edit applied Liquibase changesets; add new ones.
