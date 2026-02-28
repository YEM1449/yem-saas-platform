package com.yem.hlm.backend.payments.service;

public class PaymentInvalidAmountException extends RuntimeException {
    public PaymentInvalidAmountException(String message) {
        super(message);
    }
}
