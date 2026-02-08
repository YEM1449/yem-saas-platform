package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PropertyNotFoundException extends RuntimeException {
    public PropertyNotFoundException(UUID propertyId) {
        super("Property not found: " + propertyId);
    }
}
