package com.yem.hlm.backend.notification.domain;

public enum NotificationType {
    DEPOSIT_CREATED,
    DEPOSIT_PENDING,
    DEPOSIT_DUE_SOON,
    DEPOSIT_CONFIRMED,
    DEPOSIT_CANCELLED,
    DEPOSIT_EXPIRED,
    PAYMENT_CALL_OVERDUE,
    PROSPECT_STALE,
    RESERVATION_EXPIRING_SOON,
    /** VEFA — une option (blocage temporaire) a expiré et le bien a été libéré. */
    OPTION_EXPIRED,
    /** VEFA — le délai légal de rétractation est clos sans rétractation (la vente continue). */
    RETRACTATION_DELAI_CLOS
}
