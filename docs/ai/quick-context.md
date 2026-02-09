# Quick Context (High Signal)

- Repo: multi-tenant CRM for real estate promotion/construction.
- Backend module: `hlm-backend/` (Spring Boot 3.5.x, Spring Security, JWT HS256, PostgreSQL, Liquibase).
- Multi-tenancy: JWT claim `tid` → `TenantContext` ThreadLocal → tenant-scoped repositories.
- Error contract: `ErrorResponse` + `ErrorCode` with global handlers for 400/401/403/404/409/500.

## Go-to file paths
- Controllers (endpoints): `hlm-backend/src/main/java/com/yem/hlm/backend/**/api/*Controller.java`
- DTOs: `hlm-backend/src/main/java/com/yem/hlm/backend/**/api/dto/*.java`
- Security config: `auth/security/SecurityConfig.java`
- JWT filter: `auth/security/JwtAuthenticationFilter.java`
- JWT provider: `auth/service/JwtProvider.java`
- JWT beans/properties: `auth/config/JwtBeansConfig.java`, `auth/config/JwtProperties.java`
- Tenant context: `tenant/context/TenantContext.java`
- Error handlers: `common/error/GlobalExceptionHandler.java`
- Error contract: `common/error/ErrorResponse.java`, `common/error/ErrorCode.java`
- Liquibase master: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Tests (IT): `src/test/java/com/yem/hlm/backend/**/**/*IT.java`
- CI: `.github/workflows/backend-ci.yml`, `.github/workflows/frontend-ci.yml`

## Auth flow + claims
- Incoming request: `Authorization: Bearer <JWT>`.
- Filter validates token, extracts:
  - `sub` = userId (UUID)
  - `tid` = tenantId (UUID)
  - `roles` = list of role strings
- Filter sets `TenantContext` + Spring Security Authentication.
- ThreadLocal cleared at end of request.

## RBAC convention
- Roles: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`.
- Use `@PreAuthorize("hasRole('ADMIN')")` or `hasAnyRole('ADMIN','MANAGER')`.
- `roles` claim contains ROLE_* values; missing roles default to `ROLE_AGENT`.

## Error contract
- JSON includes: timestamp, status, error, code, message, path, optional fieldErrors.
- 401 handled by `CustomAuthenticationEntryPoint`.
- 403 handled by `CustomAccessDeniedHandler` + global handlers.

## Top commands
- Run backend: `cd hlm-backend && ./mvnw spring-boot:run`
- Tests: `cd hlm-backend && ./mvnw test`

## Do-not-do list
- Do NOT output or commit secrets (use placeholders).
- Do NOT trust tenantId from client input; always use `TenantContext`.
- Do NOT use `ROLE_ROLE_` or hardcode incorrect role prefixes.
- Do NOT edit applied Liquibase changesets; add new ones.
