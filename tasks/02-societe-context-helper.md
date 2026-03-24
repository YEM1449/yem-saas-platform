# Task 02 — Extract Shared SocieteContextHelper Component

## Priority: 🟠 MEDIUM
## Risk: Reduces code duplication, prevents future isolation bugs
## Effort: 1 hour
## Depends on: Task 01 completed

## Problem

Every service has its own copy of `requireSocieteId()` and `requireUserId()` private methods. This is error-prone — new services may forget the null-check. A shared Spring component centralizes this logic.

## Files to Create

### 1. NEW: `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContextHelper.java`

```java
package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.contact.service.CrossSocieteAccessException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Centralized helper for accessing the current société context.
 * Inject this instead of calling SocieteContext static methods directly.
 * Provides null-safe accessors that throw CrossSocieteAccessException
 * if the required context is missing.
 */
@Component
public class SocieteContextHelper {

    /**
     * Returns the current société ID or throws if missing.
     * Use this in every service/controller method that operates on société-scoped data.
     */
    public UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) {
            throw new CrossSocieteAccessException("Missing société context");
        }
        return societeId;
    }

    /**
     * Returns the current user ID or throws if missing.
     */
    public UUID requireUserId() {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) {
            throw new CrossSocieteAccessException("Missing user context");
        }
        return userId;
    }

    /**
     * Returns the current role from the société context.
     */
    public String getRole() {
        return SocieteContext.getRole();
    }

    /**
     * Returns true if the current context is a SUPER_ADMIN.
     */
    public boolean isSuperAdmin() {
        return SocieteContext.isSuperAdmin();
    }

    /**
     * Executes a task in system mode (no société scope, superAdmin=true).
     * Used by schedulers that operate across all sociétés.
     * Clears context in finally block to prevent ThreadLocal leaks.
     */
    public void runAsSystem(Runnable task) {
        try {
            SocieteContext.setSystem();
            task.run();
        } finally {
            SocieteContext.clear();
        }
    }
}
```

### 2. RENAME: `hlm-backend/src/main/java/com/yem/hlm/backend/contact/service/CrossTenantAccessException.java`

Before creating the helper, rename the exception class (or create an alias):

Create a new file `CrossSocieteAccessException.java` in the `societe` package:

```java
package com.yem.hlm.backend.societe;

/**
 * Thrown when a request attempts to access data outside its société scope,
 * or when the société context is missing entirely.
 */
public class CrossSocieteAccessException extends RuntimeException {
    public CrossSocieteAccessException(String message) {
        super(message);
    }
}
```

Note: Keep `CrossTenantAccessException` as-is for backward compatibility (Task 05 will rename it). The new `CrossSocieteAccessException` should be used by the helper. Add a handler in `GlobalExceptionHandler` for `CrossSocieteAccessException`:

```java
@ExceptionHandler(CrossSocieteAccessException.class)
public ResponseEntity<ErrorResponse> handleCrossSocieteAccess(CrossSocieteAccessException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(ErrorCode.CROSS_TENANT_ACCESS, ex.getMessage()));
}
```

## Files to Modify (Incremental Adoption)

Do NOT refactor all services at once. Instead, start using `SocieteContextHelper` in the three controllers fixed in Task 01:

- `CommissionController.java` — inject `SocieteContextHelper`, replace private `requireSocieteId()` with `helper.requireSocieteId()`
- `CommercialDashboardController.java` — same
- `ReceivablesDashboardController.java` — same

Other services can be migrated incrementally in future PRs.

## Tests to Add

### NEW: `hlm-backend/src/test/java/com/yem/hlm/backend/societe/SocieteContextHelperTest.java`

```java
package com.yem.hlm.backend.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SocieteContextHelperTest {

    private final SocieteContextHelper helper = new SocieteContextHelper();

    @AfterEach
    void cleanup() {
        SocieteContext.clear();
    }

    @Test
    void requireSocieteId_returnsId_whenSet() {
        UUID id = UUID.randomUUID();
        SocieteContext.setSocieteId(id);
        assertThat(helper.requireSocieteId()).isEqualTo(id);
    }

    @Test
    void requireSocieteId_throws_whenNull() {
        assertThatThrownBy(helper::requireSocieteId)
                .isInstanceOf(CrossSocieteAccessException.class)
                .hasMessageContaining("société");
    }

    @Test
    void requireUserId_throws_whenNull() {
        assertThatThrownBy(helper::requireUserId)
                .isInstanceOf(CrossSocieteAccessException.class);
    }

    @Test
    void runAsSystem_setsAndClearsContext() {
        SocieteContext.setSocieteId(UUID.randomUUID());
        helper.runAsSystem(() -> {
            assertThat(SocieteContext.isSuperAdmin()).isTrue();
            assertThat(SocieteContext.getSocieteId()).isNull();
        });
        // After runAsSystem, context should be cleared
        assertThat(SocieteContext.getSocieteId()).isNull();
    }
}
```

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=SocieteContextHelperTest
cd hlm-backend && ./mvnw test -Dtest=CommissionIT
cd hlm-backend && ./mvnw test -Dtest=CommercialDashboardIT
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT
```

## Acceptance Criteria

- [ ] `SocieteContextHelper` component exists in `societe` package
- [ ] `CrossSocieteAccessException` exists in `societe` package
- [ ] Exception is handled in `GlobalExceptionHandler` with 403 status
- [ ] Unit tests pass for the helper
- [ ] Three controllers from Task 01 now use the injected helper
- [ ] All existing tests still pass
