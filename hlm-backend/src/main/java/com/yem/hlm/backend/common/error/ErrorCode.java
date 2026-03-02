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

    // Media errors
    MEDIA_TOO_LARGE,            // 400 — file exceeds max allowed size
    MEDIA_TYPE_NOT_ALLOWED,     // 400 — content-type not in allowed list
    MEDIA_NOT_FOUND,            // 404 — media record not found in tenant

    // CSV import errors (400)
    IMPORT_VALIDATION_ERROR,

    // Payment schedule / tranche / call errors
    PAYMENT_SCHEDULE_EXISTS,        // 409 — schedule already exists for this contract
    INVALID_CALL_STATE,             // 409 — action not permitted in current call state
    INVALID_TRANCHE_SUM,            // 400 — tranche percentages/amounts do not sum correctly
    PAYMENT_EXCEEDS_DUE,            // 400 — payment amount exceeds the amount due for the call
    INVALID_PAYMENT_SCHEDULE_STATE, // 409 — schedule item action not permitted in current state
    PAYMENT_INVALID_AMOUNT,         // 400 — payment amount is zero or negative

    // Server errors (500)
    INTERNAL_ERROR
}
