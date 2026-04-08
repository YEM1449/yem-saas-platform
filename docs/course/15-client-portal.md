# Module 15 — Client Portal

## Learning Objectives

- Describe the magic link authentication flow step by step
- Explain how `ROLE_PORTAL` differs from CRM roles in the JWT filter
- Identify how portal endpoints enforce contact-level data isolation

---

## Magic Link Flow

```
1. Buyer submits email to POST /api/portal/auth/request-link
2. PortalAuthService looks up contact by email
3. If not found → silently succeed (don't reveal email existence)
4. Generate token: 32-byte SecureRandom → URL-safe base64
5. Store SHA-256 hex of token in portal_token table (TTL 48h)
6. Send email with link: {PORTAL_BASE_URL}/portal/login?token={raw-token}
7. Buyer clicks link → GET /api/portal/auth/verify?token={raw-token}
8. Hash the token → SHA-256 hex → look up in portal_token
9. Validate: not expired, not used
10. Mark token as used (one-time)
11. Set httpOnly cookie `hlm_portal_auth` (path `/api/portal`, TTL 2h)
12. Return `{ accessToken: "" }` so the browser never needs the raw portal JWT
```

### Why SHA-256 hash in the DB?

Only the SHA-256 hash is stored. If the database is compromised, attackers cannot reconstruct the original token values. The raw token is only in the email and in the URL parameter.

---

## Portal JWT Claims

| Claim | Value |
|-------|-------|
| `sub` | contactId (UUID) — NOT userId |
| `roles` | `["ROLE_PORTAL"]` |
| `sid` | societeId |
| _(no `tv`)_ | — portal principals are contacts, not `app_user` rows |

TTL: 2 hours (longer than CRM tokens for better UX — buyers may not check email immediately).

## Frontend Session Model

- Buyers land on `/portal/login`.
- If a `token` query parameter is present, `PortalLoginComponent` strips it from the URL and calls `GET /api/portal/auth/verify`.
- The backend sets `hlm_portal_auth` as an httpOnly cookie scoped to `/api/portal`.
- The frontend does not store a portal JWT in `localStorage`.
- `PortalSessionStore` only tracks whether the session has already been validated in this SPA runtime.
- `portalInterceptor` adds `withCredentials: true` to portal API requests so the cookie is sent.
- `portalGuard` validates the session via backend tenant-info lookup when needed.

---

## JwtAuthenticationFilter Portal Path

`JwtAuthenticationFilter.isPortalToken()` checks for `ROLE_PORTAL`:

```java
boolean isPortalToken = authorities.stream()
    .anyMatch(a -> a.getAuthority().equals("ROLE_PORTAL"));

if (isPortalToken) {
    // Set principal = contactId
    // Skip UserSecurityCacheService (contactId is not an app_user ID)
} else {
    // Normal CRM path: check userSecurityCache
}
```

Without this bypass, `UserSecurityCacheService.load(contactId)` would look up a contactId in the `app_user` table and throw `UserNotFoundException`.

---

## Security Config for Portal

```
POST /api/portal/auth/request-link → permitAll
GET  /api/portal/auth/verify       → permitAll
/api/portal/**                     → hasRole('PORTAL')
/api/**                            → hasAnyRole('ADMIN','MANAGER','AGENT')
```

Portal users cannot access CRM endpoints (`/api/**` requires CRM roles). CRM users cannot access portal endpoints (they have CRM roles, not `ROLE_PORTAL`).

---

## Contact-Level Data Isolation

Portal services extract the `contactId` from the Spring Security principal:

```java
UUID contactId = UUID.fromString(
    SecurityContextHolder.getAuthentication().getPrincipal().toString()
);
```

Every portal repository query scopes to this `contactId`:

```java
List<SaleContract> contracts = contractRepo
    .findByTenantIdAndBuyerContactId(tenantId, contactId);
```

Cross-contact access returns 404, not 403. This prevents information leakage about whether a different contact exists.

---

## Source Files

| File | Purpose |
|------|---------|
| `portal/auth/service/PortalAuthService.java` | Magic link generation and verification |
| `portal/auth/service/PortalJwtProvider.java` | Portal JWT generation |
| `portal/auth/api/PortalAuthController.java` | Request-link and verify endpoints |
| `auth/security/PortalCookieHelper.java` | Issue and clear the httpOnly portal auth cookie |
| `portal/contract/service/PortalContractService.java` | Contact-scoped contract queries |
| `auth/security/JwtAuthenticationFilter.java` | `isPortalToken()` bypass |

---

## Exercise

1. Open `PortalAuthService.java` and trace the magic link generation.
2. Find where the SHA-256 hash is computed and stored.
3. Open `PortalAuthController.java` and confirm that verify sets an httpOnly cookie and returns an empty `accessToken`.
4. Open `JwtAuthenticationFilter.java` and find `isPortalToken()`.
5. In a backend test, call `PortalJwtProvider.generate(randomContactId, societeId)` and send it as either a bearer token or the `hlm_portal_auth` cookie.
6. Call `GET /api/portal/contracts` with this session and verify it returns an empty array for an unrelated contact, not data leakage.
