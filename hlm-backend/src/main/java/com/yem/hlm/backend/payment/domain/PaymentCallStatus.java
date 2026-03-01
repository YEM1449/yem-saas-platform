package com.yem.hlm.backend.payment.domain;

/**
 * Lifecycle status of a {@link PaymentCall} (Appel de Fonds).
 *
 * <pre>
 * DRAFT → ISSUED (explicit action)
 * ISSUED → OVERDUE (scheduler, when due_date < today)
 * ISSUED / OVERDUE → CLOSED (when fully paid via Payment records)
 * </pre>
 */
public enum PaymentCallStatus {
    DRAFT,
    ISSUED,
    OVERDUE,
    CLOSED
}
