package com.yem.hlm.backend.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the automated reminder scheduler.
 *
 * <p>All properties are under the {@code app.reminder} prefix.
 */
@Component
@ConfigurationProperties(prefix = "app.reminder")
public class ReminderProperties {

    /** Whether the reminder scheduler is active (default true). */
    private boolean enabled = true;

    /**
     * Cron expression for the reminder scheduler.
     * Default: 08:00 every day.
     */
    private String cron = "0 0 8 * * *";

    /**
     * Days before a deposit due-date to send a warning email to the agent.
     * Default: 7, 3, 1.
     */
    private List<Integer> depositWarnDays = List.of(7, 3, 1);

    /**
     * Number of days without activity after which a PROSPECT/QUALIFIED_PROSPECT
     * contact is considered stale and a follow-up notification is created.
     * Default: 14.
     */
    private int prospectStaleDays = 14;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public List<Integer> getDepositWarnDays() { return depositWarnDays; }
    public void setDepositWarnDays(List<Integer> depositWarnDays) { this.depositWarnDays = depositWarnDays; }

    public int getProspectStaleDays() { return prospectStaleDays; }
    public void setProspectStaleDays(int prospectStaleDays) { this.prospectStaleDays = prospectStaleDays; }
}
