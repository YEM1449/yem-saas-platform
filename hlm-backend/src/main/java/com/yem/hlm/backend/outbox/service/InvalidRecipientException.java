package com.yem.hlm.backend.outbox.service;

/**
 * Thrown when the provided recipient address / phone is missing or malformed.
 * Maps to HTTP 400 {@code INVALID_RECIPIENT}.
 */
public class InvalidRecipientException extends RuntimeException {

    public InvalidRecipientException(String message) {
        super(message);
    }
}
