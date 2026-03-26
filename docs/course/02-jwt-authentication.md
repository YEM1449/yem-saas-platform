# Module 02 — JWT Authentication

**Audience**: junior to senior engineers
**Updated**: 2026-03-25 — reflects current implementation (`sid` claim, partial tokens, portal JWT)

> Reference: [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)

---

## 1. Introduction

Authentication uses **stateless JSON Web Tokens (JWTs)**. After a successful login, the server
issues a signed JWT. The client includes this token in every subsequent request. The server
validates the signature and reads the claims — no session store is needed.

This module explains:
- How the platform issues and validates JWTs
- The full claims structure for CRM, portal, and partial tokens
- How multi-société login branching works
- How token revocation works without a session store

---

## 2. Concepts

### 2.1 JWT Structure

A JWT has three base64url-encoded parts separated by dots:

```
header.payload.signature
```

**Header** — algorithm and type:
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload** — claims (data):
```json
{
  "sub": "user-uuid",
  "roles": ["ROLE_ADMIN"],
  "tv": 3,
  "sid": "societe-uuid",
  "iat": 1700000000,
  "exp": 1700003600
}
```

**Signature** — HMAC-SHA256 of header + payload using `JWT_SECRET`. Tamper-proof.

### 2.2 CRM JWT Claims

| Claim | Type | Present when | Meaning |
| --- | --- | --- | --- |
| `sub` | UUID string | always | User UUID (`app_user.id`) |
| `roles` | JSON array | always | Spring Security roles: `["ROLE_ADMIN"]` etc. |
| `tv` | integer | CRM token | Token version for revocation check |
| `sid` | UUID string | CRM + Portal | Active societe UUID |
| `partial` | boolean | multi-societe login | Marks a short-lived societe-selection token |
| `imp` | UUID string | impersonation | Impersonating SUPER_ADMIN UUID |

### 2.3 Portal JWT Claims

| Claim | Type | Meaning |
| --- | --- | --- |
| `sub` | UUID string | Contact UUID (buyer identity) |
| `roles` | JSON array | `["ROLE_PORTAL"]` |
| `sid` | UUID string | Societe UUID |
| *(no `tv`)* | — | Portal tokens are not revocable via token_version |

### 2.4 Partial Token (Multi-Société)

When a user belongs to multiple sociétés, login returns a **partial token** instead of a full CRM token:

```json
{
  "accessToken": "partial-jwt-here",
  "tokenType": "Partial",
  "expiresIn": 300,
  "requiresSocieteSelection": true,
  "societes": [
    { "id": "uuid-1", "nom": "ACME Immobilier" },
    { "id": "uuid-2", "nom": "Durand & Fils" }
  ]
}
```

The partial token is valid **only** for `POST /auth/switch-societe`. Any other API call with a
partial token returns 401. After societe selection, the backend issues a full scoped JWT.

---

## 3. Real Project Mapping

### 3.1 Token Generation

File: `auth/service/JwtProvider.java`

```java
// CRM token
public String generate(AppUser user, UUID societeId, String role) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(user.getId().toString())
        .claim("roles", List.of(role))       // "ROLE_ADMIN"
        .claim("tv", user.getTokenVersion())
        .claim("sid", societeId.toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(ttlSeconds))
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
}
```

Note: `role` in `AppUserSociete` is stored as `ADMIN`/`MANAGER`/`AGENT`.
`AuthService.toJwtRole()` adds the `ROLE_` prefix before embedding in the JWT claim.
`AdminUserService.toSocieteRole()` strips it when writing back to the database.

### 3.2 Token Validation

File: `auth/security/JwtAuthenticationFilter.java`

Steps on every request:
1. Read `Authorization: Bearer <token>` header.
2. Decode JWT using Spring's `JwtDecoder` (validates signature + expiry automatically).
3. Check if `partial` claim is true and route is not `/auth/switch-societe` → 401.
4. Detect `ROLE_PORTAL` → skip `UserSecurityCacheService` (contact, not user).
5. For CRM tokens: call `UserSecurityCacheService.validate(userId, tokenVersion)` → checks `enabled` flag and `tv` match.
6. Set `SocieteContext` from `sid` claim.
7. Set `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken`.
8. In `finally`: call `SocieteContext.clear()`.

### 3.3 Token Storage (Frontend)

| Token type | localStorage key | Used on routes |
| --- | --- | --- |
| CRM JWT | `hlm_access_token` | `/auth/**`, `/api/**` |
| Portal JWT | `hlm_portal_token` | `/api/portal/**` |

Two Angular interceptors handle attachment — one per token type.

---

## 4. Step-by-Step: Full Login Flow

```
Client → POST /auth/login { email, password }
         ↓
AuthService.login()
  1. Check IP rate limit (RATE_LIMIT_LOGIN_IP_MAX per RATE_LIMIT_LOGIN_WINDOW_SECONDS)
  2. Check per-identity rate limit (RATE_LIMIT_LOGIN_KEY_MAX)
  3. Find user by email: findFirstByEmail(email)
  4. Check locked_until: if past now, account is locked → 401
  5. Verify BCrypt hash
  6. On wrong password: increment failed_login_attempts; if >= LOCKOUT_MAX_ATTEMPTS → set locked_until
  7. On success: reset failed_login_attempts; load AppUserSociete memberships
  8. If 0 active memberships → 401
  9. If 1 membership → issue full CRM JWT with sid
 10. If N memberships → issue partial JWT (300s TTL), return societes list
         ↓
Client receives token
  • Single societe: store as hlm_access_token, proceed
  • Multi-societe: show selection UI → POST /auth/switch-societe { societeId }
                   → server issues full JWT → store as hlm_access_token
```

---

## 5. Best Practices

| Rule | Reason |
| --- | --- |
| `JWT_SECRET` must be 32+ random chars | HS256 requires sufficient entropy |
| Never log or expose `JWT_SECRET` | Possession = full token forgery |
| TTL should be short (≤1h for CRM) | Limits damage from leaked tokens; revocation via tv handles rest |
| Check `tv` claim on every request | Token revocation is near-real-time (bounded by cache TTL) |
| Partial tokens are one-use | `switch-societe` issues a full token; partial is then discarded |

---

## 6. Common Mistakes

### Mistake 1: Missing token_version check

```java
// WRONG — only checks signature, not revocation
boolean valid = jwtDecoder.decode(token) != null;

// CORRECT — also validate token version via cache
userSecurityCacheService.validate(userId, tokenVersionFromClaim);
```

### Mistake 2: Confusing role format

```java
// WRONG — storing with ROLE_ prefix in AppUserSociete
appUserSociete.setRole("ROLE_ADMIN");
// → violates chk_societe_role CHECK constraint (500 error)

// CORRECT — short form in DB
appUserSociete.setRole("ADMIN");
// AuthService.toJwtRole() adds "ROLE_" for the JWT
```

### Mistake 3: Using portal token on CRM route

Portal tokens have `ROLE_PORTAL`. CRM routes require `ROLE_ADMIN/MANAGER/AGENT`.
Attempting to call `GET /api/contacts` with a portal token → 403 Forbidden.

---

## 7. Exercises

**Exercise 1 — Decode a JWT**
1. Login with `admin@acme.com / Admin123!Secure`.
2. Copy the `accessToken` value.
3. Paste it at [jwt.io](https://jwt.io) (or use `base64 -d` on the payload part locally).
4. Identify each claim: `sub`, `roles`, `tv`, `sid`, `exp`.
5. Decode the `sid` — match it to the societe in the database.

**Exercise 2 — Observe Token Revocation**
1. Login with an admin user, copy the token.
2. Use `PATCH /api/users/{id}` to change the user's role (this increments `token_version`).
3. Retry any API call with the old token.
4. Observe 401.

**Exercise 3 — Test the Partial Token**
1. Create a user that belongs to two sociétés (use repository directly in an IT test).
2. Call `POST /auth/login`.
3. Assert the response has `requiresSocieteSelection: true` and `societes` list with 2 entries.
4. Call `POST /auth/switch-societe` with one of the IDs.
5. Assert the response is a full JWT (no `partial` claim, no `requiresSocieteSelection`).

---

## 8. Advanced Topics

### 8.1 SUPER_ADMIN Token

`SUPER_ADMIN` tokens have no `sid` claim. Route `/api/admin/**` is secured by
`hasRole("SUPER_ADMIN")` in `SecurityConfig`. If a SUPER_ADMIN calls a CRM endpoint,
`requireSocieteId()` will throw because `SocieteContext.getSocieteId()` is null.

### 8.2 Impersonation

`SUPER_ADMIN` can call `POST /api/admin/societes/{id}/impersonate/{userId}` to receive
a scoped JWT for a company member. The token carries an `imp` claim with the SUPER_ADMIN's
UUID for audit tracing. The impersonation token behaves like a normal CRM token except
`SocieteContext.impersonatedBy` is populated.

### 8.3 Spring Security Role Prefix Convention

Spring Security adds a `ROLE_` prefix when evaluating `hasRole('ADMIN')`.
- `hasRole('ADMIN')` internally checks for authority `ROLE_ADMIN`.
- `hasAnyRole('ADMIN', 'MANAGER')` checks for `ROLE_ADMIN` or `ROLE_MANAGER`.
- **Never** write `hasRole('ROLE_ADMIN')` — Spring would look for `ROLE_ROLE_ADMIN`.
