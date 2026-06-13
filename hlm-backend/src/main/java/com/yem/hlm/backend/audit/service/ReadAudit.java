package com.yem.hlm.backend.audit.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as sensitive: every call publishes a {@code SensitiveDataReadEvent}
 * which is persisted to the commercial audit log (B-004).
 *
 * <p>The aspect extracts the entity ID from the first {@code UUID} method argument.
 * Set {@link #entityType()} to identify the aggregate in the audit log.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadAudit {
    /** Aggregate type label stored in {@code commercial_audit_event.correlation_type}. */
    String entityType();
}
