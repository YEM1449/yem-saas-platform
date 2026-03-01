package com.yem.hlm.backend.payments.domain;

/**
 * Lifecycle state of a {@link PaymentScheduleItem}.
 *
 * <p>Transitions:
 * <pre>
 *   DRAFT → ISSUED → SENT → PAID
 *                 ↘ OVERDUE (set by daily scheduler when due_date < today and remaining > 0)
 *   DRAFT|ISSUED|SENT|OVERDUE → CANCELED
 * </pre>
 *
 * <p>{@code PAID} is terminal — a fully-paid item cannot be canceled.
 */
public enum PaymentScheduleStatus {
    DRAFT,
    ISSUED,
    SENT,
    OVERDUE,
    PAID,
    CANCELED
}
