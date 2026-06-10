package com.yem.hlm.backend.vente.service;

/**
 * Thrown when a retraction is requested outside the legal cooling-off window or when the
 * vente is not in a retractable state. Mapped to HTTP 409 (RETRACTATION_IMPOSSIBLE).
 */
public class RetractationImpossibleException extends RuntimeException {
    public RetractationImpossibleException(String message) {
        super(message);
    }
}
