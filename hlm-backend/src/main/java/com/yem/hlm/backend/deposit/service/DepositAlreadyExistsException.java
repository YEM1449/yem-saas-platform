package com.yem.hlm.backend.deposit.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class DepositAlreadyExistsException extends RuntimeException {
    public DepositAlreadyExistsException(UUID contactId, UUID propertyId) {
        super("Deposit already exists for contact " + contactId + " and property " + propertyId);
    }
}
