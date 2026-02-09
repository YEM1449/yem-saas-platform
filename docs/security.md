# Security

## JWT configuration
- **Algorithm:** HS256 (HMAC) via Spring Security JWT encoder/decoder.
- **Required claims:**
  - `sub` = userId (UUID string)
  - `tid` = tenantId (UUID string)
  - `roles` = list of role strings (e.g., `ROLE_ADMIN`)
- **TTL:** configured via `security.jwt.ttl-seconds`.
- **Fail-fast secret:** app startup fails if `security.jwt.secret` is missing/blank.

Key classes:
- `auth/config/JwtBeansConfig` (encoder/decoder, HS256)
- `auth/config/JwtProperties` (validated secret + TTL)
- `auth/service/JwtProvider` (generate/validate/extract claims)
- `auth/security/JwtAuthenticationFilter` (reads token, sets `TenantContext`)

## RBAC conventions
- Roles are stored as `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`.
- `@PreAuthorize("hasRole('ADMIN')")` expects `ROLE_ADMIN` in authorities (Spring auto-prefixes).
- `roles` claim is a **list** of strings; default role is `ROLE_AGENT` when missing.

## Tenant context rules
- `JwtAuthenticationFilter` extracts `tid` and stores it in `TenantContext`.
- `TenantContext` is ThreadLocal and **must be cleared** after each request (handled by filter).
- Services and repositories must scope queries by `tenant_id`.

## 401 vs 403 behavior
- **401** (`UNAUTHORIZED`): no/invalid token, handled by `CustomAuthenticationEntryPoint`.
- **403** (`FORBIDDEN`): valid token without role/tenant access, handled by `CustomAccessDeniedHandler` or `GlobalExceptionHandler`.

## Common pitfalls
- **ROLE_ROLE_ mismatch:** don’t add `ROLE_` twice in `hasRole()` expressions.
- **TenantContext leakage:** always clear ThreadLocal (already done in `JwtAuthenticationFilter`).
- **Missing tid claim:** token is treated as invalid; access is denied.
