package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ContactInterestNotFoundException extends RuntimeException {
    public ContactInterestNotFoundException(UUID contactId, UUID propertyId) {
        super("Contact interest not found: contact=" + contactId + " property=" + propertyId);
    }
}
