package com.yem.hlm.backend.contract.service;

import java.util.UUID;

/**
 * Thrown when attempting to sign a second contract for a property that already
 * has an active SIGNED contract. Maps to HTTP 409 Conflict.
 */
public class PropertyAlreadySoldException extends RuntimeException {
    public PropertyAlreadySoldException(UUID propertyId) {
        super("Property " + propertyId + " already has an active signed contract");
    }
}
