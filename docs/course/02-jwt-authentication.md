# Module 02 — JWT Authentication

**Audience**: junior to senior engineers  
**Updated**: 2026-04-08

Reference material:

- [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)
- [../02-architecture/README.md](../02-architecture/README.md)

## Learning Objectives

- Explain the four authentication token/session modes used by the platform
- Read the important JWT claims and know which flows emit them
- Trace a login request through `AuthService`, `JwtProvider`, and `JwtAuthenticationFilter`
- Understand how portal authentication differs from CRM authentication

## Why JWTs Are Used Here

The platform is API-first and stateless at the HTTP layer. A signed token lets the backend validate identity and authorization claims without storing server-side web sessions for each CRM user.

This works especially well here because the platform already needs:

- role claims for Spring Security
- a société scope claim (`sid`)
- partial tokens for multi-société selection
- platform-level `SUPER_ADMIN` tokens with no société scope
- a separate buyer-portal auth path

## The Four Token / Session Shapes

| Mode | Principal | Transport | Main use |
| --- | --- | --- | --- |
| CRM JWT | `app_user.id` | `Authorization` header and `hlm_access_token` in frontend storage | normal CRM use |
| Partial JWT | `app_user.id` | bearer token only | temporary token for `switch-societe` |
| SUPER_ADMIN JWT | `app_user.id` | bearer token | platform administration without a société scope |
| Portal JWT | `contact.id` | `hlm_portal_auth` httpOnly cookie | buyer portal session |

The important design point is that all four are JWTs, but they are not interchangeable.

## JWT Claims Reference

### CRM token

| Claim | Meaning |
| --- | --- |
| `sub` | current CRM user UUID |
| `roles` | JWT authorities such as `["ROLE_ADMIN"]` |
| `tv` | token version for revocation |
| `sid` | active société UUID |
| `imp` | optional impersonating SUPER_ADMIN UUID |

### Partial token

| Claim | Meaning |
| --- | --- |
| `sub` | CRM user UUID |
| `roles` | one CRM role |
| `tv` | token version |
| `partial` | `true` |
| `exp` | short TTL, used only for société selection |

### SUPER_ADMIN token

| Claim | Meaning |
| --- | --- |
| `sub` | platform operator UUID |
| `roles` | `["ROLE_SUPER_ADMIN"]` |
| `sid` | absent |
| `tv` | present |

### Portal token

| Claim | Meaning |
| --- | --- |
| `sub` | buyer contact UUID |
| `roles` | `["ROLE_PORTAL"]` |
| `sid` | buyer’s société UUID |
| `tv` | absent |

Portal tokens are intentionally different because buyers are not `app_user` rows.

## Where Tokens Are Created

### CRM and SUPER_ADMIN tokens

Primary code path:

- [AuthService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java)
- [JwtProvider.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/JwtProvider.java)

High-level flow:

1. `POST /auth/login` checks credentials.
2. The backend resolves active société memberships.
3. If exactly one active société exists, a full CRM JWT is minted.
4. If multiple memberships exist, a partial token is minted instead.
5. If the user is a platform-only `SUPER_ADMIN`, a platform token is minted with no `sid`.

### Portal token

Primary code path:

- [PortalAuthController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java)
- [PortalAuthService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java)
- [PortalJwtProvider.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalJwtProvider.java)
- [PortalCookieHelper.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/PortalCookieHelper.java)

High-level flow:

1. Buyer requests a magic link.
2. Backend verifies and consumes the one-time token.
3. Backend generates a portal JWT.
4. Backend sets the `hlm_portal_auth` httpOnly cookie on `/api/portal`.
5. The frontend keeps only an in-memory “authenticated” flag for the current SPA runtime.

## How The Frontend Transports Auth

### CRM frontend

- token is stored as `hlm_access_token`
- CRM interceptor adds `Authorization: Bearer <token>` to `/auth/**` and `/api/**` requests

### Portal frontend

- no portal JWT is stored in `localStorage`
- `PortalAuthService.verifyToken()` calls `GET /api/portal/auth/verify?token=...`
- backend sets the cookie
- `portalInterceptor` sends `withCredentials: true` for `/api/portal/**`
- `portalGuard` validates the session through the backend when needed

This is an intentional security upgrade: browser JavaScript never needs the raw buyer portal JWT.

## Login Branches

### Standard CRM login

```text
POST /auth/login
  -> validate password
  -> resolve memberships
  -> one active société
  -> issue CRM JWT with sid + tv
```

### Multi-société login

```text
POST /auth/login
  -> validate password
  -> resolve memberships
  -> multiple active sociétés
  -> issue partial JWT
  -> client chooses société
POST /auth/switch-societe
  -> issue full CRM JWT with chosen sid
```

### Portal login

```text
POST /api/portal/auth/request-link
  -> send magic link
GET /api/portal/auth/verify?token=...
  -> verify token
  -> mint portal JWT
  -> set hlm_portal_auth cookie
```

## How Tokens Are Resolved On Requests

The runtime request path is anchored in [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java).

Resolution order:

1. Try the `Authorization` header first.
2. If absent, fall back to the auth cookie.
3. Use the CRM cookie helper for normal app routes.
4. Use `PortalCookieHelper` for `/api/portal/**`.

That order is important because:

- API clients still use bearer tokens
- partial tokens are bearer-only
- portal browser sessions use cookies

## What Happens After Decoding

Once the JWT is decoded and validated:

1. roles are converted into `GrantedAuthority`
2. the filter distinguishes portal tokens from CRM tokens
3. `SocieteContext` is populated
4. `SecurityContextHolder` receives a `UsernamePasswordAuthenticationToken`

The two main branches are:

### Portal branch

- `sub` is treated as `contactId`
- `ROLE_PORTAL` is detected
- no `UserSecurityCacheService` lookup happens
- `SocieteContext.userId` is set to the contact UUID

### CRM branch

- `sub` is treated as `app_user.id`
- `tv` is checked against `UserSecurityCacheService`
- `sid` is used to set société scope
- impersonation is applied if the `imp` claim is present

## Partial Tokens

Partial tokens are a safety mechanism, not a convenience feature.

They exist so the backend can:

- authenticate the user once
- prove identity during the société-selection step
- avoid issuing a full-scoped token before the active société is chosen

They are accepted only on `POST /auth/switch-societe`.

If a partial token is presented to a normal CRM endpoint, the filter deliberately skips authentication and lets Spring Security reject the request.

## Operational Notes

### TTLs

- CRM token TTL comes from `JWT_TTL_SECONDS`
- partial tokens are intentionally short-lived
- portal token TTL is fixed in `PortalJwtProvider`

### Why `sid` matters

The token does not just identify the caller. It also identifies the active société. That claim is what allows downstream code to populate `SocieteContext` and align application-level access with row-level data isolation.

### Why portal omits `tv`

Portal sessions are invalidated by:

- one-time magic-link consumption
- cookie expiration
- portal token TTL

They are not checked against `app_user.token_version` because buyers are contacts, not CRM users.

## Common Mistakes

### Mistake 1: treating portal auth like CRM auth

Wrong mental model:

- portal token in local storage
- portal interceptor adds bearer header

Current implementation:

- backend sets `hlm_portal_auth`
- portal frontend sends credentials, not bearer tokens

### Mistake 2: using `hasRole('ROLE_ADMIN')`

Spring Security adds `ROLE_` for `hasRole(...)`. Use `hasRole('ADMIN')`, not `hasRole('ROLE_ADMIN')`.

### Mistake 3: assuming all valid JWTs have `sid`

`SUPER_ADMIN` tokens intentionally omit it. Any code path that blindly requires a société without checking the auth surface will break platform flows.

### Mistake 4: assuming portal verify returns a usable access token payload

The current controller returns an empty `accessToken` field after setting the cookie. Client code should not depend on reading the raw token from the response body.

## Source Files To Study

| File | Why it matters |
| --- | --- |
| [AuthService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java) | login branching and partial-token issuance |
| [JwtProvider.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/JwtProvider.java) | CRM and SUPER_ADMIN token generation |
| [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java) | request-time token resolution and context setup |
| [PortalAuthController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java) | cookie-based portal verify/logout |
| [PortalJwtProvider.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalJwtProvider.java) | buyer portal token claims |
| [portal-auth.service.ts](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend/src/app/portal/core/portal-auth.service.ts) | frontend portal session behavior |
| [portal.interceptor.ts](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend/src/app/portal/core/portal.interceptor.ts) | credential-forwarding for portal APIs |

## Exercises

### Exercise 1 — Inspect one CRM token

1. Log in as `admin@acme.com`.
2. Copy the CRM token.
3. Decode the payload.
4. Identify `sub`, `roles`, `tv`, and `sid`.
5. Confirm the `sid` matches the active société.

### Exercise 2 — Follow the multi-société path

1. Create a user with two active memberships.
2. Call `POST /auth/login`.
3. Confirm the response requires société selection.
4. Call `POST /auth/switch-societe`.
5. Verify the returned token is no longer marked `partial`.

### Exercise 3 — Follow the portal path

1. Open [PortalAuthController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java).
2. Confirm verify sets a cookie and returns an empty access token field.
3. Open [portal-auth.service.ts](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend/src/app/portal/core/portal-auth.service.ts).
4. Confirm the frontend marks the session authenticated without storing a raw portal JWT in browser storage.
