package com.yem.hlm.backend.payment.service;

import java.util.UUID;

/** Thrown when a payment call is not found (or cross-tenant access attempted). */
public class PaymentCallNotFoundException extends RuntimeException {
    public PaymentCallNotFoundException(UUID callId) {
        super("Payment call not found: " + callId);
    }
}
