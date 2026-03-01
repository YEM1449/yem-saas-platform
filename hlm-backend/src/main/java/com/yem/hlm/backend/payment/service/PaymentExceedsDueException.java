package com.yem.hlm.backend.payment.service;

/** Thrown when a payment amount would exceed the amount due for a call. */
public class PaymentExceedsDueException extends RuntimeException {
    public PaymentExceedsDueException(String message) {
        super(message);
    }
}
