# Task 11 — Fix Null societeId in UserManagementController

## Priority: 🔴 CRITICAL
## Risk: Same vulnerability as Task 01 — SUPER_ADMIN token leaks data
## Effort: 20 minutes
## Note: This is the same bug pattern from Task 01, now in the new usermanagement module.

## Problem

`UserManagementController.java` has **18 occurrences** of bare `SocieteContext.getSocieteId()` and `SocieteContext.getUserId()` without null-checks. A SUPER_ADMIN token without société scope would pass `null` to all service methods.

## File to Modify

### `hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementController.java`

Add private guards (or use `SocieteContextHelper` if Task 02 is done first):

```java
private UUID requireSocieteId() {
    UUID societeId = SocieteContext.getSocieteId();
    if (societeId == null) {
        throw new com.yem.hlm.backend.contact.service.CrossSocieteAccessException("Missing société context");
    }
    return societeId;
}

private UUID requireUserId() {
    UUID userId = SocieteContext.getUserId();
    if (userId == null) {
        throw new com.yem.hlm.backend.contact.service.CrossSocieteAccessException("Missing user context");
    }
    return userId;
}
```

Replace all 18 occurrences:
- `SocieteContext.getSocieteId()` → `requireSocieteId()`
- `SocieteContext.getUserId()` → `requireUserId()`

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=UserManagementIsolationIT
cd hlm-backend && ./mvnw test -Dtest=UserManagementServiceTest
```

## Acceptance Criteria

- [ ] No bare `SocieteContext.getSocieteId()` calls in the controller
- [ ] No bare `SocieteContext.getUserId()` calls in the controller
- [ ] All existing tests pass
