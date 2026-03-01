package com.yem.hlm.backend.payments.domain;

/**
 * Types of automatic and manual payment reminders.
 * Used as the idempotency key in {@link ScheduleItemReminder} to prevent duplicate sends.
 */
public enum ReminderType {
    /** Sent 7 days before due date. */
    PRE_DUE_7D,
    /** Sent 1 day before due date. */
    PRE_DUE_1D,
    /** Sent 1 day after due date (first overdue reminder). */
    OVERDUE_1D,
    /** Sent 7 days after due date. */
    OVERDUE_7D,
    /** Sent 30 days after due date. */
    OVERDUE_30D,
    /** Manual reminder triggered by a CRM user via API. */
    MANUAL
}
