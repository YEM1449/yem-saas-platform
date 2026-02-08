package com.yem.hlm.backend.deposit.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class PropertyAlreadyReservedException extends RuntimeException {
    public PropertyAlreadyReservedException(UUID propertyId) {
        super("Property is already reserved by an active deposit: " + propertyId);
    }
}
