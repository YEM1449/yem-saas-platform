package com.yem.hlm.backend.reminder;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron-driven scheduler that delegates to {@link ReminderService}.
 *
 * <p>Disabled in the test profile via {@code spring.task.scheduling.enabled=false}.
 * Configurable cron expression: {@code app.reminder.cron} (default 08:00 daily).
 */
@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;
    private final SocieteContextHelper societeContextHelper;

    public ReminderScheduler(ReminderService reminderService, SocieteContextHelper societeContextHelper) {
        this.reminderService = reminderService;
        this.societeContextHelper = societeContextHelper;
    }

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
}
