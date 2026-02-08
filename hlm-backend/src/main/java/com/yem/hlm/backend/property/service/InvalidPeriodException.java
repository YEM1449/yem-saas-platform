package com.yem.hlm.backend.property.service;

/**
 * Thrown when dashboard period validation fails (e.g., from > to, range > 366 days).
 * Results in HTTP 400 BAD REQUEST.
 */
public class InvalidPeriodException extends RuntimeException {
    public InvalidPeriodException(String message) {
        super(message);
    }
}
