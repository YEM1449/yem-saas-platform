# Task 04 — Add Audit Logging for Société Switch

## Priority: 🟢 LOW
## Risk: Gap in security audit trail
## Effort: 20 minutes

## Problem

When a user switches société via `/auth/switch-societe`, no security audit event is recorded. This means the audit trail has a blind spot for lateral movement between sociétés.

## Files to Modify

### 1. `hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/AuthService.java`

In the `switchSociete()` method, add audit logging after minting the new JWT (before the return statement):

```java
// After: String token = jwtProvider.generate(userId, societeId, role, user.getTokenVersion());
// Add:
String ip = extractClientIp();
securityAuditLogger.logSuccessfulLogin(user.getEmail(), userId, ip, role + " [SWITCH→" + societeId + "]");
log.info("Société switch: userId={} → societeId={} role={}", userId, societeId, role);
```

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=AuthLoginIT
cd hlm-backend && ./mvnw test -Dtest=AuthControllerTest
```

## Acceptance Criteria

- [ ] `switchSociete()` logs a security audit event with the target société ID
- [ ] Log message includes userId, target societeId, and assigned role
- [ ] All auth tests still pass
