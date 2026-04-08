# Module 05 — Filter Chain

## Learning Objectives

- Trace a request from ingress to controller through the platform’s custom filters
- Understand the difference between correlation, authentication, and authorization
- Explain how bearer tokens and cookies are resolved in one security chain
- Identify the cleanup points that prevent context leakage across reused threads

## Big Picture

Every request passes through two important custom layers before controller code matters:

1. request correlation
2. JWT authentication

Then Spring Security applies authorization rules.

This means the rough lifecycle is:

```text
HTTP request
  -> RequestCorrelationFilter
  -> Spring Security chain
     -> JwtAuthenticationFilter
     -> authorization rules
  -> controller
  -> response
```

## Step 1 — RequestCorrelationFilter

Code:

- [RequestCorrelationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/common/filter/RequestCorrelationFilter.java)

What it does:

1. reads `X-Request-Id`
2. generates one if absent
3. stores it in MDC as `requestId`
4. echoes it back on the response
5. clears MDC keys in `finally`

Why this matters:

- every log line for a request can be grouped
- callers can report a request ID when debugging production failures
- auth failures still get a request ID because this filter runs very early

Important correction for older notes:

- the live header is `X-Request-Id`
- not `X-Correlation-ID`

## Step 2 — JwtAuthenticationFilter

Code:

- [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java)

What it does:

1. resolve a token
2. validate signature and expiry
3. reject partial tokens outside the switch flow
4. derive authorities
5. apply CRM or portal-specific logic
6. populate `SocieteContext`
7. populate `SecurityContextHolder`
8. clear `SocieteContext` in `finally`

This filter is where the platform bridges from raw JWT claims into Spring Security and société-scoped application context.

## Token Resolution Order

This detail matters because the platform supports multiple clients and auth transports.

Resolution order inside `JwtAuthenticationFilter`:

1. `Authorization: Bearer <token>`
2. fallback cookie resolution

Cookie resolution is route-aware:

- `/api/portal/**` uses `PortalCookieHelper`
- other app routes use the CRM cookie helper

Why this is good:

- browser portal sessions can be cookie-based
- API clients can still use bearer tokens
- partial tokens remain header-driven

## CRM Branch Vs Portal Branch

### CRM branch

If the token is a CRM token:

- `sub` is treated as `app_user.id`
- `tv` is checked through `UserSecurityCacheService`
- `sid` becomes the active société scope
- impersonation metadata is applied when present

### Portal branch

If `ROLE_PORTAL` is present:

- `sub` is treated as `contact.id`
- no `UserSecurityCacheService` lookup is performed
- `sid` still becomes société scope
- `SocieteContext.userId` is set to the buyer contact ID

This split is essential because buyer portal principals are not CRM users.

## Partial Tokens In The Filter

Partial tokens are intentionally narrow.

If a request carries a partial token and is not going to `POST /auth/switch-societe`, the filter does not authenticate it as a normal user session.

That protects the system from accidentally treating “authenticated but not yet scoped” users as fully active CRM principals.

## Step 3 — Authorization Rules

Once authentication state is set, Spring Security evaluates URL rules from:

- [SecurityConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java)

Important public routes:

- `POST /auth/login`
- `POST /auth/logout`
- `POST /auth/switch-societe`
- `/auth/invitation/**`
- `GET /actuator/health`
- `POST /api/portal/auth/request-link`
- `GET /api/portal/auth/verify`
- `POST /api/portal/auth/logout`

Important protected route families:

- `/api/admin/**` -> `SUPER_ADMIN`
- `/api/portal/**` -> `PORTAL`
- `/api/**` -> CRM roles

Then controller-level `@PreAuthorize` may tighten access further.

## Security Headers In The Chain

`SecurityConfig` also attaches browser-security headers:

- Content-Security-Policy
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- referrer policy
- permissions policy
- optional HSTS when TLS is enabled

These are not “filters” in the same mental sense as auth, but they are part of the same request/response security pipeline and matter for production behavior.

## Cleanup Behavior

Two cleanups are especially important:

### MDC cleanup

`RequestCorrelationFilter` removes:

- `requestId`
- `societeId`

### Société context cleanup

`JwtAuthenticationFilter` clears `SocieteContext`

Without these `finally` blocks, a pooled servlet thread could leak one request’s identity or société scope into the next request. That would be catastrophic in a shared SaaS runtime.

## Mental Model For Debugging

When a request fails, ask these questions in order:

1. Did it enter with a request ID?
2. Did the filter resolve a token from header or cookie?
3. Was the JWT structurally valid?
4. Was it treated as CRM, partial, portal, or SUPER_ADMIN?
5. Did URL-level authorization reject it?
6. Did method-level `@PreAuthorize` reject it?
7. Did service-level business rules reject it?

That order prevents a lot of wasted debugging effort.

## Common Mistakes

### Mistake 1: assuming no bearer token means unauthenticated

Cookie-based portal sessions prove that is not always true.

### Mistake 2: using the wrong request ID header name

Use `X-Request-Id`, not `X-Correlation-ID`.

### Mistake 3: forgetting filter cleanup

If you add request-scoped context and do not clear it in `finally`, you risk cross-request leakage.

### Mistake 4: thinking URL rules are evaluated before auth

Authentication happens first, then authorization rules decide what the authenticated principal may access.

## Source Files To Study

| File | Why it matters |
| --- | --- |
| [RequestCorrelationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/common/filter/RequestCorrelationFilter.java) | request ID lifecycle |
| [JwtAuthenticationFilter.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java) | token resolution and auth context |
| [SecurityConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java) | filter insertion and authorization rules |
| [PortalCookieHelper.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/PortalCookieHelper.java) | portal cookie transport |

## Exercises

### Exercise 1 — Trace one unauthenticated request

1. Call `GET /actuator/health`.
2. Confirm it succeeds without JWT auth.
3. Confirm the response still includes `X-Request-Id`.

### Exercise 2 — Trace one portal request

1. Open `JwtAuthenticationFilter`.
2. Follow the cookie-resolution branch for `/api/portal/**`.
3. Explain why the portal branch skips `UserSecurityCacheService`.

### Exercise 3 — Explain cleanup guarantees

1. Find both `finally` blocks in the correlation and JWT filters.
2. Describe what would break if either one were removed.
