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

## Transport Security (TLS/HTTPS)

See [docs/https.md](https.md) for the complete setup guide.

**Summary:**
- All production traffic must use HTTPS. Plain HTTP returns 301 redirect.
- HSTS header enforced with 1-year max-age and preload (only when `SSL_ENABLED=true`).
- TLS 1.0 / 1.1 disabled. TLS 1.2 + 1.3 only.
- Dev: embedded Tomcat self-signed cert (`scripts/generate-dev-cert.sh`).
- Prod: Nginx terminates TLS; Spring Boot trusts `X-Forwarded-Proto` (`FORWARD_HEADERS_STRATEGY=FRAMEWORK`).
- Portal magic-link URL must use `https://` (`PORTAL_BASE_URL` env var).

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

## Rate Limiting & Account Lockout

### Login Rate Limiting (Bucket4j)
- `LoginRateLimiter` maintains two in-memory token-bucket maps: per-IP and per-identity (`tenantKey:email`).
- Limits are applied before any DB access (fail-fast).
- Response on limit exceeded: HTTP 429 with `Retry-After: <seconds>` header and `X-RateLimit-Remaining: 0`.
- `ErrorCode.LOGIN_RATE_LIMITED`.
- Config (env vars): `RATE_LIMIT_LOGIN_IP_MAX` (default 20), `RATE_LIMIT_LOGIN_KEY_MAX` (default 10), `RATE_LIMIT_LOGIN_WINDOW_SECONDS` (default 60).
- Cleanup: idle (full-capacity) buckets are removed every 5 minutes via `@Scheduled`.

### Account Lockout
- Tracked via `failed_login_attempts` (INT) and `locked_until` (TIMESTAMP) on `app_user` (changeset 027).
- `User.recordFailedAttempt(maxAttempts, durationMinutes)` increments the counter and sets `lockedUntil` on threshold breach.
- `User.isLockedOut()` returns true if `lockedUntil` is in the future.
- Lockout check occurs after rate-limit but before password verification.
- Response: HTTP 401 with `ErrorCode.ACCOUNT_LOCKED`; message includes ISO-8601 `lockedUntil`.
- Config: `LOCKOUT_MAX_ATTEMPTS` (default 5), `LOCKOUT_DURATION_MINUTES` (default 15).
- Successful login resets `failedLoginAttempts` to 0.

## Structured Security Logging
Three named SLF4J loggers in `SecurityAuditLogger` (package `auth.service`):
- `security.auth` — `FAILED_LOGIN`, `LOGIN_SUCCESS`, `ACCOUNT_LOCKED`, `TOKEN_REVOCATION`
- `security.tenant` — `CROSS_TENANT_ATTEMPT`
- `security.ratelimit` — `RATE_LIMIT_TRIGGERED`

All events use `[SECURITY] event=X key=val ...` format at WARN level. Emails are masked: `joh***@acme.com`.
In production (`application-production.yml`), log level is WARN for all three loggers.

## Error Semantics
- 401: invalid/expired token, missing auth, tokenVersion mismatch, disabled/deleted user, account locked.
- 429: rate limit exceeded (login endpoint only).
- 403: authenticated but lacks required role/permission.
- API errors use standardized `ErrorResponse` + `ErrorCode`.

## Operational Security Notes
- `JWT_SECRET` is mandatory and must be at least 32 characters.
- Do not log raw Authorization headers or magic-link raw tokens.
- Secret scanning in CI is audit-only by default; Snyk handles SAST + OSS dependency vulnerability gates.
- `EMAIL_HOST`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` must be set via environment variables; never hardcoded. Test profile uses GreenMail (no real credentials needed).
- All TLS private keys and PKCS12 keystores are gitignored. Never commit `hlm-backend/src/main/resources/ssl/*.p12`.
- `PortalTokenCleanupScheduler` purges expired/used tokens daily at 03:00. Monitor its log line `[PORTAL-CLEANUP] Deleted N expired/used portal tokens` in production.

## Production Security Checklist
- Set `spring.profiles.active=production` to disable Swagger/OpenAPI endpoints.
- Set `RATE_LIMIT_LOGIN_IP_MAX` and `RATE_LIMIT_LOGIN_KEY_MAX` appropriate to expected load.
- Set `LOCKOUT_MAX_ATTEMPTS` and `LOCKOUT_DURATION_MINUTES` to meet compliance requirements.
- Configure log aggregation to capture `security.auth`, `security.tenant`, `security.ratelimit` streams.
- `JWT_SECRET` must be at least 32 characters and stored in a secrets manager (never in source).

## Email Provider Activation

`SmtpEmailSender` is activated only when `app.email.host` (env: `EMAIL_HOST`) is a **non-blank** string.

```java
@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")
```

> **Pitfall**: `@ConditionalOnProperty` treats an empty-string property value as "present" (only `null` and the literal string `"false"` are treated as absent). Since `application.yml` defaults `app.email.host: ${EMAIL_HOST:}` (empty string when `EMAIL_HOST` is unset), using `@ConditionalOnProperty` alone would activate `SmtpEmailSender` in every environment where `EMAIL_HOST` is not set, causing authentication failures at send time. The `@ConditionalOnExpression` correctly rejects blank values.

When `EMAIL_HOST` is not set → `NoopEmailSender` is active (logs, no actual send).
When `EMAIL_HOST` is set → `SmtpEmailSender` is active (real SMTP delivery).

## Related References
- Compact security baseline: [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)
- Architecture and request flow: [01_ARCHITECTURE.md](01_ARCHITECTURE.md)
