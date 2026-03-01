package com.yem.hlm.backend.payment.domain;

/**
 * Lifecycle status of a {@link PaymentTranche}.
 *
 * <pre>
 * PLANNED → ISSUED (when a PaymentCall is issued)
 * ISSUED  → PARTIALLY_PAID (when some payments are received, total < amount)
 * ISSUED / PARTIALLY_PAID → PAID (when total payments >= amount)
 * ISSUED / PARTIALLY_PAID → OVERDUE (set by scheduler when due_date passed)
 * </pre>
 */
public enum TrancheStatus {
    PLANNED,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE
}
