package com.yem.hlm.backend.audit.domain;

/**
 * Commercial audit event types recorded for key deposit and contract lifecycle transitions.
 */
public enum AuditEventType {
    DEPOSIT_CREATED,
    DEPOSIT_CONFIRMED,
    DEPOSIT_CANCELED,
    DEPOSIT_EXPIRED,
    CONTRACT_CREATED,
    CONTRACT_SIGNED,
    CONTRACT_CANCELED,

    // Payment module events
    PAYMENT_CALL_ISSUED,
    PAYMENT_RECEIVED,

    // Reservation module events
    RESERVATION_CREATED,
    RESERVATION_CANCELLED,
    RESERVATION_EXPIRED,
    RESERVATION_CONVERTED_TO_DEPOSIT,

    // GDPR / Law 09-08 events
    CONTACT_ANONYMIZED,
    CONSENT_CHANGED,

    // Contact lifecycle events
    CONTACT_CREATED,
    CONTACT_STATUS_CHANGED,

    // User management events
    USER_INVITED,
    USER_ACTIVATED,
    USER_ROLE_CHANGED,
    USER_REMOVED,
    USER_UPDATED,
    USER_UNBLOCKED,
    USER_ANONYMIZED
}
