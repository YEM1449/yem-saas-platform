package com.yem.hlm.backend.immeuble.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class ImmeubleNameExistsException extends RuntimeException {
    public ImmeubleNameExistsException(String nom, UUID projectId) {
        super("Immeuble '" + nom + "' already exists in project " + projectId);
    }
}
