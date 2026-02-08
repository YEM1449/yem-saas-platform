package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ContactEmailAlreadyExistsException extends RuntimeException {
    public ContactEmailAlreadyExistsException(String email) {
        super("Contact email already exists for tenant: " + email);
    }
}
