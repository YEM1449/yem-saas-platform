package com.yem.hlm.backend.common.error;

/**
 * Standard error codes for API responses.
 * Provides stable, frontend-friendly error identification.
 */
public enum ErrorCode {
    // Validation errors (400)
    VALIDATION_ERROR,

    // Authentication errors (401)
    UNAUTHORIZED,

    // Authorization errors (403)
    FORBIDDEN,

    // Not found errors (404)
    NOT_FOUND,

    // Conflict errors (409)
    TENANT_KEY_EXISTS,
    CONTACT_EMAIL_EXISTS,
    USER_EMAIL_EXISTS,
    CONTACT_INTEREST_EXISTS,
    DEPOSIT_ALREADY_EXISTS,
    PROPERTY_ALREADY_RESERVED,
    INVALID_DEPOSIT_STATE,
    INVALID_STATUS_TRANSITION,

    // Bad request errors (400)
    INVALID_CLIENT_CONVERSION,
    INVALID_DEPOSIT_REQUEST,

    // Property errors
    INVALID_PROPERTY_TYPE,
    PROPERTY_REFERENCE_CODE_EXISTS,
    INVALID_PERIOD,

    // Project errors (409)
    PROJECT_NAME_EXISTS,

    // Project assignment errors (400)
    ARCHIVED_PROJECT,

    // Contract errors
    PROPERTY_ALREADY_SOLD,      // 409 — property already has an active SIGNED contract
    INVALID_CONTRACT_STATE,     // 409 — action not permitted in current contract state
    CONTRACT_DEPOSIT_MISMATCH,  // 400 — sourceDepositId doesn't match contract details or not CONFIRMED

    // Outbox / messaging errors (400)
    INVALID_RECIPIENT,          // missing or malformed recipient address / phone
    CONTACT_CHANNEL_MISSING,    // contact found but required channel field is blank

<<<<<<< HEAD
    // Payment schedule errors
    PAYMENT_SCHEDULE_ITEM_NOT_FOUND,   // 404
    INVALID_PAYMENT_SCHEDULE_STATE,    // 409 — action not allowed in current item state
    PAYMENT_INVALID_AMOUNT,            // 400 — amount <= 0
=======
    // Payment module errors
    PAYMENT_SCHEDULE_EXISTS,    // 409 — a payment schedule already exists for this contract
    INVALID_TRANCHE_SUM,        // 400 — tranche percentages or amounts do not sum correctly
    TRANCHE_NOT_FOUND,          // 404 — tranche not found in tenant
    PAYMENT_CALL_NOT_FOUND,     // 404 — payment call not found in tenant
    INVALID_CALL_STATE,         // 409 — action not permitted in current call status
    PAYMENT_EXCEEDS_DUE,        // 400 — payment amount would exceed the amount due
    CALL_ALREADY_CLOSED,        // 409 — payment call is already CLOSED
>>>>>>> 9e440eb (Revenue-Foundation)

    // Server errors (500)
    INTERNAL_ERROR
}
