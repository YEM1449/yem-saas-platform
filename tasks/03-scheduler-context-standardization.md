# Task 03 — Standardize Scheduler SocieteContext Handling

## Priority: 🟠 MEDIUM
## Risk: Inconsistent société context in background jobs
## Effort: 1 hour
## Depends on: Task 02 (uses SocieteContextHelper)

## Problem

Four schedulers handle SocieteContext differently:
- `DataRetentionScheduler` — correctly uses `SocieteContext.setSystem()` + `clear()` ✅
- `ReminderScheduler` — no context management (delegates to ReminderService which queries globally) ⚠️
- `DepositWorkflowScheduler` — no context management ⚠️
- `ReservationExpiryScheduler` — no context management ⚠️

While these work because they pass `societeId` directly to queries, the pattern is inconsistent and fragile.

## Files to Modify

### 1. `hlm-backend/src/main/java/com/yem/hlm/backend/reminder/ReminderScheduler.java`

Inject `SocieteContextHelper` and wrap the scheduled method:

```java
private final SocieteContextHelper societeContextHelper;

// Add to constructor parameters

@Scheduled(cron = "${app.reminder.cron:0 0 8 * * *}")
public void runDailyReminders() {
    societeContextHelper.runAsSystem(() -> {
        log.info("[REMINDER-SCHEDULER] Starting daily reminder run");
        try {
            reminderService.runDepositDueReminders();
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Deposit reminders failed: {}", e.getMessage(), e);
        }
        try {
            reminderService.runProspectFollowUp();
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Prospect follow-up failed: {}", e.getMessage(), e);
        }
        log.info("[REMINDER-SCHEDULER] Daily reminder run complete");
    });
}
```

### 2. `hlm-backend/src/main/java/com/yem/hlm/backend/deposit/scheduler/DepositWorkflowScheduler.java`

Same pattern:

```java
private final SocieteContextHelper societeContextHelper;

// Add to constructor parameters

@Scheduled(cron = "0 0 * * * *")
public void hourly() {
    societeContextHelper.runAsSystem(() -> {
        depositService.runHourlyWorkflow(Duration.ofHours(24));
    });
}
```

### 3. `hlm-backend/src/main/java/com/yem/hlm/backend/reservation/service/ReservationExpiryScheduler.java`

Same pattern:

```java
private final SocieteContextHelper societeContextHelper;

// Add to constructor parameters

@Scheduled(cron = "${app.reservation.expiry-cron:0 0 * * * *}")
public void runExpiryCheck() {
    societeContextHelper.runAsSystem(() -> {
        log.info("Reservation expiry check starting");
        try {
            reservationService.runExpiryCheck();
        } catch (Exception e) {
            log.error("Reservation expiry check failed", e);
        }
    });
}
```

### 4. `hlm-backend/src/main/java/com/yem/hlm/backend/gdpr/scheduler/DataRetentionScheduler.java`

Refactor to use `SocieteContextHelper.runAsSystem()` instead of manual `SocieteContext.setSystem()` / `clear()`:

Replace the manual try/finally pattern with `societeContextHelper.runAsSystem(...)`.

### 5. `hlm-backend/src/main/java/com/yem/hlm/backend/outbox/scheduler/OutboundDispatcherScheduler.java`

Check if this scheduler also needs the pattern. If it has a `@Scheduled` method, wrap it.

### 6. `hlm-backend/src/main/java/com/yem/hlm/backend/payments/service/ReminderScheduler.java`

Check if this scheduler exists and needs the pattern.

### 7. `hlm-backend/src/main/java/com/yem/hlm/backend/portal/scheduler/PortalTokenCleanupScheduler.java`

Check and wrap if needed.

## Required Import

```java
import com.yem.hlm.backend.societe.SocieteContextHelper;
```

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=DataRetentionSchedulerTest
cd hlm-backend && ./mvnw test -Dtest=ReminderServiceTest
cd hlm-backend && ./mvnw test -Dtest=PortalTokenCleanupSchedulerTest
cd hlm-backend && ./mvnw test
```

## Acceptance Criteria

- [ ] All `@Scheduled` methods use `societeContextHelper.runAsSystem()` wrapper
- [ ] No manual `SocieteContext.setSystem()` / `SocieteContext.clear()` calls remain in schedulers
- [ ] All existing tests pass
- [ ] `rg "SocieteContext.setSystem" hlm-backend/src/main/java` returns only `SocieteContextHelper.java` and `SocieteContext.java`
