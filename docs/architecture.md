# Architecture

## Module / package map
- **Backend module:** `hlm-backend/`
- **Main packages:**
  - `com.yem.hlm.backend.auth` (auth endpoints, JWT, security config)
  - `com.yem.hlm.backend.tenant` (tenant APIs, context, repos)
  - `com.yem.hlm.backend.property` (properties + dashboard)
  - `com.yem.hlm.backend.contact` (contacts + interests)
  - `com.yem.hlm.backend.deposit` (deposits + reporting)
  - `com.yem.hlm.backend.notification` (in-app notifications)
  - `com.yem.hlm.backend.common` (error contract, shared helpers)
  - `com.yem.hlm.backend.user` (users + roles)

## Auth + tenant request flow
```
Client
  -> Authorization: Bearer <JWT>
     -> JwtAuthenticationFilter
        - validates token
        - extracts subject (userId) + tid (tenantId) + roles
        - sets TenantContext (ThreadLocal)
        - sets Spring Security Authentication
  -> Controller -> Service -> Repository (tenant_id filtered)
  -> Response
```

Key flow components:
- JWT validation + TenantContext population: `auth/security/JwtAuthenticationFilter`
- JWT signing/verification: `auth/service/JwtProvider`, `auth/config/JwtBeansConfig`
- TenantContext storage: `tenant/context/TenantContext`
- Security entry/denied handlers: `auth/security/CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`

## Tenant isolation enforcement
- **JWT claim**: `tid` (UUID) is required and extracted by `JwtAuthenticationFilter`.
- **ThreadLocal context**: `TenantContext` stores tenantId + userId for request scope.
- **Repository scoping**: Repos query by `tenant_id` (e.g., `PropertyRepository`, `ContactRepository`, `DepositRepository`, `NotificationRepository`).
- **Service checks**: Services use `TenantContext` to fetch/guard tenant-specific data (e.g., `PropertyService`, `ContactService`, `DepositService`, `NotificationService`, `TenantService`).

## Error handling strategy
- Standard JSON contract: `common/error/ErrorResponse` + `ErrorCode`.
- Global handlers: `common/error/GlobalExceptionHandler` plus Spring Security handlers for 401/403.
- Error response includes: timestamp, status, error, code, message, path, optional fieldErrors.

## CI workflows
- Backend CI: `.github/workflows/backend-ci.yml`
- Frontend CI (guarded if frontend exists): `.github/workflows/frontend-ci.yml`
