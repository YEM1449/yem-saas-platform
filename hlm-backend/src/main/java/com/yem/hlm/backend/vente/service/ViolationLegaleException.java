package com.yem.hlm.backend.vente.service;

/**
 * Thrown when an operation would violate a legal constraint (e.g. deposit exceeding the
 * 5% cap, Art. 618-4 Loi 44-00). Mapped to HTTP 422 (VIOLATION_LEGALE).
 */
public class ViolationLegaleException extends RuntimeException {
    public ViolationLegaleException(String message) {
        super(message);
    }
}
