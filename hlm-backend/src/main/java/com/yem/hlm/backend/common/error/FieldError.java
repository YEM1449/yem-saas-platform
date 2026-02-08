package com.yem.hlm.backend.common.error;

/**
 * Represents a single field validation error.
 */
public record FieldError(
        String field,
        String message
) {}
