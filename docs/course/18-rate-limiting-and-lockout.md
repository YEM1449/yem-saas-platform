# Module 18: Rate Limiting And Lockout

## Why This Matters

Authentication and public entry points are prime abuse targets.

## Learning Goals

- understand how login abuse is reduced
- understand portal magic-link protections
- understand the relationship between rate limiting and user experience

## Current Controls

### Staff login

- per-IP throttling
- per-identity throttling
- account lockout after repeated failures
- timing-attack mitigation for unknown users

### Portal link requests

- public endpoint throttling
- generic success response to avoid user enumeration

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/LoginRateLimiter.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/LoginRateLimiter.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/common/ratelimit/RateLimiterService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/common/ratelimit/RateLimiterService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java)

## Exercise

Describe why a public portal link endpoint must avoid revealing whether an email address exists in the system.
