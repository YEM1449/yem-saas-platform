package com.yem.hlm.backend.property.service;

/**
 * Thrown when attempting to create a property with a reference code that already exists for the tenant.
 * Results in HTTP 409 CONFLICT.
 */
public class PropertyReferenceCodeExistsException extends RuntimeException {
    public PropertyReferenceCodeExistsException(String referenceCode) {
        super("Property with reference code '" + referenceCode + "' already exists for this tenant");
    }
}
