# Module 05 — Filter Chain

## Learning Objectives

- Describe the Spring Security filter chain order
- Trace a request through the filters from arrival to controller
- Explain what `RequestCorrelationFilter` and `JwtAuthenticationFilter` each do

---

## Filter Order

Two custom filters are inserted before `UsernamePasswordAuthenticationFilter`:

```
1. RequestCorrelationFilter    (runs first — sets X-Correlation-ID in MDC)
2. JwtAuthenticationFilter     (validates JWT, sets TenantContext + SecurityContext)
3. UsernamePasswordAuthenticationFilter  (Spring default, not used for API)
4. ... other Spring Security filters ...
5. Controller
```

---

## RequestCorrelationFilter

`common/filter/RequestCorrelationFilter.java`

1. Reads `X-Correlation-ID` header from the request.
2. If absent, generates a UUID v4.
3. Stores the ID in `MDC.put("correlationId", id)` — available in all log statements for the duration of the request.
4. Writes `X-Correlation-ID: {id}` to the response header — clients can use this to correlate request and response for debugging.
5. In the `finally` block: `MDC.remove("correlationId")`.

---

## JwtAuthenticationFilter

`auth/security/JwtAuthenticationFilter.java`

1. Reads `Authorization: Bearer <token>` header.
2. If absent → no JWT authentication set; Spring continues (will fail at URL rule check).
3. Calls `jwtProvider.isValid(token)`.
4. On valid: extracts `tid`, `sub`, `roles`, `tv` from claims.
5. Checks if `ROLE_PORTAL` is in roles:
   - **Portal token** → skip `UserSecurityCacheService`; set contactId as principal.
   - **CRM token** → load `UserSecurityInfo` from cache; check enabled and token version.
6. Sets `TenantContext.set(tenantId, userId)`.
7. Sets `SecurityContextHolder` with `JwtAuthenticationToken`.
8. Calls `chain.doFilter(request, response)`.
9. `finally`: `TenantContext.clear()`.

---

## URL Rule Evaluation

After the filter chain, Spring Security evaluates the URL pattern rules from `SecurityConfig`:

```
OPTIONS /**                       → permitAll (CORS preflight, skips JWT check)
POST /auth/login                  → permitAll
GET  /actuator/health             → permitAll
POST /api/portal/auth/request-link → permitAll
GET  /api/portal/auth/verify       → permitAll
/api/portal/**                    → hasRole('PORTAL')
/api/**                           → hasAnyRole('ADMIN','MANAGER','AGENT')
anyRequest                        → authenticated
```

Rules are evaluated in order. The first matching rule wins.

---

## Source Files

| File | Purpose |
|------|---------|
| `common/filter/RequestCorrelationFilter.java` | Correlation ID injection |
| `auth/security/JwtAuthenticationFilter.java` | JWT parsing and TenantContext setup |
| `auth/security/SecurityConfig.java` | Filter registration and URL rules |

---

## Exercise

1. Open `SecurityConfig.java` and find `securityFilterChain(HttpSecurity http)`.
2. Identify the two `http.addFilterBefore(...)` calls.
3. Verify the order: `RequestCorrelationFilter` is added before `JwtAuthenticationFilter` which is before `UsernamePasswordAuthenticationFilter`.
4. Make a request with `curl -v http://localhost:8080/actuator/health` (no token).
5. Observe the `X-Correlation-ID` header in the response — this is set by `RequestCorrelationFilter`.
