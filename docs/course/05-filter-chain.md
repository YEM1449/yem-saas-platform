# Module 05: Security Filter Chain And Request Lifecycle

## Why This Matters

Understanding the filter chain helps you debug auth, cookies, and authorization issues quickly.

## Learning Goals

- understand where authentication happens
- understand how cookies and bearer tokens are resolved
- understand how the request becomes an authenticated business call

## Flow To Study

1. request arrives
2. filter resolves bearer token or cookie
3. token is validated
4. authorities and societe context are established
5. request proceeds to controller
6. `SocieteContext` is cleared in `finally`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java)
- [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)

## Subtleties

- partial tokens are allowed only for the societe-switch endpoint
- portal tokens skip CRM user revocation checks
- platform-level `SUPER_ADMIN` tokens can be valid without `sid`

## Exercise

Write down the exact difference between how the filter treats a portal token and a CRM token.
