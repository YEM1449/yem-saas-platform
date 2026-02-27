package com.yem.hlm.backend.outbox.service;

import java.util.UUID;

/**
 * Thrown when an outbound message cannot be found for the current tenant.
 * Maps to HTTP 404 {@code NOT_FOUND}.
 */
public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException(UUID messageId) {
        super("Outbound message not found: " + messageId);
    }
}
