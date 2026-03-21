package com.yem.hlm.backend.contact.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CrossSocieteAccessException extends RuntimeException {
    public CrossSocieteAccessException(String message) {
        super(message);
    }
}
