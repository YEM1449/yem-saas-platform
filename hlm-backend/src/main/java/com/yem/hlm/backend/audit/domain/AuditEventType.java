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
    CONTRACT_CANCELED
}
