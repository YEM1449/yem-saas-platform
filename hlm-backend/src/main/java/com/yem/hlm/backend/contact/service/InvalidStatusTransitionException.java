package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.contact.domain.ContactStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(ContactStatus from, ContactStatus to) {
        super("Invalid status transition: " + from + " → " + to);
    }
}
