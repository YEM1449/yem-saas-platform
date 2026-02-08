package com.yem.hlm.backend.deposit.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DepositNotFoundException extends RuntimeException {
    public DepositNotFoundException(UUID id) {
        super("Deposit not found: " + id);
    }
}
