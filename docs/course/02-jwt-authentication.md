# Module 02 — JWT Authentication

## Learning Objectives

- Describe the JWT claims used in this platform
- Explain how `JwtProvider` generates and validates tokens
- Identify what happens when a token is expired or tampered with

---

## Concept

Authentication uses stateless JSON Web Tokens (JWTs). After a successful login, the server issues a signed JWT. The client includes this token in every subsequent request. The server validates the signature and reads the claims — no session store is needed.

---

## Token Claims

| Claim | Type | Value |
|-------|------|-------|
| `sub` | UUID string | User ID (or contactId for portal tokens) |
| `tid` | UUID string | Tenant ID |
| `roles` | JSON array | `["ROLE_ADMIN"]` |
| `tv` | integer | Token version (for revocation) |
| `iat` | Unix epoch | Issued-at time |
| `exp` | Unix epoch | Expiry time (iat + JWT_TTL_SECONDS) |

---

## Algorithm

**HS256** (HMAC-SHA256). The signing key is `JWT_SECRET` — a symmetric secret known only to the server. Minimum 32 characters.

---

## Token Generation

`JwtProvider.generate(AppUser user)`:
1. Reads `user.getId()`, `user.getTenant().getId()`, `user.getRole().name()`, `user.getTokenVersion()`.
2. Builds a `JwtClaimsSet` with all claims and expiry = now + `JWT_TTL_SECONDS`.
3. Calls `jwtEncoder.encode(JwtEncoderParameters.from(claims))`.
4. Returns the token string.

---

## Token Validation

`JwtAuthenticationFilter` calls `jwtProvider.isValid(token)`:
1. `JwtDecoder.decode(token)` — Spring Security verifies the HS256 signature and checks `exp`.
2. If signature is invalid → `JwtException` → 401.
3. If expired → `JwtException` → 401.
4. If valid → extract claims and proceed.

---

## Portal Tokens

`PortalJwtProvider` issues portal tokens with:
- `sub` = contactId (not userId)
- `roles` = `["ROLE_PORTAL"]`
- TTL = 2 hours (longer than CRM tokens)
- No `tv` claim (portal principals are contacts, not `app_user` rows)

The same `JwtEncoder`/`JwtDecoder` beans validate both token types.

---

## Source Files

| File | Purpose |
|------|---------|
| `auth/service/JwtProvider.java` | CRM token generation and validation |
| `auth/config/JwtBeansConfig.java` | JwtEncoder and JwtDecoder bean setup |
| `auth/config/JwtProperties.java` | @ConfigurationProperties for secret and TTL |
| `portal/auth/service/PortalJwtProvider.java` | Portal token generation |
| `auth/security/JwtAuthenticationFilter.java` | Token extraction and validation per-request |

---

## Exercise

1. Open `JwtProvider.java` and read `generate(AppUser user)`.
2. Identify the line that sets the `tv` claim.
3. Open `JwtAuthenticationFilter.java` and find where the filter reads the `tv` claim from the decoded JWT.
4. Run: `curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@acme.com","password":"Admin123!Secure"}' | jq .token`
5. Decode the token at https://jwt.io and verify the claims match the table above.
