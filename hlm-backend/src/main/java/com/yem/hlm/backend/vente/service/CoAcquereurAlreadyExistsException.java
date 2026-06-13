package com.yem.hlm.backend.vente.service;

/**
 * Thrown when adding a second co-buyer to a vente (Wave 12 allows one). Mapped to
 * HTTP 409 (CO_ACQUEREUR_EXISTS).
 */
public class CoAcquereurAlreadyExistsException extends RuntimeException {
    public CoAcquereurAlreadyExistsException() {
        super("Cette vente a déjà un co-acquéreur.");
    }
}
