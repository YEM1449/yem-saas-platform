# SECURITY_BASELINE.md — Security Configuration Reference

_Updated: 2026-03-04_

## Authentication

| Mechanism | Detail |
|-----------|--------|
| Algorithm | HS256 (HMAC-SHA256) |
| Key minimum | 32 characters (app refuses to start if shorter) |
| Token header | `Authorization: Bearer <JWT>` |
| Token validation | `JwtAuthenticationFilter` (OncePerRequestFilter) |
| Token revocation | `tv` (tokenVersion) claim checked per-request via cache |
| Portal auth | Separate JWT via `PortalJwtProvider`; no `tv` claim; 2h TTL |

## JWT Claims (CRM)
- `sub`: userId (UUID string)
- `tid`: tenantId (UUID string)
- `roles`: list of role strings (e.g., `["ROLE_ADMIN"]`)
- `tv`: tokenVersion (integer, for revocation)
- `iat`, `exp`: standard issued-at / expiry

## JWT Claims (Portal)
- `sub`: contactId (UUID string)
- `tid`: tenantId (UUID string)
- `roles`: `["ROLE_PORTAL"]`
- `iat`, `exp`
- No `tv` claim — portal tokens are not revocable (expire in 2h)

## Public Endpoints
- `POST /auth/login`
- `POST /api/portal/auth/magic-link`
- `GET /api/portal/auth/verify`
- `GET /actuator/health`

## RBAC Annotations
```java
// Correct pattern (Spring adds ROLE_ prefix)
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
@PreAuthorize("hasRole('PORTAL')")

// WRONG — do NOT use:
@PreAuthorize("hasRole('ROLE_ADMIN')")   // double-prefix bug
```

## Tenant Isolation
- Never trust `tenantId` from request body or path parameters.
- Always use `TenantContext.getTenantId()` in service layer.
- Repository queries MUST include `AND t.tenant.id = :tenantId`.
- Cross-tenant isolation verified by `CrossTenantIsolationIT`.

## Password Security
- Passwords hashed with BCrypt.
- Seed password `Admin123!` is BCrypt-hashed in Liquibase changeset.
- Password reset: admin-only endpoint; generates temporary credential.

## Rate Limiting
- Bucket4j (v8.10.1) present as dependency.
- [OPEN POINT] Rate limiting configuration not visible in main application.yml. Verify `RateLimitConfig` or equivalent.

## CORS
- `CorsConfig` bean — allowed origins configurable via `app.cors.allowed-origins` property.
- Dev: Angular proxy removes CORS requirement (port 4200 → 8080 same-origin in practice).
- Production: set `CORS_ALLOWED_ORIGINS` to your frontend domain.

## Input Validation
- Bean Validation (`@Valid`, `@NotBlank`, `@Email`, etc.) on all DTOs.
- `JwtProperties` has `@Validated + @NotBlank` — fail-fast on startup.
- `GlobalExceptionHandler` converts `MethodArgumentNotValidException` to `VALIDATION_ERROR` envelope.

## Secret Management
- No secrets in code — all via environment variables.
- `.env.example` shows variable names only (no real values).
- `.gitignore` excludes `.env`.
- GitHub Actions: `SNYK_TOKEN`, `SNYK_ORG` as repository secrets.

## CI Security Gates
| Gate | Tool | Threshold |
|------|------|-----------|
| SAST (Java + TS) | CodeQL | security-and-quality queries |
| SAST (Code) | Snyk Code | SARIF → GitHub Security tab |
| OSS Dependencies | Snyk OSS | Fail on HIGH+ |
| Dependency Review | GitHub dep-review | Fail on HIGH+ (PRs) |
| Secret Patterns | grep-based (audit-only) | Warning only |

## Structured Logging
- Logstash Logback Encoder: JSON-formatted logs.
- No sensitive data (passwords, tokens) logged — verify new log lines do not log `Authorization` headers.
- Actuator endpoints: health (public), others (admin-only).

## Magic Link Token Security
- 32-byte `SecureRandom` → URL-safe base64 (256 bits of entropy).
- Stored as SHA-256 hex — even if DB is compromised, tokens cannot be reversed.
- 48h TTL + one-time use flag.
- Sent via email only (no URL parameters visible in logs after initial send).
