# Module 03: Token Revocation And Membership Switching

## Why This Matters

JWTs are stateless by default, but this platform needs near-immediate revocation when security-sensitive changes occur.

## Learning Goals

- understand `token_version`
- understand how revocation stays fast
- understand why membership switching is a separate flow

## Implementation Summary

- `app_user.token_version` is embedded in staff JWTs as `tv`
- `UserSecurityCacheService` caches enabled state and token version
- changing password, role, enablement, or related security state invalidates prior sessions

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/CacheConfig.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/CacheConfig.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java)

## Why Membership Switching Is Separate

- a user can belong to several societes
- the system cannot guess the correct workspace
- the partial token limits what is possible before the final choice is made

## Exercise

List three events that should invalidate an existing staff token and explain why.
