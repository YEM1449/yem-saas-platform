package com.yem.hlm.backend.common.error;

/**
 * Standard error codes for API responses.
 * Provides stable, frontend-friendly error identification.
 */
public enum ErrorCode {
    // Validation errors (400)
    VALIDATION_ERROR,
    INVALID_REQUEST,

    // Authentication errors (401)
    UNAUTHORIZED,
    INVALID_OR_MISSING_TOKEN,   // 401 — JWT missing, malformed, or expired
    TOKEN_INVALIDATED,          // 401 — JWT token version invalidated (logout / membership removed)

    // Authorization errors (403)
    FORBIDDEN,

    // Not found errors (404)
    NOT_FOUND,
    USER_NOT_FOUND,             // 404 — user not found
    CONTACT_NOT_FOUND,          // 404 — contact not found
    PROPERTY_NOT_FOUND,         // 404 — property not found
    CONTRACT_NOT_FOUND,         // 404 — contract not found
    PROJECT_NOT_FOUND,          // 404 — project not found
    MEMBERSHIP_NOT_FOUND,       // 404 — société membership not found

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

    // Immeuble errors (409)
    IMMEUBLE_NAME_EXISTS,        // 409 — a building with this name already exists in the project

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

    // Commission errors (404)
    COMMISSION_RULE_NOT_FOUND,  // 404 — commission rule not found in tenant

    // Portal errors (401)
    PORTAL_TOKEN_INVALID,       // 401 — magic link token invalid, expired, or already used

    // Reservation errors
    RESERVATION_NOT_FOUND,              // 404 — reservation not found in tenant
    PROPERTY_NOT_AVAILABLE_FOR_RESERVATION, // 409 — property already reserved, deposited or sold
    INVALID_RESERVATION_STATE,          // 409 — action not permitted in current reservation state

    // Contract immutability (409)
    SIGNED_CONTRACT_IMMUTABLE,  // 409 — attempt to modify an already SIGNED contract

    // Concurrent update (409)
    CONCURRENT_UPDATE,          // 409 — optimistic lock failure; resource was modified concurrently

    // GDPR / Law 09-08 errors
    GDPR_ERASURE_BLOCKED,       // 409 — contact has SIGNED contracts; erasure not permitted
    GDPR_EXPORT_NOT_FOUND,      // 404 — contact not found for data export
    CONSENT_REQUIRED,           // 400 — consent or legal basis required (Loi 09-08 Art. 4 / RGPD Art. 6)

    // Rate limiting (429)
    RATE_LIMIT_EXCEEDED,        // 429 — too many requests for this operation
    LOGIN_RATE_LIMITED,         // 429 — too many login attempts from this IP or identity

    // Account lockout (401)
    ACCOUNT_LOCKED,             // 401 — account temporarily locked after too many failed attempts

    // Multi-société errors
    NO_SOCIETE_ACCESS,          // 401 — user has no active société membership
    SOCIETE_NOT_IN_CLAIMS,      // 403 — requested société not accessible by this user
    SOCIETE_INACTIVE,           // 403 — société membership is inactive
    SUPER_ADMIN_REQUIRED,       // 403 — operation requires SUPER_ADMIN role
    SOCIETE_NOT_FOUND,          // 404 — société not found
    SOCIETE_ALREADY_EXISTS,     // 409 — a société with this nom/key already exists
    SOCIETE_SUSPENDED,          // 403 — société is suspended; operation rejected
    USER_ALREADY_IN_SOCIETE,    // 409 — user already has a membership in this société
    QUOTA_BIENS_ATTEINT,        // 409 — société has reached its property quota
    QUOTA_CONTACTS_ATTEINT,     // 409 — société has reached its contact quota
    QUOTA_PROJETS_ATTEINT,      // 409 — société has reached its project quota

    // RBAC enforcement errors
    ROLE_ESCALATION_FORBIDDEN,  // 403 — company-level ADMIN tried to assign ADMIN role (privilege escalation)
    INSUFFICIENT_ROLE,          // 403 — caller's role is insufficient for this operation
    ROLE_INVALIDE,              // 400 — invalid role value supplied

    // User management errors
    INVITATION_EXPIREE,          // 410 — invitation link expired or already used
    INVITATION_EN_COURS,         // 409 — a valid pending invitation already exists for this email
    DERNIER_ADMIN,               // 409 — cannot remove or demote the last admin of a société
    QUOTA_UTILISATEURS_ATTEINT,  // 409 — société has reached its user quota
    MEMBRE_DEJA_EXISTANT,        // 409 — user is already an active member of this société
    MEMBRE_NON_TROUVE,           // 404 — user is not a member of this société
    MOT_DE_PASSE_TROP_COURT,     // 400 — password too short
    MOT_DE_PASSE_TROP_FAIBLE,    // 400 — password does not meet complexity requirements
    MOT_DE_PASSE_CONTIENT_EMAIL, // 400 — password contains the user's email address
    COMPTE_DEJA_DEBLOQUE,        // 400 — account is not blocked

    // Task errors (404)
    TASK_NOT_FOUND,             // 404 — task not found in société

    // Document errors (404)
    DOCUMENT_NOT_FOUND,         // 404 — document not found in société

    // Server errors (500)
    INTERNAL_ERROR
}
