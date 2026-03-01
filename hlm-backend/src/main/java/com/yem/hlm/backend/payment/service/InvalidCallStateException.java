package com.yem.hlm.backend.payment.service;

/** Thrown when an action is not permitted in the current payment call status. */
public class InvalidCallStateException extends RuntimeException {
    public InvalidCallStateException(String message) {
        super(message);
    }
}
