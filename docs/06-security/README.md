# Security Reference

## Latest Audit

- [Security Audit Report - 2026-04-01](./SECURITY_AUDIT_2026-04-01.md)
- [Snyk Security Configuration Guide](./SNYK_CONFIGURATION.md)

Spring Security · JWT · PostgreSQL RLS · Row-Level Isolation

---

## 1. Authentication Modes

| Mode | Token issued by | Principal | Societe context | Notes |
|---|---|---|---|---|
| CRM JWT | `JwtProvider` | `userId` (UUID string) | `sid` claim | Standard staff login; role: ADMIN/MANAGER/AGENT |
| Portal JWT | `PortalJwtProvider` | `contactId` (UUID string) | `tid` claim | Magic-link only; role: ROLE_PORTAL; no `tv` claim |
| SUPER_ADMIN JWT | `JwtProvider` | `userId` (UUID string) | No `sid` claim | Platform-level; no societe isolation |

---

## 2. JWT Claims

| Claim | Field name in code | Type | Description |
|---|---|---|---|
| `sub` | `subject` | UUID string | User ID (CRM/SUPER_ADMIN) or Contact ID (portal) |
| `roles` | `roles` | Array | `["ROLE_ADMIN"]`, `["ROLE_PORTAL"]`, etc. |
| `tv` | `tv` | Long | Token version — used for revocation; absent in portal JWTs |
| `sid` | `sid` | UUID string | Societe ID — absent for SUPER_ADMIN and portal tokens |
| `partial` | `partial` | Boolean | `true` if token is a partial token (societe selection pending) |
| `imp` | `imp` | UUID string | Impersonator's user ID; present only during impersonation sessions |

### JWT TTLs

| Token Type | TTL |
|---|---|
| CRM full token | Configured via `app.jwt.expiration` |
| CRM partial token | Short-lived; only valid for `POST /auth/switch-societe` |
| Portal token | 2 hours |
| Magic-link token (raw) | 48 hours (stored only as SHA-256 hash) |

---

## 3. CRM Auth Flow

### Step-by-Step Login

1. Client sends `POST /auth/login` with `{email, password}`.
2. `AuthService.login()` loads user via `userRepository.findFirstByEmail(email)`.
3. Checks account `enabled` and `locked_until` — returns `AccountLockedException` if locked.
4. Verifies BCrypt password hash. On failure: increments `failed_login_attempts`; locks account if threshold reached.
5. On success: resets `failed_login_attempts` and `locked_until`.
6. Loads `appUserSocieteRepository.findByIdUserIdAndActifTrue(userId)` — returns 401 if no active memberships.
7. If user has exactly one active societe membership: issues a full JWT (step 9).
8. If user has multiple memberships: issues a **partial token** (`partial=true`, no `sid`) and returns `requiresSocieteSelection: true`.
9. Client calls `POST /auth/switch-societe` with `{societeId}` and the partial token.
10. `AuthService.switchSociete()` validates membership and issues a full JWT including `sid`.

### Token Validation (per request)

1. `JwtAuthenticationFilter` extracts the `Authorization: Bearer <token>` header.
2. Decodes the JWT using the `JwtDecoder` bean (shared by `JwtProvider` and `PortalJwtProvider`).
3. Reads `roles` claim to determine the auth mode.
4. For `ROLE_PORTAL`: sets `contactId` as principal; skips `UserSecurityCacheService`.
5. For all other roles: calls `UserSecurityCacheService.validateTokenVersion(userId, tv)`. If `tv` is less than the cached `token_version`, the token is rejected with 401.
6. Sets `SocieteContext` ThreadLocal from the `sid` claim.
7. Creates `UsernamePasswordAuthenticationToken` with `SimpleGrantedAuthority("ROLE_ADMIN")` (etc.) and registers it in `SecurityContextHolder`.

---

## 4. Portal Magic-Link Flow

### Step-by-Step

1. Client submits email to `POST /api/portal/auth/magic-link`.
2. `PortalAuthService` looks up `Contact` by email and societe context.
3. Generates 32-byte `SecureRandom` → URL-safe Base64 → raw token.
4. Computes `SHA-256(rawToken)` → hex string stored in `portal_token.token_hash`.
5. Sets `expires_at = now() + 48h`, `used = false`.
6. Calls `EmailSender.send(contactEmail, subject, magicLinkUrl)` directly (not via outbox — no User FK for public endpoints).
7. Client clicks the link, which contains the raw token as a query parameter.
8. Client calls `POST /api/portal/auth/verify` with `{token}`.
9. `PortalAuthService` computes `SHA-256(token)`, looks up `portal_token` by hash.
10. Validates: `used == false`, `expires_at > now()`.
11. Marks `used = true`.
12. Issues portal JWT: `sub=contactId`, `roles=["ROLE_PORTAL"]`, `tid=societeId`, TTL 2h.

### Security Properties

| Property | Implementation |
|---|---|
| Raw token never stored | Only `SHA-256` hex hash stored in `portal_token.token_hash` |
| One-time use | `used = true` set atomically on first successful verify |
| 48-hour TTL | `expires_at` checked on verify |
| Rate limited | Magic-link requests are rate-limited per email address |
| No SSRF | `PORTAL_BASE_URL` is a server-side config value; the link is constructed server-side |

---

## 5. Token Revocation

### Mechanism

`app_user.token_version` is a monotonically incrementing integer. Every JWT issued by `JwtProvider` includes the `tv` claim set to the current `token_version` at issue time.

On each authenticated request, `UserSecurityCacheService.validateTokenVersion(userId, claimedTv)` checks that `claimedTv >= storedTokenVersion`. If the stored version has been incremented since the JWT was issued, the token is rejected with 401.

### Events that Increment token_version

- Role change on `AppUserSociete`
- User disabled (`enabled = false`)
- Password reset
- Membership deactivated (`AppUserSociete.actif = false`)

### Cache Layer

`UserSecurityCacheService` caches `token_version` lookups:

| Mode | Backend | Scope |
|---|---|---|
| Default | Caffeine | In-process; not shared between instances |
| Redis (`REDIS_ENABLED=true`) | Redis | Shared across instances; safe for horizontal scaling |

### Portal JWT Exception

Portal JWTs do not have a `tv` claim. Revocation for portal sessions is handled by the `portal_token.used` flag and the 2-hour TTL only.

---

## 6. Rate Limiting and Lockout

### Login Rate Limiting

Two independent limiters protect `POST /auth/login`:

1. **Per-IP limiter**: Sliding window; rejects with 429 when threshold exceeded.
2. **Per-email limiter**: Sliding window; rejects with 429 when threshold exceeded.

Note: `String.formatted()` with extra arguments silently drops them. A historical bug in `LoginRateLimitIT` caused `"email": "%s".formatted(tenant, email)` to use `tenant` as the email value. Verify template argument counts match placeholder counts.

### Account Lockout

After a configured number of consecutive failed passwords, the `app_user` record is locked:

```
failed_login_attempts >= threshold → locked_until = now() + lockout_duration
```

The `locked_until` timestamp is checked at step 3 of the login flow and returns `AccountLockedException` (423).

### Portal Magic-Link Rate Limiting

`POST /api/portal/auth/magic-link` is rate-limited per email address to prevent enumeration and abuse.

### Invitation Rate Limiting

`POST /api/mon-espace/utilisateurs/invitations` is rate-limited per admin user:
- Limit: 10 requests per hour
- Keyed by: `invitation:{adminId}`
- Config: `app.rate-limit.invitation.max-requests`, `app.rate-limit.invitation.window-seconds`
- Enforced by: `RateLimiterService.checkInvitation(adminId)` called in `UserManagementController.inviter()`

---

## 7. Multi-Société Isolation

Isolation is enforced at three independent layers. An attacker who bypasses one layer still faces the next.

### Layer 1 — Application: SocieteContext + Service Guards

`JwtAuthenticationFilter` sets `SocieteContext` (ThreadLocal) from the `sid` JWT claim on every request. Every service method calls `SocieteContextHelper.requireSocieteId()` as its first line, which throws `IllegalStateException` if the context is null. All repository queries include `WHERE societe_id = :societeId`.

### Layer 2 — AOP: RlsContextAspect

`RlsContextAspect` intercepts `@Service` and `@Repository` method calls. Inside the active transaction, it executes:

```sql
SET LOCAL app.current_societe_id = '<uuid>';
```

`@Order(LOWEST_PRECEDENCE - 1)` ensures this AOP advice runs inside the Spring transaction boundary (so `SET LOCAL` applies to the current transaction).

### Layer 3 — Database: PostgreSQL RLS

13 domain tables have `ROW SECURITY ENABLED`. PostgreSQL enforces the policy:

```sql
-- Policy: only rows where societe_id matches the session variable
CREATE POLICY societe_isolation ON contact
  USING (societe_id = current_setting('app.current_societe_id')::uuid);
```

The nil-UUID sentinel (`00000000-0000-0000-0000-000000000000`) bypasses RLS for system-context operations (e.g., `OutboundDispatcherScheduler`, `DataRetentionScheduler`).

---

## 8. Role Assignment Rules

| Actor | Can assign | Cannot assign |
|---|---|---|
| `SUPER_ADMIN` | `ADMIN`, `MANAGER`, `AGENT` to any societe | — |
| Company `ADMIN` | `MANAGER`, `AGENT` within their societe | `ADMIN` (privilege escalation prevention) |
| `MANAGER` | Nothing | All roles |
| `AGENT` | Nothing | All roles |

These rules are enforced by `SocieteRoleValidator`. Attempting to assign `ADMIN` as a company `ADMIN` returns 403.

### Storage Convention

`AppUserSociete.role` stores: `ADMIN`, `MANAGER`, or `AGENT` — no `ROLE_` prefix.

- `AdminUserService.toSocieteRole()` strips the `ROLE_` prefix before writing.
- `AuthService.toJwtRole()` adds the `ROLE_` prefix for JWT `GrantedAuthority`.

The `chk_societe_role` CHECK constraint enforces this at the database level. Storing `ROLE_ADMIN` causes a 500 error.

---

## 9. Transport Security

| Control | Implementation |
|---|---|
| Sessions | Stateless (`SessionCreationPolicy.STATELESS`); no CSRF risk from browser session cookies |
| CSRF | Disabled (`csrf().disable()`) — bearer token APIs are not vulnerable to CSRF |
| CORS | Allow-list via `CORS_ALLOWED_ORIGINS` env var; configured in `SecurityConfig` |
| CSP | `Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'` |
| X-Frame-Options | `DENY` — prevents clickjacking |
| HSTS | Enabled when embedded TLS is configured (`server.ssl.enabled=true`) |

### SecurityConfig Path Ordering

Spring Security evaluates rules in order; the first match wins:

1. `permitAll`: `/auth/**`, `/api/portal/auth/**`
2. `hasRole("PORTAL")`: `/api/portal/**`
3. `hasAnyRole("ADMIN","MANAGER","AGENT")`: `/api/**`
4. `hasRole("SUPER_ADMIN")`: `/api/admin/**`
5. All other requests: `authenticated()`

**Important**: `/api/admin/**` is guarded by `SUPER_ADMIN`. The `/api/users` path (used for company admin CRUD) must NOT be under `/api/admin/`.

---

## 10. GDPR Controls

### Contact GDPR Fields

Each `Contact` record has:
- `consentement_rgpd` — boolean consent flag
- `date_consentement` — consent capture timestamp
- `base_traitement` — legal basis for processing
- `duree_retention_jours` — retention period in days
- `anonymized_at` — set when anonymization is executed

### Available GDPR Operations

| Operation | Endpoint | Notes |
|---|---|---|
| Contact export | `GET /api/contacts/{id}/export` | Returns full contact data as JSON |
| Contact anonymization | `POST /api/contacts/{id}/anonymize` | Irreversible; blocked if signed contracts exist |
| User export | `GET /api/users/{id}/export` | Returns full user data as JSON |
| User anonymization | `POST /api/users/{id}/anonymize` | Blocked if signed contracts require identity retention |

### Anonymization Block Condition

Anonymization is blocked when a signed `SaleContract` references the contact or user as buyer or agent, because legal identity retention is required for active contracts.

### DataRetentionScheduler

Automated GDPR sweeps run via `DataRetentionScheduler`. The scheduler uses the nil-UUID context to query across all societes and processes contacts where `duree_retention_jours` has elapsed since last activity.

---

## 11. Known Security Gaps

The following gaps exist in the current implementation and have not yet been addressed:

| Gap | Location | Impact |
|---|---|---|
| Societe suspension not enforced at request time | `AuthService.login()` and domain service layer | A user belonging to a suspended societe can still log in and access resources |
| `max_biens`, `max_contacts`, `max_projets` not enforced at service layer | `PropertyService`, `ContactService`, `ProjectService` | Quota fields exist in `Societe` entity but no enforcement; only `max_utilisateurs` is actively enforced by `QuotaService` |
| `SocieteContext.role` slot not populated | `JwtAuthenticationFilter` | The role slot in `SocieteContext` is not set by the current filter; role is read from `SecurityContextHolder` instead |
| `POST /tenants` endpoint permitted without controller | `SecurityConfig` | `SecurityConfig.permitAll()` includes `/tenants` but no active controller exists at that path |
