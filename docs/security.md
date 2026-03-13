# Security

## Authentication Model

### CRM JWT
- Algorithm: HS256 (`JwtEncoder`/`JwtDecoder`).
- Claims:
  - `sub`: CRM user ID
  - `tid`: tenant ID
  - `roles`: CRM roles (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`)
  - `tv`: token version (revocation control)
  - `iat`, `exp`
- TTL is configurable (`JWT_TTL_SECONDS`, default 3600).

### Portal JWT
- Issued after magic-link verification (`/api/portal/auth/verify`).
- Claims:
  - `sub`: contact ID (not user ID)
  - `tid`: tenant ID
  - `roles`: `["ROLE_PORTAL"]`
  - `iat`, `exp`
- No `tv` claim; short-lived (2h), stateless.

## Token Revocation (CRM)
- `User.tokenVersion` increments on role changes and account disable.
- Each request compares JWT `tv` with cached user security info.
- Mismatch, missing user, or disabled user leads to 401.
- Cache (`userSecurityCache`) is evicted on role/enablement updates.

## Security Route Boundaries
Public routes include:
- `POST /auth/login`
- `POST /api/portal/auth/request-link`
- `GET /api/portal/auth/verify`
- `GET /actuator/health`, `GET /actuator/info`
- Swagger/OpenAPI endpoints
- `POST /tenants` bootstrap endpoint

Protected route groups:
- `/api/portal/**` -> `ROLE_PORTAL` only
- `/api/**` -> CRM roles only (`ADMIN`, `MANAGER`, `AGENT`)

## RBAC Conventions
- Use `@PreAuthorize("hasRole('ADMIN')")` style. Never include `ROLE_` in `hasRole(...)`.
- Apply method-level authorization for fine-grained control and mixed access paths.
- AGENT visibility is ownership-scoped in service layer for sensitive domains.

## Tenant Isolation
- Tenant comes from JWT `tid` and is stored in `TenantContext`.
- `TenantContext` must be cleared at request end (filter responsibility).
- Repository/service operations must remain tenant-scoped; never trust tenant IDs from payload/path.

## Error Semantics
- 401: invalid/expired token, missing auth, tokenVersion mismatch, disabled/deleted user.
- 403: authenticated but lacks required role/permission.
- API errors use standardized `ErrorResponse` + `ErrorCode`.

## Operational Security Notes
- `JWT_SECRET` is mandatory and must be at least 32 characters.
- Do not log raw Authorization headers or magic-link raw tokens.
- Secret scanning in CI is audit-only by default; Snyk handles SAST + OSS dependency vulnerability gates.

## Related References
- Compact security baseline: [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)
- Architecture and request flow: [01_ARCHITECTURE.md](01_ARCHITECTURE.md)
