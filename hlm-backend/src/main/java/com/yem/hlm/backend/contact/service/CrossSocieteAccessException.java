package com.yem.hlm.backend.contact.service;

/**
 * @deprecated Use {@link com.yem.hlm.backend.societe.CrossSocieteAccessException} instead.
 * Kept as a subclass for backward compatibility with existing service callers.
 */
@Deprecated
public class CrossSocieteAccessException extends com.yem.hlm.backend.societe.CrossSocieteAccessException {
    @Deprecated
    public CrossSocieteAccessException(String message) {
        super(message);
    }
}
