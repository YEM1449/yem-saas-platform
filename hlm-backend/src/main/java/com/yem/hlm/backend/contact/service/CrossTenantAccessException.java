package com.yem.hlm.backend.contact.service;

/**
 * @deprecated Use {@link com.yem.hlm.backend.societe.CrossSocieteAccessException} instead.
 * Kept as a subclass for backward compatibility with existing service callers.
 */
@Deprecated
public class CrossTenantAccessException extends com.yem.hlm.backend.societe.CrossSocieteAccessException {
    @Deprecated
    public CrossTenantAccessException(String message) {
        super(message);
    }
}
