package com.yem.hlm.backend.outbox.service;

/**
 * Thrown when a contact is resolved but the required channel field
 * (email for EMAIL, phone for SMS) is blank.
 * Maps to HTTP 400 {@code CONTACT_CHANNEL_MISSING}.
 */
public class ContactChannelMissingException extends RuntimeException {

    public ContactChannelMissingException(String message) {
        super(message);
    }
}
