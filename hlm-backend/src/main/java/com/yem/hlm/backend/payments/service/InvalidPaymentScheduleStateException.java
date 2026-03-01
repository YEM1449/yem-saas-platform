package com.yem.hlm.backend.payments.service;

public class InvalidPaymentScheduleStateException extends RuntimeException {
    public InvalidPaymentScheduleStateException(String message) {
        super(message);
    }
}
