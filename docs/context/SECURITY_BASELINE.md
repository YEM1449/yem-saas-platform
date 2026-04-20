# Security Baseline

This document summarizes the security posture visible in the implementation.

## 1. Security Objectives

- isolate each societe from every other societe
- separate buyer access from staff access
- prevent token reuse after role, membership, or password changes
- reduce common web risks such as XSS token theft, CSRF, clickjacking, and brute force login abuse
- provide enough auditability for operational investigation

## 2. Authentication Modes

| Mode | Principal | Cookie | Scope |
| --- | --- | --- | --- |
| CRM staff | `app_user` | `hlm_auth` | `/auth/*`, `/api/*`, `/api/admin/*` |
| Buyer portal | `contact` | `hlm_portal_auth` | `/api/portal/*` |

## 3. Staff JWT Structure

Current staff tokens include:

- `sub`: user UUID
- `roles`: Spring Security authorities
- `tv`: token version
- `sid`: active societe UUID when the session is societe-scoped
- `partial`: present only on multi-societe selection tokens
- `imp`: optional impersonating superadmin UUID

Important behaviors:

- partial tokens are only valid for `POST /auth/switch-societe`
- platform-level `SUPER_ADMIN` tokens may omit `sid`
- final JWTs are hidden from JavaScript by using httpOnly cookies

## 4. Portal JWT Structure

Portal tokens differ intentionally:

- `sub` is the buyer contact ID
- `sid` is the active societe
- `roles` contains only `ROLE_PORTAL`
- no `tv` claim is required because CRM user revocation rules do not apply to buyers

## 5. Route Protection Model

### Public routes

- `/auth/login`
- `/auth/logout`
- `/auth/switch-societe`
- `/auth/invitation/**`
- `/actuator/health`
- `/actuator/info`
- `/api/portal/auth/request-link`
- `/api/portal/auth/verify`
- `/api/portal/auth/logout`

### Protected route families

| Route family | Required role |
| --- | --- |
| `/api/admin/**` | `SUPER_ADMIN` |
| `/api/portal/**` | `ROLE_PORTAL` |
| `/api/**` | `ROLE_ADMIN`, `ROLE_MANAGER`, or `ROLE_AGENT` |
| `/v3/api-docs/**`, `/swagger-ui/**` | authenticated CRM roles or `SUPER_ADMIN` |

## 6. Cookie Security

### `hlm_auth`

- httpOnly
- configurable `Secure`
- configurable `SameSite`
- path `/`

### `hlm_portal_auth`

- httpOnly
- configurable `Secure`
- configurable `SameSite`
- path `/api/portal`

Why this matters:

- JavaScript cannot read the tokens directly
- buyer cookies stay out of CRM requests
- secure transport settings can be aligned with the deployment environment

## 7. Session Revocation

Staff sessions are revocable before expiry.

Mechanism:

1. `token_version` is embedded in the JWT as `tv`.
2. `JwtAuthenticationFilter` compares it against the cached server-side value.
3. role changes, password changes, disables, and certain membership actions evict the relevant cache entry.

Cache backends:

- Caffeine by default
- Redis when `app.redis.enabled=true`

## 8. Multi-Societe Isolation Controls

Isolation is enforced with defense in depth:

### Application layer

- services derive `societeId` from `SocieteContext`
- repositories query with `societeId`
- controllers do not allow cross-societe selectors to bypass current scope

### Database layer

- `RlsContextAspect` sets `app.current_societe_id`
- PostgreSQL RLS limits rows to matching `societe_id`
- nil-UUID bypass is reserved for explicit system-mode workflows

## 9. Authorization Model

### Platform roles

- `SUPER_ADMIN` manages societes, members, quotas, compliance settings, and impersonation

### Societe roles

- `ADMIN`
- `MANAGER`
- `AGENT`

Business rule preserved by code:

- only `SUPER_ADMIN` can assign `ADMIN`
- societe admins can manage managers and agents but cannot escalate to platform power

## 10. Abuse Resistance

### Login protection

`AuthService` enforces:

- per-IP rate limiting
- per-identity rate limiting
- account lockout after repeated failures
- timing-attack mitigation through a dummy password hash path for unknown users

### Portal link protection

`PortalAuthService` enforces:

- rate limiting on magic-link requests
- generic success messaging to avoid user enumeration
- hashed token storage
- 48-hour token expiry
- one-time-use verification

## 11. HTTP And Browser Protections

`SecurityConfig` enables:

- CSP
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- strict referrer policy
- restricted permissions policy
- optional HSTS when TLS is enabled at the app layer

## 12. File Handling Controls

- multipart limits are configured in Spring
- allowed media and document MIME types are configurable
- storage backends are abstracted rather than exposing raw provider semantics to controllers
- document and media access always remains tied to business ownership checks

## 13. Audit And Investigability

Key audit signals come from:

- commercial audit events
- security audit logger events for login failures, rate limits, and successful login
- outbox history for sent or failed messages
- notification and timeline aggregation

## 14. Operational Security Checklist

- set a strong `JWT_SECRET`
- enable secure cookies in production
- use exact `CORS_ALLOWED_ORIGINS`
- terminate TLS correctly and set `FORWARD_HEADERS_STRATEGY=FRAMEWORK` behind Nginx
- enable Redis for multi-instance revocation consistency
- store object storage credentials securely
- review open Swagger access only for trusted authenticated staff
