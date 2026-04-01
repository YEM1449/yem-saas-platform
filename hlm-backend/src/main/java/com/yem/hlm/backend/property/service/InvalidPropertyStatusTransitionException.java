package com.yem.hlm.backend.property.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidPropertyStatusTransitionException extends RuntimeException {
    public InvalidPropertyStatusTransitionException(String message) {
        super(message);
    }
}
