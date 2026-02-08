package com.yem.hlm.backend.deposit.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidDepositStateException extends RuntimeException {
    public InvalidDepositStateException(String message) {
        super(message);
    }
}
