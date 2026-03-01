package com.yem.hlm.backend.payment.service;

import java.util.UUID;

/** Thrown when a payment tranche is not found (or cross-tenant access attempted). */
public class TrancheNotFoundException extends RuntimeException {
    public TrancheNotFoundException(UUID trancheId) {
        super("Payment tranche not found: " + trancheId);
    }
}
