# SECURITY_BASELINE.md — Security Configuration Reference

_Updated: 2026-03-05_

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
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`
- `GET /swagger-ui.html`
- `POST /tenants`
- `POST /api/portal/auth/request-link`
- `GET /api/portal/auth/verify`

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
- Configured via `app.rate-limit.*` in `application.yml` (legacy bucket4j config):
  - `app.rate-limit.login.{capacity,refill-period,exceeded-message}`
  - `app.rate-limit.portal-link.{capacity,refill-period,exceeded-message}`
- Sprint 1 IP + identity-key rate limiting uses `app.security.rate-limit.login.*`:
  - `app.security.rate-limit.login.ip-max` (env: `RATE_LIMIT_LOGIN_IP_MAX`, default 20)
  - `app.security.rate-limit.login.key-max` (env: `RATE_LIMIT_LOGIN_KEY_MAX`, default 10)
  - `app.security.rate-limit.login.window-seconds` (env: `RATE_LIMIT_LOGIN_WINDOW_SECONDS`, default 60)

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
| Gate | Tool | Threshold | Status |
|------|------|-----------|--------|
| SAST (Java + TS) | Snyk Code (`snyk.yml` code job) | SARIF → GitHub Security tab | ✅ Active |
| OSS Dependencies | Snyk OSS (`snyk.yml` open-source job) | Fail on HIGH+ | ✅ Active |
| OSS Schedule | Snyk OSS (weekly cron) | Fail on HIGH+ | ✅ Active |
| Dependency Review | Removed — requires GHAS; replaced by Snyk OSS (`snyk.yml` open-source job) | Fail on HIGH+ via Snyk | ❌ Removed |
| Secret Patterns | grep-based (`secret-scan.yml`) | Audit-only by default; optional fail mode via `SECRET_SCAN_ENFORCE=true` | ✅ Active |
| CodeQL SAST | Removed — requires GHAS (use Snyk Code instead) | — | ❌ Removed |

## Structured Logging
- Logstash Logback Encoder: JSON-formatted logs.
- No sensitive data (passwords, tokens) logged — verify new log lines do not log `Authorization` headers.
- Actuator endpoints: health (public), others (admin-only).

## Magic Link Token Security
- 32-byte `SecureRandom` → URL-safe base64 (256 bits of entropy).
- Stored as SHA-256 hex — even if DB is compromised, tokens cannot be reversed.
- 48h TTL + one-time use flag.
- Sent via email only (no URL parameters visible in logs after initial send).

## Transport Layer Security (TLS)

All traffic between client and server must be encrypted in any environment handling real user data.

**Mode A — Embedded Tomcat TLS:**
`SSL_ENABLED=true` activates Tomcat TLS on port 8443. A PKCS12 keystore is loaded from
`SSL_KEYSTORE_PATH`. TLS 1.0 and 1.1 are disabled; only TLS 1.2 and 1.3 are allowed.
An HTTP redirect connector on `HTTP_REDIRECT_PORT` (default 8080) issues 302 redirects
to the HTTPS port.

**Mode B — Nginx reverse proxy (production):**
`SSL_ENABLED=false`; Spring Boot listens plain HTTP on `127.0.0.1:8080`.
`FORWARD_HEADERS_STRATEGY=FRAMEWORK` enables Spring to trust `X-Forwarded-Proto` from Nginx.
Port 8080 must be firewall-blocked from external access.

**Protocol:** TLSv1.2 + TLSv1.3 only. TLS 1.0 and 1.1 disabled.

**Ciphers:** ECDHE + AES-GCM + ChaCha20 only. No RC4, no 3DES, no MD5.

**HSTS:** `max-age=31536000; includeSubDomains; preload` — set by Spring Boot when
`SSL_ENABLED=true`, set by Nginx in production. Never emitted over plain HTTP.

**Security headers set by Spring Boot (both modes):**
- `X-Frame-Options: DENY` — clickjacking prevention
- `Content-Security-Policy: default-src 'self'` — XSS / injection

**Security headers set by Nginx (Mode B):**
- `Strict-Transport-Security` — HSTS
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy`

**JWT security:** The `Authorization: Bearer` header is only safe when the connection
is encrypted. Plain HTTP must not be used in staging or production.

**Portal magic link:** `PORTAL_BASE_URL` must begin with `https://` in production.
Sending magic-link tokens over HTTP exposes them to interception.

**Reference:** See `docs/https.md` for complete setup instructions.
