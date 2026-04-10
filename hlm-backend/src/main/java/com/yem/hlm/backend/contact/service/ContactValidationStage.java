package com.yem.hlm.backend.contact.service;

/**
 * Pipeline stage used to determine which contact fields are required
 * before proceeding to the next commercial step.
 */
public enum ContactValidationStage {
    /** Minimum required for a reservation: phone number. */
    RESERVATION,
    /** Full KYC required before a sale contract can be created: phone + nationalId + address. */
    VENTE
}
