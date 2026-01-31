package com.yem.hlm.backend.tenant.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TenantKeyAlreadyExistsException extends RuntimeException {
    public TenantKeyAlreadyExistsException(String key) {
        super("Tenant key already exists: " + key);
    }
}
