package com.yem.hlm.backend.common.validation;

/**
 * Marker interface for DTOs that must provide at least one of phone or email.
 * Java records that implement this interface automatically expose their
 * {@code phone()} and {@code email()} component accessors.
 */
public interface PhoneOrEmailTarget {
    String phone();
    String email();
}
