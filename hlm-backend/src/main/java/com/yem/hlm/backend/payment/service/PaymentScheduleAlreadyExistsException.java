package com.yem.hlm.backend.payment.service;

import java.util.UUID;

/** Thrown when a payment schedule already exists for the given contract. */
public class PaymentScheduleAlreadyExistsException extends RuntimeException {
    public PaymentScheduleAlreadyExistsException(UUID contractId) {
        super("A payment schedule already exists for contract " + contractId);
    }
}
