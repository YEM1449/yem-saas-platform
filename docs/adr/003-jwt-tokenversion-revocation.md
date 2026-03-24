# ADR 003 — JWT Revocation via tokenVersion

**Date:** 2026-03-22
**Authors:** YEM Platform Team

---

## Status

Accepted

---

## Context

The platform issues stateless JWTs for authentication. Stateless tokens have a well-known limitation: they cannot be revoked before their expiry time. This is problematic in several high-stakes scenarios:

- A user's role is changed (downgraded from MANAGER to AGENT). Their current token still carries the old role — they retain elevated access until the token expires.
- A user is removed from a société. Their token still grants access to that company's data until expiry.
- A SUPER_ADMIN suspends a société. All member tokens should become invalid immediately.
- A user logs out or requests account deactivation. Their token should not remain valid.

A full server-side session store (Redis blocklist of revoked JWT IDs) would solve this but reintroduces statefulness, increases infrastructure coupling, and adds a Redis round-trip to every authenticated request.

---

## Decision

The platform uses a **tokenVersion** mechanism for near-real-time JWT revocation without a token blocklist.

**How it works:**

1. The `app_user` table has an integer `token_version` column (default 0).
2. Every issued JWT carries a `tv` claim containing the user's `tokenVersion` at the time of issuance.
3. On every authenticated request, `JwtAuthenticationFilter` reads the `tv` claim from the token and compares it to the current `tokenVersion` in `UserSecurityCacheService` (a short-lived in-memory or Redis-backed cache over the database value).
4. If `tv` in the token does not match the database value, the filter skips authentication — Spring Security returns 401 with `TOKEN_INVALIDATED`.

**When tokenVersion is incremented:**

- `UserManagementService.changerRole()` — role change
- `UserManagementService.retirerMembre()` — member removal
- `SocieteService.desactiver()` — société suspension (increments for all members atomically)

Incrementing `tokenVersion` immediately invalidates all previously issued tokens for that user. The next login produces a new token with the updated version.

**Cache design:**

`UserSecurityCacheService` caches `UserSecurityInfo` (userId → {enabled, tokenVersion}) with a short TTL (Caffeine in development, Redis in production when `REDIS_ENABLED=true`). This limits database reads to once per TTL window per user, rather than once per request.

**Portal tokens are exempt:** Tokens with `ROLE_PORTAL` do not go through `UserSecurityCacheService` because the `sub` claim is a contact ID (not a CRM user ID). Portal sessions are invalidated by magic link single-use semantics and a 2-hour TTL.

**SUPER_ADMIN tokens:** These carry `tokenVersion` just like company-tier tokens and are subject to the same revocation mechanism.

---

## Consequences

**Positive:**
- Role changes, member removals, and company suspensions take effect within the cache TTL (seconds to minutes), not at token expiry (which could be hours).
- No token blocklist or Redis dependency required for revocation. Redis is optional and only affects cache distribution, not correctness.
- The mechanism is transparent to API clients: revoked tokens receive a standard 401 response with `TOKEN_INVALIDATED` code, prompting re-authentication.

**Negative:**
- There is a window of up to one cache TTL during which a revoked token can still authenticate. This is a deliberate trade-off between security and performance. For the highest-sensitivity operations (account takeover recovery, legal investigation), the cache TTL should be set low.
- Incrementing `tokenVersion` logs out all active sessions for the user across all devices simultaneously. This is intentional for security events but may surprise a user who expects only the current session to be affected by a role change they performed themselves.
- The `tv` claim is a breaking change from tokens issued without it. Tokens without a `tv` claim are treated as version 0 (backward compatibility, `safeExtractTokenVersion()` returns 0 on missing claim).

---

## Alternatives Considered

**JWT blocklist in Redis:** A set of revoked JWI IDs checked on every request. Correct, but requires Redis as a hard dependency. Adds latency. Grows unboundedly unless pruned by expiry. Rejected in favour of `tokenVersion`.

**Short token TTL (e.g., 5 minutes) + refresh tokens:** Limits the revocation window without server state, but requires a refresh token flow, a refresh endpoint, and client-side token rotation logic. Significantly more complex for the frontend. May be adopted in a future iteration.

**Re-validate user on every request against the database:** Correct, but a database round-trip per request at scale is unacceptable. The cache layer in `UserSecurityCacheService` is the compromise that achieves near-real-time revocation at low cost.
