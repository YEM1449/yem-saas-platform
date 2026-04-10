package com.yem.hlm.backend.contact.service;

import java.util.List;

/**
 * Thrown when a contact is missing fields required for the requested pipeline stage.
 */
public class ClientIncompleteException extends RuntimeException {

    private final List<String> missingFields;

    public ClientIncompleteException(List<String> missingFields) {
        super("Contact is missing required fields: " + missingFields);
        this.missingFields = missingFields;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
