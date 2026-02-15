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

    // Server errors (500)
    INTERNAL_ERROR
}
