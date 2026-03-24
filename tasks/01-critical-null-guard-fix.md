# Task 01 — Fix Null societeId Bypass in Controllers

## Priority: 🔴 CRITICAL
## Risk: Data leakage across sociétés
## Effort: 30 minutes

## Problem

Three controllers call `SocieteContext.getSocieteId()` directly **without null-checking**. If a SUPER_ADMIN token (which has no `sid` claim) reaches these endpoints, `societeId` will be `null`, causing repository queries to either fail silently or return unscoped data.

Every other service in the codebase uses a `requireSocieteId()` guard — these controllers are the outliers.

## Files to Modify

### 1. `hlm-backend/src/main/java/com/yem/hlm/backend/commission/api/CommissionController.java`

**Current (BROKEN):** Lines reference `SocieteContext.getSocieteId()` directly.

**Fix:** Add a private `requireSocieteId()` method and replace all 6 occurrences:

```java
// ADD this method to the class:
private UUID requireSocieteId() {
    UUID societeId = SocieteContext.getSocieteId();
    if (societeId == null) {
        throw new com.yem.hlm.backend.contact.service.CrossTenantAccessException("Missing société context");
    }
    return societeId;
}
```

Then replace every occurrence of `SocieteContext.getSocieteId()` in the controller methods with `requireSocieteId()`:
- `myCommissions()` method
- `commissions()` method
- `listRules()` method
- `createRule()` method
- `updateRule()` method
- `deleteRule()` method

### 2. `hlm-backend/src/main/java/com/yem/hlm/backend/dashboard/api/CommercialDashboardController.java`

**Same fix:** Add `requireSocieteId()` method and replace all `SocieteContext.getSocieteId()` calls.

### 3. `hlm-backend/src/main/java/com/yem/hlm/backend/dashboard/api/ReceivablesDashboardController.java`

**Same fix:** Add `requireSocieteId()` method and replace all `SocieteContext.getSocieteId()` calls.

## Import Required

Each controller file needs:
```java
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
```

(This import may already exist; check before adding.)

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=CommissionIT
cd hlm-backend && ./mvnw test -Dtest=CommercialDashboardIT
cd hlm-backend && ./mvnw test -Dtest=ReceivablesDashboardIT
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT
```

## Acceptance Criteria

- [ ] No direct `SocieteContext.getSocieteId()` calls remain in any controller class (only in `requireSocieteId()` helpers)
- [ ] All existing tests pass
- [ ] A SUPER_ADMIN token without société scope would get a 403/400 instead of null societeId
