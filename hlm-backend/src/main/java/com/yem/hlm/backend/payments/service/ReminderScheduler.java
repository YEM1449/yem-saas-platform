package com.yem.hlm.backend.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link ReminderService#processAll()} on a daily cron schedule.
 *
 * <p>Disabled in test profile via {@code spring.task.scheduling.enabled=false}
 * (same pattern as {@code OutboundDispatcherScheduler}).
 */
@Component
@ConditionalOnProperty(value = "spring.task.scheduling.enabled", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * Runs daily at the time configured by {@code app.payments.reminder-cron}.
     * Default: 07:00 UTC every day.
     */
    @Scheduled(cron = "${app.payments.reminder-cron:0 0 7 * * *}")
    public void run() {
        log.info("ReminderScheduler: starting daily reminder run");
        try {
            reminderService.processAll();
        } catch (Exception ex) {
            log.error("ReminderScheduler: unexpected error during reminder run", ex);
        }
        log.info("ReminderScheduler: daily reminder run complete");
    }
}
