# Module 03 — Token Revocation

## Learning Objectives

- Explain why signature-valid JWTs still need server-side revocation checks
- Describe how `tokenVersion` and `UserSecurityCacheService` work together
- Identify which business actions revoke active CRM sessions
- Understand the difference between explicit cache eviction and TTL-bounded propagation

## The Core Problem

A JWT can be:

- correctly signed
- not expired
- structurally valid

and still represent permissions that are no longer allowed.

Examples:

- an admin disables a compromised account
- a manager is demoted to agent
- a user is removed from a société
- a temporary password reset should invalidate old sessions

If the backend trusted signature and expiry alone, those old tokens would continue to work until `exp`.

## The Platform’s Solution

The system uses a `tokenVersion` counter on CRM users.

Relevant pieces:

- [User.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/domain/User.java)
- [UserSecurityInfo.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityInfo.java)
- [UserSecurityCacheService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java)
- [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java)

The idea is simple:

1. every CRM JWT embeds `tv`
2. every authenticated CRM request loads cached `(enabled, tokenVersion)` state
3. if the cached record says the user is disabled or the versions differ, the token is rejected

## Request-Time Algorithm

On a CRM request:

1. `JwtAuthenticationFilter` decodes the token
2. it extracts `sub`, `sid`, `roles`, and `tv`
3. it calls `userSecurityCacheService.getSecurityInfo(userId)`
4. it compares the cached `tokenVersion` with the JWT claim
5. it rejects the token if:
   - the user is missing
   - the user is disabled
   - the version differs

In code, the important branch is the CRM branch, not the portal branch. Portal sessions deliberately skip this check.

## What Is Cached

`UserSecurityCacheService` caches:

- `enabled`
- `tokenVersion`

It does not cache the full user profile or full role matrix.

That is intentional:

- the filter only needs enough data to answer “is this token still valid?”
- smaller cached records reduce invalidation complexity

## Revocation Triggers In The Codebase

### Admin user management

In [AdminUserService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/service/AdminUserService.java):

| Action | Increments tokenVersion | Explicit cache evict |
| --- | --- | --- |
| `changeRole(...)` | yes | yes |
| `setEnabled(...)` | yes | yes |
| `resetPassword(...)` | yes | yes |

### Membership management

In [UserManagementService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementService.java):

| Action | Increments tokenVersion |
| --- | --- |
| change member role | yes |
| remove member from société | yes |

### Société-wide membership operations

In [SocieteService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java):

| Action | Increments tokenVersion |
| --- | --- |
| add member | yes |
| update member role | yes |
| remove member | yes |
| deactivate société | yes for active members |

The important mental model is:

- some flows revoke a single user
- some flows revoke every member affected by a société-level operation

## Eviction Vs TTL

This is the operational nuance engineers often miss.

### Fast path: explicit eviction

Some services call `userSecurityCacheService.evict(userId)` immediately after mutating security-sensitive data.

Example flows:

- role change in `AdminUserService`
- disable in `AdminUserService`
- password reset in `AdminUserService`

When that happens, the next request will usually reload current security state immediately.

### Bounded path: TTL only

Other flows increment `tokenVersion` without explicit cache eviction.

In those cases, revocation still happens, but it is bounded by the cache TTL.

The code documents this TTL in:

- [UserSecurityCacheService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java)
- [CacheConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/CacheConfig.java)

So the practical guarantee is:

- immediate or near-immediate when explicit eviction is used
- otherwise within roughly the configured cache lifetime

## Caffeine Vs Redis

### Single instance

Caffeine is enough:

- in-process
- low latency
- simple operationally

### Multi-instance deployment

Redis is the safer choice:

- each JVM has its own Caffeine cache
- one instance may observe a revocation before another if only local caches are used
- shared Redis-backed cache reduces that divergence

This does not eliminate all race windows by itself, but it aligns instances around a shared cache state.

## Portal Sessions Are Different

Portal sessions do not use `tokenVersion`.

Why:

- buyers are `contact` records, not `app_user` records
- portal JWTs omit the `tv` claim
- portal sessions are invalidated through magic-link single-use semantics, cookie expiration, and JWT TTL

That is why `JwtAuthenticationFilter` explicitly skips `UserSecurityCacheService` when it detects `ROLE_PORTAL`.

## Security And Product Tradeoffs

This design intentionally accepts a bounded propagation window in exchange for fast stateless request handling.

Benefits:

- every request does not hit the database directly
- revocation still works without central server-side session objects
- platform scales cleanly for normal CRM traffic

Tradeoff:

- unless a path explicitly evicts the cache, revocation may take up to the cache TTL to become effective everywhere

That tradeoff is acceptable here because:

- CRM JWT TTLs are short
- sensitive admin flows often evict explicitly
- severe actions like account disable and role change still become effective quickly

## Common Mistakes

### Mistake 1: assuming signature validation is enough

It is not. Signature validation proves authenticity, not current authorization.

### Mistake 2: forgetting password resets are revocation events

In this codebase, temporary password reset does bump `tokenVersion`. Older course material often misses this detail.

### Mistake 3: assuming all revocation flows evict the cache

Some do, some do not. Always verify the service path you are touching.

### Mistake 4: trying to reuse the CRM revocation model for portal users

Portal users are not CRM users. Their auth lifecycle is intentionally separate.

## Source Files To Study

| File | Why it matters |
| --- | --- |
| [UserSecurityCacheService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java) | cached security metadata |
| [UserSecurityInfo.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityInfo.java) | minimal cached record |
| [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java) | request-time revocation check |
| [AdminUserService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/service/AdminUserService.java) | explicit eviction flows |
| [UserManagementService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementService.java) | membership change revocation |
| [SocieteService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java) | société-wide revocation paths |

## Exercises

### Exercise 1 — Trace one revocation path

1. Open [AdminUserService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/service/AdminUserService.java).
2. Follow `changeRole(...)`.
3. Confirm it increments `tokenVersion`.
4. Confirm it evicts the cache entry.
5. Confirm the filter would reject the old token on the next request.

### Exercise 2 — Compare with a TTL-bounded path

1. Open [SocieteService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java).
2. Find one path that increments `tokenVersion`.
3. Check whether it also calls `userSecurityCacheService.evict(...)`.
4. Explain the practical propagation behavior for existing sessions.

### Exercise 3 — Explain why portal is exempt

1. Open [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java).
2. Find the `isPortalToken(...)` branch.
3. Explain why a portal `contactId` cannot be validated through `UserSecurityCacheService`.
