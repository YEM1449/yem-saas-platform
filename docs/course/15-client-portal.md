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
6. Send email with link: {PORTAL_BASE_URL}/portal/verify?token={raw-token}
7. Buyer clicks link → GET /api/portal/auth/verify?token={raw-token}
8. Hash the token → SHA-256 hex → look up in portal_token
9. Validate: not expired, not used
10. Mark token as used (one-time)
11. Return portal JWT {sub=contactId, roles=["ROLE_PORTAL"], TTL=2h}
```

### Why SHA-256 hash in the DB?

Only the SHA-256 hash is stored. If the database is compromised, attackers cannot reconstruct the original token values. The raw token is only in the email and in the URL parameter.

---

## Portal JWT Claims

| Claim | Value |
|-------|-------|
| `sub` | contactId (UUID) — NOT userId |
| `roles` | `["ROLE_PORTAL"]` |
| `tid` | tenantId |
| _(no `tv`)_ | — portal principals are contacts, not `app_user` rows |

TTL: 2 hours (longer than CRM tokens for better UX — buyers may not check email immediately).

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
| `portal/contract/service/PortalContractService.java` | Contact-scoped contract queries |
| `auth/security/JwtAuthenticationFilter.java` | `isPortalToken()` bypass |

---

## Exercise

1. Open `PortalAuthService.java` and trace the magic link generation.
2. Find where the SHA-256 hash is computed and stored.
3. Open `JwtAuthenticationFilter.java` and find `isPortalToken()`.
4. In a test, call `PortalJwtProvider.generate(randomContactId, tenantId)` to create a portal bearer token.
5. Call `GET /api/portal/contracts` with this token — verify it returns an empty array (no contracts for this contact), not 401 or 403.
