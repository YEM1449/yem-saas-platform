package com.yem.hlm.backend.payments.service;

import java.util.UUID;

public class PaymentScheduleItemNotFoundException extends RuntimeException {
    public PaymentScheduleItemNotFoundException(UUID id) {
        super("Payment schedule item not found: " + id);
    }
}
