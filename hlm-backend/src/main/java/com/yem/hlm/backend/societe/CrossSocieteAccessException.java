package com.yem.hlm.backend.societe;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a request attempts to access data outside its société scope,
 * or when the société context is missing entirely.
 *
 * This is the canonical cross-société access exception. All legacy variants
 * ({@code CrossTenantAccessException}, {@code contact.service.CrossSocieteAccessException})
 * extend this class and are handled via subclass matching.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class CrossSocieteAccessException extends RuntimeException {
    public CrossSocieteAccessException(String message) {
        super(message);
    }
}
