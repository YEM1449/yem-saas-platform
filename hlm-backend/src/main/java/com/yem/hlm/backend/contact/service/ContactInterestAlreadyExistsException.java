package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class ContactInterestAlreadyExistsException extends RuntimeException {
    public ContactInterestAlreadyExistsException(UUID contactId, UUID propertyId) {
        super("Contact interest already exists: contact=" + contactId + " property=" + propertyId);
    }
}
