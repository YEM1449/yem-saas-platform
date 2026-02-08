package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class ContactAlreadyClientException extends RuntimeException {
    public ContactAlreadyClientException(UUID contactId) {
        super("Contact is already CLIENT: " + contactId);
    }
}
