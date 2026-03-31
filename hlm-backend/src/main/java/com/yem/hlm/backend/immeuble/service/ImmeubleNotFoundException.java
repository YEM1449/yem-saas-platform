package com.yem.hlm.backend.immeuble.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ImmeubleNotFoundException extends RuntimeException {
    public ImmeubleNotFoundException(UUID id) {
        super("Immeuble not found: " + id);
    }
}
