package com.yem.hlm.backend.deposit.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDepositRequestException extends RuntimeException {
    public InvalidDepositRequestException(String message) {
        super(message);
    }
}
