package com.yem.hlm.backend.property.service;

/**
 * Thrown when property type-specific validation fails.
 * For example, when a VILLA is missing required bedrooms field.
 * Results in HTTP 400 BAD REQUEST.
 */
public class InvalidPropertyTypeException extends RuntimeException {
    public InvalidPropertyTypeException(String message) {
        super(message);
    }
}
