# Module 02: JWT Authentication And Session Cookies

## Why This Matters

The platform uses browser-friendly cookie sessions while still relying on JWT semantics under the hood.

## Learning Goals

- understand staff and portal auth flows
- understand why final sessions use cookies
- understand the special multi-societe selection step

## Two Auth Flows

### Staff CRM

- login with email and password
- receive direct session or partial societe-selection response
- final session stored in `hlm_auth`

### Buyer portal

- request magic link
- verify token
- session stored in `hlm_portal_auth`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/api/AuthController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/api/AuthController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/CookieTokenHelper.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/CookieTokenHelper.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/PortalCookieHelper.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/PortalCookieHelper.java)

## Key Ideas

- the final token is intentionally hidden from JavaScript
- partial tokens exist only for societe switching
- portal and CRM cookies do not share the same path or trust model

## Exercise

Trace a staff login from `/auth/login` to the point where the cookie is written on the response.
